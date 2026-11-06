import { Component, ElementRef, HostListener, inject, OnInit, signal, ViewChild } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet, FormsModule, NgTemplateOutlet],
  selector: 'app-shell.page',
  styleUrl: './shell.page.css',
  templateUrl: './shell.page.html',
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
