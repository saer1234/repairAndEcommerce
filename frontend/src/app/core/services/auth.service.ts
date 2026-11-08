import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { Api } from '../../api';
import { CompanyContext } from '../../core/services/company-context';
import { User, LoginResult } from '../../core/models/model';

const SESSION_KEY = 'repairflow.session.v1';
interface StoredSession { accessToken: string; expiresAt: string; user: User; }

// Session storage survives page refresh in the same tab, but generally disappears
// when the tab/window is closed. It is readable by JavaScript; do not store
// long-lived credentials here. Prefer HttpOnly refresh cookies for production.
function restoreSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== 'object') throw new Error('Invalid session');
    const session = value as Partial<StoredSession>;
    if (typeof session.accessToken !== 'string' || !session.accessToken ||
        typeof session.expiresAt !== 'string' || !Number.isFinite(Date.parse(session.expiresAt)) ||
        Date.parse(session.expiresAt) <= Date.now() ||
        !session.user || typeof session.user.id !== 'number' ||
        typeof session.user.email !== 'string' || typeof session.user.platformRole !== 'string') {
      throw new Error('Expired or invalid session');
    }
    return session as StoredSession;
  } catch {
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* blocked storage */ }
    return null;
  }
}

@Injectable({providedIn: 'root'})
export class AuthService {

     private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly companies = inject(CompanyContext);
  private readonly restored = restoreSession();
  readonly token = signal<string | null>(this.restored?.accessToken ?? null);
  readonly user = signal<User | null>(this.restored?.user ?? null);
  readonly expiresAt = signal<number>(this.restored ? Date.parse(this.restored.expiresAt) : 0);

  // A plain function (rather than computed) checks the real clock on every call.
  readonly signedIn = () => !!this.token() && Date.now() < this.expiresAt();
  readonly canShop = () => this.signedIn() && this.user()?.platformRole === 'CLIENT' && this.companies.loaded() && this.companies.mine().length === 0;

  login(email: string, password: string) {
    return this.api.post<LoginResult>('/auth/login', { email, password }).pipe(tap(result => {
      if (!Number.isFinite(Date.parse(result.expiresAt)) || Date.parse(result.expiresAt) <= Date.now()) {
        throw new Error('The server returned an expired session');
      }
      this.companies.reset(); // Don't mix previous users' company selections.
      this.token.set(result.accessToken);
      this.user.set(result.user);
      this.expiresAt.set(Date.parse(result.expiresAt));
      try {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify({
          accessToken: result.accessToken, expiresAt: result.expiresAt, user: result.user
        } satisfies StoredSession));
      } catch { /* Private/blocked storage: login still works until refresh */ }
    }));
  }

  register(fullName: string, email: string, password: string, phone: string, accountType: 'OWNER'|'CLIENT') {
    return this.api.post<User>('/auth/register', { fullName, email, password, phone, accountType });
    
  }

  clearSession(): void {
    this.token.set(null);
    this.user.set(null);
    this.expiresAt.set(0);
    this.companies.reset();
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* blocked storage */ }
  }
  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }
}
