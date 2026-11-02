import { NgTemplateOutlet } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit, ViewChild, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';
import { CompanyContext } from './company-context';

@Component({
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, FormsModule, NgTemplateOutlet],
  template: `
<a class="skip-to-content" href="#main-content">Skip to main content</a>
<div class="app-shell">
  <!-- Persistent desktop navigation. Hidden completely at mobile widths. -->
  <aside class="desktop-sidebar" aria-label="Application sidebar">
    <div class="sidebar-brand">
      <span class="brand-symbol" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a5 5 0 0 0-6.4 6.4L3 18a2.1 2.1 0 0 0 3 3l5.3-5.3a5 5 0 0 0 6.4-6.4l-3.2 3.2-3-3 3.2-3.2Z" /></svg>
      </span>
      <span class="sidebar-brand-copy"><strong>RepairFlow</strong><small>MANAGEMENT SUITE</small></span>
    </div>
    <ng-container [ngTemplateOutlet]="navigationLinks"></ng-container>
    <div class="nav-profile">
      <div class="nav-profile-person">
        <span class="profile-avatar" aria-hidden="true">{{ initials }}</span>
        <span class="profile-details"><strong>{{ auth.user()?.fullName }}</strong><small>{{ auth.user()?.platformRole }}</small></span>
      </div>
      <button type="button" class="sidebar-logout" (click)="signOut()" aria-label="Log out of RepairFlow">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/></svg>
        <span>Log out</span>
      </button>
    </div>
  </aside>

  <div class="workspace-column">
    <!-- Always reachable on mobile, even when the drawer is closed. -->
    <header class="mobile-app-bar">
      <button #menuTrigger type="button" class="mobile-menu-button" (click)="openMenu()"
              [attr.aria-expanded]="menuOpen()" aria-label="Open navigation menu" aria-controls="mobile-navigation">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>
      </button>
      <div class="mobile-brand">
        <span class="brand-symbol" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a5 5 0 0 0-6.4 6.4L3 18a2.1 2.1 0 0 0 3 3l5.3-5.3a5 5 0 0 0 6.4-6.4l-3.2 3.2-3-3 3.2-3.2Z" /></svg>
        </span>
        <strong>RepairFlow</strong>
      </div>
      <button type="button" class="mobile-logout" (click)="signOut()" aria-label="Log out of RepairFlow">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/></svg>
        <span>Log out</span>
      </button>
    </header>

    <main id="main-content" class="main-content" tabindex="-1">
      <header class="workspace-header">
        <div class="workspace-heading">
          <p class="workspace-eyebrow">YOUR WORKSPACE</p>
          <h2>Repair Management</h2>
          <p class="workspace-subtitle">Everything you need, in one place.</p>
        </div>
        <div class="context-pickers">
          @if (context.mine().length) {
            <div class="company-picker">
              <label for="managedCompany">My workspace <span class="picker-note">(private)</span></label>
              <select id="managedCompany" [ngModel]="context.managedSelected()"
                      (ngModelChange)="context.managedSelected.set(+$event)">
                @for (c of context.mine(); track c.id) {
                  <option [ngValue]="c.id">{{ c.name }} — {{ c.yourRole }}</option>
                }
              </select>
            </div>
          }
          @if (auth.canShop()) {
            <div class="company-picker">
              <label for="storeCompany">Browse customer store</label>
              <select id="storeCompany" [ngModel]="context.selected()"
                      (ngModelChange)="context.selected.set(+$event)">
                @for (c of context.available(); track c.id) {
                  <option [ngValue]="c.id">{{ c.name }}</option>
                }
              </select>
            </div>
          }
        </div>
      </header>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      <router-outlet />
    </main>
  </div>

  <!-- A real mobile drawer: not in the tab order while closed. -->
  @if (menuOpen()) {
    <div class="mobile-nav-layer" id="mobile-navigation">
      <button type="button" class="mobile-nav-backdrop" tabindex="-1" aria-label="Close navigation menu" (click)="closeMenu()"></button>
      <aside #mobileDrawer class="mobile-nav-drawer" role="dialog" aria-modal="true" aria-label="Navigation menu">
        <div class="drawer-top">
          <div class="mobile-brand drawer-brand">
            <span class="brand-symbol" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a5 5 0 0 0-6.4 6.4L3 18a2.1 2.1 0 0 0 3 3l5.3-5.3a5 5 0 0 0 6.4-6.4l-3.2 3.2-3-3 3.2-3.2Z" /></svg>
            </span>
            <strong>RepairFlow</strong>
          </div>
          <button #drawerClose type="button" class="drawer-close" aria-label="Close navigation menu" (click)="closeMenu()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>
          </button>
        </div>
        <ng-container [ngTemplateOutlet]="navigationLinks"></ng-container>
        <div class="nav-profile">
          <div class="nav-profile-person">
            <span class="profile-avatar" aria-hidden="true">{{ initials }}</span>
            <span class="profile-details"><strong>{{ auth.user()?.fullName }}</strong><small>{{ auth.user()?.platformRole }}</small></span>
          </div>
          <button type="button" class="sidebar-logout" (click)="signOut()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/></svg>
            <span>Log out</span>
          </button>
        </div>
      </aside>
    </div>
  }

  <!-- Shared nav template keeps desktop and mobile links and role rules in sync. -->
  <ng-template #navigationLinks>
    <nav class="app-nav" aria-label="Primary navigation">
      <p class="nav-section-title">OVERVIEW</p>
      <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="9" rx="1.4"/><rect x="14" y="3" width="7" height="5" rx="1.4"/><rect x="14" y="12" width="7" height="9" rx="1.4"/><rect x="3" y="16" width="7" height="5" rx="1.4"/></svg>
        <span>Dashboard</span>
      </a>
      @if (auth.canShop() || context.canManage()) {
        <p class="nav-section-title">COMMERCE</p>
      }
      @if (auth.canShop()) {
        <a routerLink="/shop" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 10h18l-1.5-6h-15L3 10Z"/><path d="M5 10v10h14V10M9 20v-6h6v6"/></svg>
          <span>Customer Store</span>
        </a>
      }
      @if (auth.canShop() || context.canManage()) {
        <a routerLink="/orders" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/></svg>
          <span>Orders &amp; Invoices</span>
        </a>
      }
      @if (context.canManage()) {
        <a routerLink="/inventory" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m12 3 9 5-9 5-9-5 9-5ZM3 8v9l9 5 9-5V8M12 13v9"/></svg>
          <span>Manage My Store</span>
        </a>
      }
      <p class="nav-section-title">REPAIRS</p>
      <a routerLink="/repairs" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14.7 6.3a5 5 0 0 0-6.4 6.4L3 18a2.1 2.1 0 0 0 3 3l5.3-5.3a5 5 0 0 0 6.4-6.4l-3.2 3.2-3-3 3.2-3.2Z"/></svg>
        <span>Request a Repair</span>
      </a>
      <a routerLink="/track-repairs" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
        <span>Track My Repairs</span>
      </a>
      @if (context.mine().length) {
        <a routerLink="/repair-workspace" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 21h18M6 21v-9l6-4 6 4v9M9 21v-5h6v5M5 8l7-5 7 5"/></svg>
          <span>Repair Workspace</span>
        </a>
      }
      <p class="nav-section-title">ACCOUNT</p>
      <a routerLink="/companies" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu(false)">
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 13h18"/></svg>
        <span>Companies</span>
      </a>
    </nav>
  </ng-template>
</div>
`
})
export class ShellPage implements OnInit {
  readonly auth = inject(AuthService);
  readonly context = inject(CompanyContext);
  readonly error = signal('');
  readonly menuOpen = signal(false);

  @ViewChild('menuTrigger') private menuTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('drawerClose') private drawerClose?: ElementRef<HTMLButtonElement>;
  @ViewChild('mobileDrawer') private mobileDrawer?: ElementRef<HTMLElement>;

  get initials(): string {
    const name = this.auth.user()?.fullName?.trim() || 'User';
    return name.split(/\s+/).slice(0, 2).map(part => part[0].toUpperCase()).join('');
  }

  ngOnInit(): void {
    this.context.refresh(() => this.error.set('Unable to load companies. Check backend connection.'));
  }

  openMenu(): void {
    this.menuOpen.set(true);
    requestAnimationFrame(() => this.drawerClose?.nativeElement.focus());
  }

  closeMenu(restoreFocus = true): void {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    if (restoreFocus) requestAnimationFrame(() => this.menuTrigger?.nativeElement.focus());
  }

  signOut(): void {
    this.closeMenu(false);
    this.auth.logout();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
  }

  @HostListener('document:keydown.tab', ['$event'])
  onTab(event: Event): void {
	if( (event instanceof KeyboardEvent)) return;
    if (!this.menuOpen()) return;
    const drawer = this.mobileDrawer?.nativeElement;
    if (!drawer) return;
    const focusables = Array.from(drawer.querySelectorAll<HTMLElement>('button:not([disabled]), a[href]'));
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (!first || !last) return;
    if ((event as KeyboardEvent).shiftKey && (document.activeElement === first || !drawer.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!(event as KeyboardEvent).shiftKey && (document.activeElement === last || !drawer.contains(document.activeElement))) {
      event.preventDefault();
      first.focus();
    }
  }

  @HostListener('window:resize')
  onResize(): void {
    if (window.innerWidth > 920) this.closeMenu(false);
  }
}
