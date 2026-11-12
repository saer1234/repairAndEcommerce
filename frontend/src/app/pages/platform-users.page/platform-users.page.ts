import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../api';

interface PlatformUser {
  id: number; fullName: string; email: string; phone: string | null;
  platformRole: 'USER' | 'OWNER' | 'CLIENT' | 'SUPER_ADMIN';
  active: boolean; createdAt: string | null;
}
interface PlatformUsersResponse {
  users: PlatformUser[]; page: number; pageSize: number;
  totalUsers: number; totalPages: number;
}

@Component({
  selector: 'app-platform-users',
  imports: [FormsModule, DatePipe],
  templateUrl: './platform-users.page.html',
  styleUrl: './platform-users.page.css'
})
export class PlatformUsersPage implements OnInit {
  private readonly api = inject(Api);
  readonly users = signal<PlatformUser[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly page = signal(0);
  readonly pageSize = signal(20);
  readonly totalPages = signal(0);
  readonly totalUsers = signal(0);
  search = '';
  private appliedSearch = '';

  changeRole(user: PlatformUser, role: PlatformUser['platformRole']): void {
    if (role === 'SUPER_ADMIN' || user.platformRole === 'SUPER_ADMIN' || role === user.platformRole) return;
    this.error.set('');
    this.api.patch<PlatformUser>(`/platform/users/${user.id}/role`, {role}).subscribe({
      next: updated => this.users.update(list => list.map(u => u.id === updated.id ? updated : u)),
      error: err => {this.error.set(err.error?.detail || 'Unable to update role. Verify company memberships.'); this.load();}
    });
  }

  ngOnInit(): void { this.load(); }

  find(): void {
    this.appliedSearch = this.search.trim();
    this.load(0);
  }

  changePageSize(value: string | number): void {
    this.pageSize.set(Number(value));
    this.load(0);
  }

  load(page = this.page()): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) return;
    this.loading.set(true);
    this.error.set('');
    const path = `/platform/users?page=${page}&size=${this.pageSize()}&search=${encodeURIComponent(this.appliedSearch)}`;
    this.api.get<PlatformUsersResponse>(path).subscribe({
      next: result => {
        this.users.set(result.users);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalUsers.set(result.totalUsers);
        this.loading.set(false);
      },
      error: err => {
        this.users.set([]);
        this.loading.set(false);
        this.error.set(err.status === 403 ?
          'Only the platform owner can access the full user directory.' :
          'Unable to load users. Check your connection and try again.');
      }
    });
  }
}
