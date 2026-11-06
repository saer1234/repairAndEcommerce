import { inject, Service, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Api } from '../../api';
import { Company } from '../models/model';

@Service()
export class CompanyContext {
    private readonly api = inject(Api);
  // All public companies, regardless of the account's company memberships.
  readonly available = signal<Company[]>([]);
  readonly loaded = signal(false);
  // Companies in which the signed-in user is a member.
  readonly mine = signal<Company[]>([]);
  // Public customer store selection. Allowed to point to ANY active company.
  readonly selected = signal<number | null>(null);
  // Separate private workspace selection. Must be one of 'mine'.
  readonly managedSelected = signal<number | null>(null);

  role() {
    return this.mine().find(c => c.id === this.managedSelected())?.yourRole ?? null;
  }
  company() {
    return this.available().find(c => c.id === this.selected());
  }
  managedCompany() {
    return this.mine().find(c => c.id === this.managedSelected());
  }
  canManage() { return this.role() === 'OWNER' || this.role() === 'MANAGER'; }

  reset(): void {
    this.loaded.set(false);
    this.mine.set([]);
    this.available.set([]);
    this.selected.set(null);
    this.managedSelected.set(null);
  }

  refresh(onError: (e: unknown) => void = () => {}): void {
    this.loaded.set(false);
    forkJoin({
      mine: this.api.get<Company[]>('/companies'),
      all: this.api.get<Company[]>('/catalog/companies')
    }).subscribe({
      next: ({ mine, all }) => {
        this.mine.set(mine);
        this.available.set(all);
        if (!mine.some(c => c.id === this.managedSelected())) {
          this.managedSelected.set(mine[0]?.id ?? null);
        }
        if (!all.some(c => c.id === this.selected())) {
          const preferred = all.find(c => c.id === this.managedSelected());
          this.selected.set(preferred?.id ?? all[0]?.id ?? null);
        }
        this.loaded.set(true);
      },
      error: onError
    });
  }
}
