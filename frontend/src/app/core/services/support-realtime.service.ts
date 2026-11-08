import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../api';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

export interface SupportLiveMessage {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  senderType: 'CUSTOMER' | 'SUPPORT';
  body: string;
  createdAt: string;
}

type ServerFrame =
  | { type: 'connected' }
  | { type: 'refresh'; companyId: number }
  | { type: 'message'; companyId: number; conversationId: number; message: SupportLiveMessage }
  | { type: 'ack'; requestId: string; message: SupportLiveMessage }
  | { type: 'error'; requestId?: string; message: string };

interface TicketResponse { ticket: string; expiresAt: string; }
interface Pending {
  resolve(message: SupportLiveMessage): void;
  reject(reason: Error): void;
  timer: ReturnType<typeof setTimeout>;
}

/** Bidirectional native WebSocket client; no JWT in a URL or new NPM dependencies. */
@Injectable({ providedIn: 'root' })
export class SupportRealtimeService {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private socket: WebSocket | null = null;
  private company: number | null = null;
  private pending = new Map<string, Pending>();

  isConnected(companyId: number): boolean {
    return this.company === companyId && this.socket?.readyState === WebSocket.OPEN;
  }

  connect(
    companyId: number,
    onRefresh: () => void,
    onState: (connected: boolean) => void,
    onMessage: (message: SupportLiveMessage) => void
  ): () => void {
    // Only one active support subscription per Angular page.
    this.disconnect();
    this.company = companyId;
    let active = true;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let retries = 0;
    const rejectPending = (message: string) => {
      for (const pending of this.pending.values()) {
        clearTimeout(pending.timer);
        pending.reject(new Error(message));
      }
      this.pending.clear();
    };
    const retry = () => {
      if (!active || !this.auth.signedIn()) return;
      const delay = Math.min(1000 * 2 ** Math.min(retries++, 4), 15000);
      reconnectTimer = setTimeout(() => { void open(); }, delay);
    };
    const open = async (): Promise<void> => {
      if (!active || !this.auth.signedIn()) return;
      try {
        // The REST request uses the regular Authorization: Bearer JWT header.
        // The short-lived, single-use ticket is all that travels in the WS URL.
        const credential = await firstValueFrom(this.api.post<TicketResponse>(
          `/companies/${companyId}/support/ws-ticket`, {}
        ));
        if (!active) return;
        const url = new URL(`${environment.apiUrl}/support/ws`, window.location.origin);
        url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
        url.searchParams.set('ticket', credential.ticket);
        const socket = new WebSocket(url.toString());
        this.socket = socket;
        socket.onopen = () => {
          if (!active) { socket.close(); return; }
          retries = 0;
          onState(true);
          onRefresh(); // Resynchronize messages missed while disconnected.
        };
        socket.onmessage = event => {
          if (!active) return;
          try {
            const frame = JSON.parse(String(event.data)) as ServerFrame;
            if (frame.type === 'refresh' && frame.companyId === companyId) onRefresh();
            if (frame.type === 'message' && frame.companyId === companyId) onMessage(frame.message);
            if (frame.type === 'ack') {
              const pending = this.pending.get(frame.requestId);
              if (pending) {
                clearTimeout(pending.timer);
                this.pending.delete(frame.requestId);
                pending.resolve(frame.message);
              }
            }
            if (frame.type === 'error') {
              const pending = frame.requestId ? this.pending.get(frame.requestId) : undefined;
              if (pending) {
                clearTimeout(pending.timer);
                this.pending.delete(frame.requestId!);
                pending.reject(new Error(frame.message));
              }
            }
          } catch (error) {
            console.warn('Invalid support message', error);
          }
        };
        socket.onclose = () => {
          // Ignore a stale socket from a previous company/reconnect attempt.
          if (this.socket !== socket) return;
          this.socket = null;
          rejectPending('Chat connection interrupted. Check whether your message was received before retrying.');
          onState(false);
          retry();
        };
        socket.onerror = () => {
          // The close event triggers retry; avoid duplicate reconnect attempts.
          if (this.socket === socket) onState(false);
        };
      } catch (error) {
        if (!active) return;
        onState(false);
        // An expired session is not fixable by reconnecting with the same token.
        if (this.auth.signedIn()) retry();
        else console.warn('Support chat session expired', error);
      }
    };
    void open();

    return () => {
      active = false;
      if (reconnectTimer !== null) clearTimeout(reconnectTimer);
      if (this.company === companyId) this.disconnect();
      onState(false);
    };
  }

  send(companyId: number, conversationId: number, body: string): Promise<SupportLiveMessage> {
    const socket = this.socket;
    if (!this.isConnected(companyId) || !socket)
      return Promise.reject(new Error('Real-time connection unavailable'));
    const requestId = crypto.randomUUID();
    return new Promise<SupportLiveMessage>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error('No reply from the server. Check the conversation before retrying.'));
      }, 15000);
      this.pending.set(requestId, { resolve, reject, timer });
      try {
        socket.send(JSON.stringify({ type: 'send', requestId, conversationId, body }));
      } catch (error) {
        clearTimeout(timer);
        this.pending.delete(requestId);
        reject(error instanceof Error ? error : new Error('Unable to send message'));
      }
    });
  }

  private disconnect(): void {
    this.company = null;
    const socket = this.socket;
    this.socket = null;
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(new Error('Chat closed before confirmation'));
    }
    this.pending.clear();
    if (socket && socket.readyState !== WebSocket.CLOSED) socket.close();
  }
}
