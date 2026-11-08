import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { CompanyAbout } from '../../core/models/company-about.model';
import { Company } from '../../core/models/model';

@Component({
  selector: 'app-about-page', standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './about.page.html', styleUrl: './about.page.css'
})
export class AboutPage {
  readonly auth = inject(AuthService);
  readonly ctx = inject(CompanyContext);
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly companies = signal<Company[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly profile = signal<CompanyAbout | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly ownerCanEdit = () => this.auth.signedIn() &&
    this.ctx.role() === 'OWNER' && this.ctx.managedSelected() === this.selectedId();

  constructor() {
    this.api.get<Company[]>('/catalog/companies').pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: list => {
        this.companies.set(list);
        const rawId = Number(this.route.snapshot.queryParamMap.get('companyId'));
        const preferred = list.find(c => c.id === rawId)?.id ??
          list.find(c => c.id === this.ctx.selected())?.id ?? list[0]?.id ?? null;
        if (preferred) this.selectCompany(preferred);
      },
      error: () => this.error.set('Could not load participating shops.')
    });
  }
  selectCompany(value: number | string): void {
    const id = Number(value);
    if (!this.companies().some(c => c.id === id)) return;
    // Keep the customer's selected store in sync with the shop shown here, so Support opens the right company.
    if (this.auth.canShop()) this.ctx.selected.set(id);
    this.selectedId.set(id); this.profile.set(null); this.loading.set(true); this.error.set('');
    this.api.get<CompanyAbout>(`/catalog/companies/${id}/about`)
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: about => { if (this.selectedId() === id) { this.profile.set(about); this.loading.set(false); } },
        error: () => { if (this.selectedId() === id) { this.loading.set(false); this.error.set('Could not load shop information.'); } }
      });
  }
}
