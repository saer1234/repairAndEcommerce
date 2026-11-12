import { Component, DestroyRef, ElementRef, HostListener, ViewChild, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { interval } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { SupportRealtimeService, SupportLiveMessage } from '../../core/services/support-realtime.service';

interface SupportConversation {
  id: number; companyId: number; companyName: string; customerId: number;
  customerName: string; customerEmail: string;
  status: 'OPEN' | 'CLOSED'; updatedAt: string;
}
interface SupportMessage {
  id: number; conversationId: number; senderId: number; senderName: string;
  senderType: 'CUSTOMER' | 'SUPPORT'; body: string; createdAt: string;
}
interface Availability { online: boolean; availableAgents: number; }
interface OfflineInquiry {
  id: number; companyId: number; customerId: number; customerName: string;
  customerEmail: string; body: string; createdAt: string;
  reply: string | null; repliedByName: string | null; repliedAt: string | null;
}

@Component({
  selector: 'app-support-page',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './support.page.html',
  styleUrl: './support.page.css'
})
export class SupportPage {
  @ViewChild('messageList') private messageList?: ElementRef<HTMLElement>;
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly ctx = inject(CompanyContext);
  private readonly destroyRef = inject(DestroyRef);
  private readonly realtime = inject(SupportRealtimeService);
  readonly context = this.ctx;
  readonly currentUser = this.auth.user;
  readonly mode = computed<'staff' | 'client' | 'none'>(() => {
    const role = this.ctx.role();
    if (role === 'OWNER' || role === 'MANAGER' || role === 'EMPLOYEE') return 'staff';
    return this.auth.canShop() ? 'client' : 'none';
  });
  readonly canClose = computed(() => this.ctx.role() === 'OWNER' || this.ctx.role() === 'MANAGER');
  readonly companyId = computed(() => this.mode() === 'staff'
    ? this.ctx.managedSelected() : this.mode() === 'client' ? this.ctx.selected() : null);

  readonly availability = signal<Availability | null>(null);
  readonly availableForChat = signal(false);
  readonly inbox = signal<SupportConversation[]>([]);
  readonly selected = signal<SupportConversation | null>(null);
  readonly messages = signal<SupportMessage[]>([]);
  readonly offlineMessages = signal<OfflineInquiry[]>([]);
  readonly selectedOffline = signal<OfflineInquiry | null>(null);
  readonly staffTab = signal<'live' | 'offline'>('live');
  readonly pendingOffline = computed(() => this.offlineMessages().filter(x => !x.reply).length);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly realtimeConnected = signal(false);
  // Immediate WS presence signal: do not wait for the 8-second database close.
  // An empty map means the initial presence is unknown; CLOSED status is still enforced.
  readonly customerPresence = signal<Record<number, boolean>>({});
  readonly selectedClientOnline = computed(() => {
    const c = this.selected();
    if (!c || this.mode() !== 'staff') return true;
    return this.customerPresence()[c.customerId] !== false;
  });
  draft = '';
  offlineDraft = '';
  offlineReplyDraft = '';
  private readonly sessionId = crypto.randomUUID();
  private presenceCompany: number | null = null;
  private heartbeatInFlight = false;
  private presenceEpoch = 0;
  private loadGeneration = 0;
  private disconnectRealtime: (() => void) | null = null;

  constructor() {
    effect(() => {
      const id = this.companyId();
      const mode = this.mode();
      untracked(() => {
        if (this.presenceCompany && (this.presenceCompany !== id || mode !== 'staff')) {
          this.leavePresence(this.presenceCompany);
        }
        if (id && mode !== 'none') {
          this.loadCompany(id, mode);
          const generation = this.loadGeneration;
          this.disconnectRealtime = this.realtime.connect(id,
            () => { if (generation === this.loadGeneration) this.refresh(true); },
            connected => {
              if (generation !== this.loadGeneration) return;
              this.realtimeConnected.set(connected);
              // A disconnected staff socket clears this page's presence on the server.
              // Re-establish the lease promptly after a verified reconnect.
              if (connected && mode === 'staff' && this.availableForChat()) this.heartbeat(true);
            },
            message => { if (generation === this.loadGeneration) this.receiveLiveMessage(message); },
            (customerId, online) => {
              if (generation !== this.loadGeneration || mode !== 'staff') return;
              this.customerPresence.update(current => ({ ...current, [customerId]: online }));
            },
            (online, availableAgents) => {
              if (generation === this.loadGeneration) this.availability.set({ online, availableAgents });
            },
            mode === 'staff' ? this.sessionId : undefined);
        }
        else this.resetPage();
      });
    });
    // Availability still needs an occasional refresh because staff heartbeat leases can expire.
    // Live chat message delivery itself uses the authenticated event stream.
    interval(30000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (document.visibilityState !== 'visible' || !this.auth.signedIn() || this.busy()) return;
      if (this.realtimeConnected()) { const id = this.companyId(); if (id) this.loadAvailability(id); }
      else this.refresh(true); // Degraded-network fallback.
    });
    interval(25000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (document.visibilityState === 'visible' && this.auth.signedIn() &&
          this.mode() === 'staff' && this.availableForChat()) this.heartbeat(true);
    });
    this.destroyRef.onDestroy(() => {
      this.disconnectRealtime?.();
      if (this.presenceCompany) this.leavePresence(this.presenceCompany);
    });
  }

  @HostListener('document:visibilitychange')
  visibilityChanged(): void {
    if (document.visibilityState === 'visible' && this.availableForChat() && this.mode() === 'staff') {
      this.heartbeat(true);
    }
  }

  private base(id = this.companyId()): string { return `/companies/${id}/support`; }

  private resetPage(): void {
    this.disconnectRealtime?.(); this.disconnectRealtime = null;
    this.realtimeConnected.set(false);
    this.customerPresence.set({});
    ++this.loadGeneration;
    this.availability.set(null); this.inbox.set([]); this.selected.set(null);
    this.messages.set([]); this.offlineMessages.set([]); this.selectedOffline.set(null);
    this.loading.set(false);
  }

  private loadCompany(id: number, mode: 'staff' | 'client'): void {
    this.resetPage();
    const generation = this.loadGeneration;
    this.error.set(''); this.notice.set(''); this.loading.set(true);
    this.loadAvailability(id, generation);
    this.loadOffline(id, mode, generation);
    if (mode === 'staff') {
      this.loadInbox(id, generation);
    } else {
      this.loadMine(id, generation);
    }
  }

  /** Resync the client conversation status when the socket reconnects or reopens it. */
  private loadMine(id: number, generation = this.loadGeneration, silent = false): void {
    this.api.get<SupportConversation | null>(`${this.base(id)}/mine`).subscribe({
      next: conversation => {
        if (generation !== this.loadGeneration || this.companyId() !== id || this.mode() !== 'client') return;
        this.loading.set(false);
        this.selected.set(conversation);
        if (conversation) this.loadMessages(conversation.id, id, true);
        else this.messages.set([]);
      },
      error: () => {
        if (generation !== this.loadGeneration || silent) return;
        this.loading.set(false);
        this.error.set('Unable to load your conversation.');
      }
    });
  }

  private loadAvailability(id: number, generation = this.loadGeneration): void {
    this.api.get<Availability>(`${this.base(id)}/availability`).subscribe({
      next: value => { if (generation === this.loadGeneration) this.availability.set(value); },
      error: () => { if (generation === this.loadGeneration) this.availability.set(null); }
    });
  }

  private loadOffline(id: number, mode: 'staff' | 'client', generation = this.loadGeneration): void {
    const suffix = mode === 'staff' ? '/offline-messages' : '/offline-messages/mine';
    this.api.get<OfflineInquiry[]>(`${this.base(id)}${suffix}`).subscribe({
      next: list => {
        if (generation !== this.loadGeneration) return;
        this.offlineMessages.set(list);
        if (mode === 'staff') {
          const current = this.selectedOffline();
          const next = list.find(m => m.id === current?.id) ?? list[0] ?? null;
          this.selectedOffline.set(next);
        }
      },
      error: () => { if (generation === this.loadGeneration) this.error.set('Unable to load offline inquiries.'); }
    });
  }

  private loadInbox(id: number, generation = this.loadGeneration, silent = false, skipCurrentMessages = false): void {
    this.api.get<SupportConversation[]>(`${this.base(id)}/conversations`).subscribe({
      next: list => {
        if (generation !== this.loadGeneration) return;
        this.inbox.set(list); this.loading.set(false);
        const current = this.selected();
        const next = list.find(c => c.id === current?.id) ?? list[0] ?? null;
        this.selected.set(next);
        if (next) {
          if (!skipCurrentMessages || current?.id !== next.id) this.loadMessages(next.id, id, true);
        } else this.messages.set([]);
      },
      error: () => {
        if (generation !== this.loadGeneration) return;
        this.loading.set(false);
        if (!silent) this.error.set('Unable to load live conversations.');
      }
    });
  }

  private receiveLiveMessage(message: SupportLiveMessage): void {
    const current = this.selected();
    if (current?.id === message.conversationId) {
      this.messages.update(existing => existing.some(m => m.id === message.id)
        ? existing : [...existing, message]);
      this.scrollMessages();
    } else if (this.mode() === 'client') {
      // Another tab may have started the conversation.
      this.refresh(true);
    }
    if (this.mode() === 'staff') {
      const id = this.companyId();
      if (id) this.loadInbox(id, this.loadGeneration, true, true);
    }
  }

  private scrollMessages(): void {
    requestAnimationFrame(() => {
      const element = this.messageList?.nativeElement;
      if (element) element.scrollTop = element.scrollHeight;
    });
  }

  choose(conversation: SupportConversation): void {
    this.selected.set(conversation); this.messages.set([]); this.error.set('');
    this.loadMessages(conversation.id);
  }

  chooseOffline(message: OfflineInquiry): void {
    this.selectedOffline.set(message); this.offlineReplyDraft = '';
    this.error.set('');
  }

  private loadMessages(conversationId: number, companyId = this.companyId(), silent = false): void {
    const generation = this.loadGeneration;
    this.api.get<SupportMessage[]>(`${this.base(companyId)}/conversations/${conversationId}/messages`).subscribe({
      next: list => {
        if (generation === this.loadGeneration && this.selected()?.id === conversationId) {
          // Merge already-pushed WebSocket messages so an older REST result cannot hide them.
          const latest = this.messages().filter(m => m.conversationId === conversationId);
          const byId = new Map<number, SupportMessage>(list.map(m => [m.id, m]));
          for (const m of latest) byId.set(m.id, m);
          this.messages.set(Array.from(byId.values()).sort((a, b) => a.id - b.id).slice(-100));
          this.scrollMessages();
        }
      },
      error: () => {
        if (!silent && generation === this.loadGeneration) this.error.set('Unable to load chat messages.');
      }
    });
  }

  refresh(silent = false): void {
    const id = this.companyId(); const mode = this.mode();
    if (!id || mode === 'none') return;
    this.loadAvailability(id);
    this.loadOffline(id, mode);
    if (mode === 'staff') this.loadInbox(id, this.loadGeneration, silent);
    else this.loadMine(id, this.loadGeneration, silent);
  }

  toggleAvailability(): void {
    const id = this.companyId();
    if (!id || this.mode() !== 'staff' || this.busy()) return;
    if (this.availableForChat()) {
      this.leavePresence(id);
      this.notice.set('You are no longer available for live chat.');
      return;
    }
    this.heartbeat(false);
  }

  private heartbeat(silent: boolean): void {
    const id = this.companyId();
    if (!id || this.mode() !== 'staff' || this.heartbeatInFlight) return;
    const epoch = this.presenceEpoch;
    this.heartbeatInFlight = true;
    if (!silent) this.busy.set(true);
    this.api.post<Availability>(`${this.base(id)}/presence/heartbeat`, { sessionId: this.sessionId }).subscribe({
      next: value => {
        this.heartbeatInFlight = false;
        if (this.companyId() !== id || this.mode() !== 'staff' || this.presenceEpoch !== epoch) {
          this.api.delete(`${this.base(id)}/presence/${this.sessionId}`).subscribe({ error: () => {} });
          return;
        }
        this.presenceCompany = id; this.availableForChat.set(true);
        this.availability.set(value); this.busy.set(false);
        if (!silent) this.notice.set('You are available to answer live chats.');
      },
      error: () => {
        this.heartbeatInFlight = false;
        if (this.companyId() !== id || this.presenceEpoch !== epoch) return;
        this.availableForChat.set(false); this.presenceCompany = null; this.busy.set(false);
        this.error.set('Could not update your online status. Try again.');
        this.refresh(true);
      }
    });
  }

  private leavePresence(companyId: number): void {
    ++this.presenceEpoch;
    this.presenceCompany = null; this.availableForChat.set(false);
    this.api.delete(`${this.base(companyId)}/presence/${this.sessionId}`).subscribe({ error: () => {} });
    if (companyId === this.companyId()) {
      this.loadAvailability(companyId);
    }
  }

  start(): void {
    const id = this.companyId();
    if (!id || this.busy() || !this.availability()?.online || !this.realtimeConnected()) return;
    this.busy.set(true); this.error.set('');
    this.api.post<SupportConversation>(`${this.base(id)}/conversations`, {}).subscribe({
      next: conversation => {
        this.busy.set(false); this.selected.set(conversation);
        this.loadMessages(conversation.id, id);
      },
      error: () => {
        this.busy.set(false); this.loadAvailability(id);
        this.error.set('The shop may have gone offline. Please send an offline message.');
      }
    });
  }

  send(): void {
    const id = this.companyId(); const conversation = this.selected();
    const body = this.draft.trim();
    if (!id || !conversation || conversation.status !== 'OPEN' || !this.selectedClientOnline() || !this.realtimeConnected() || !this.availability()?.online ||
        (this.mode() === 'staff' && !this.availableForChat()) ||
        !body || body.length > 2000 || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.notice.set('');
    if (this.realtime.isConnected(id)) {
      void this.realtime.send(id, conversation.id, body).then(message => {
        this.busy.set(false);
        if (this.draft.trim() === body) this.draft = '';
        this.receiveLiveMessage(message); // The ack may arrive before the broadcast.
      }).catch(error => {
        this.busy.set(false);
        this.error.set(error instanceof Error ? error.message : 'Unable to send live message.');
        this.loadAvailability(id);
      });
      return;
    }
    // Fallback when a corporate proxy blocks WebSockets; REST still persists the message.
    this.api.post<SupportMessage>(`${this.base(id)}/conversations/${conversation.id}/messages`, { body }).subscribe({
      next: message => {
        this.busy.set(false);
        if (this.draft.trim() === body) this.draft = '';
        this.receiveLiveMessage(message);
      },
      error: () => {
        this.busy.set(false); this.loadAvailability(id);
        this.error.set('Live chat unavailable. Use an offline inquiry if the shop is offline.');
      }
    });
  }

  sendOffline(): void {
    const id = this.companyId(); const body = this.offlineDraft.trim();
    if (!id || this.mode() !== 'client' || !body || body.length > 2000 || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.api.post<OfflineInquiry>(`${this.base(id)}/offline-messages`, { body }).subscribe({
      next: () => {
        this.busy.set(false); this.offlineDraft = '';
        this.notice.set('Your message was saved. Shop staff can reply when they return.');
        this.loadOffline(id, 'client');
      },
      error: () => {
        this.busy.set(false); this.error.set('Could not save your offline inquiry.');
      }
    });
  }

  replyOffline(): void {
    const id = this.companyId(); const inquiry = this.selectedOffline();
    const body = this.offlineReplyDraft.trim();
    if (!id || this.mode() !== 'staff' || !inquiry || inquiry.reply ||
        !body || body.length > 2000 || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.api.patch<OfflineInquiry>(`${this.base(id)}/offline-messages/${inquiry.id}/reply`, { body }).subscribe({
      next: value => {
        this.busy.set(false); this.offlineReplyDraft = '';
        this.selectedOffline.set(value); this.notice.set('Reply saved for the customer.');
        this.loadOffline(id, 'staff');
      },
      error: () => {
        this.busy.set(false); this.error.set('Reply was not saved; the inquiry may already be answered.');
      }
    });
  }

  setStatus(status: 'OPEN' | 'CLOSED'): void {
    const id = this.companyId(); const conversation = this.selected();
    if (!id || !conversation || !this.canClose() || this.busy() ||
        (status === 'OPEN' && !this.realtimeConnected())) return;
    this.busy.set(true); this.error.set('');
    this.api.patch<SupportConversation>(`${this.base(id)}/conversations/${conversation.id}/status`, { status }).subscribe({
      next: value => {
        this.busy.set(false); this.selected.set(value);
        this.notice.set(status === 'CLOSED' ? 'Conversation closed.' : 'Conversation reopened.');
        this.refresh(true);
      },
      error: () => { this.busy.set(false); this.error.set('Unable to change conversation status.'); }
    });
  }
}
