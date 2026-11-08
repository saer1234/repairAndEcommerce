import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api } from '../../api';
import { CompanyContext } from '../../core/services/company-context';
import { CompanyAbout, CompanyAboutEdit } from '../../core/models/company-about.model';

@Component({ selector: 'app-about-settings', standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './about-settings.page.html', styleUrl: './about-settings.page.css' })
export class AboutSettingsPage {
  private readonly api = inject(Api);
  private readonly destroy = inject(DestroyRef);
  readonly ctx = inject(CompanyContext);
  readonly companyId = computed(() => this.ctx.managedSelected());
  readonly canEdit = computed(() => this.ctx.role() === 'OWNER');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  about: CompanyAboutEdit = this.empty();
  private generation = 0;
  constructor() {
    effect(() => {
      const id = this.companyId();
      untracked(() => { this.error.set(''); this.success.set(''); this.about = this.empty(); if (id && this.canEdit()) this.load(id); });
    });
  }
  private empty(): CompanyAboutEdit {
    return { headline:'', description:'', storeAddress:'', contactPhone:'', contactEmail:'',
      mapsUrl:'', instagramUrl:'', facebookUrl:'', tiktokUrl:'', whatsappUrl:'', websiteUrl:'', openingHours:'' };
  }
  private load(id: number): void {
    const generation = ++this.generation;
    this.api.get<CompanyAbout>(`/catalog/companies/${id}/about`).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => {
        if (generation !== this.generation || this.companyId() !== id) return;
        this.about = { headline: result.headline, description: result.description ?? '',
          storeAddress: result.storeAddress ?? '', contactPhone: result.contactPhone ?? '',
          contactEmail: result.contactEmail ?? '', mapsUrl: result.mapsUrl ?? '',
          instagramUrl: result.instagramUrl ?? '', facebookUrl: result.facebookUrl ?? '',
          tiktokUrl: result.tiktokUrl ?? '', whatsappUrl: result.whatsappUrl ?? '',
          websiteUrl: result.websiteUrl ?? '', openingHours: result.openingHours ?? '' };
      },
      error: () => this.error.set('Could not load this company profile.')
    });
  }
  save(): void {
    const id = this.companyId();
    if (!id || !this.canEdit() || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.put<CompanyAbout>(`/companies/${id}/about`, this.about).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => { this.busy.set(false); this.success.set('Published! Customers can now see the updated About Us page.');
        this.about.headline = result.headline; },
      error: () => { this.busy.set(false); this.error.set('Unable to save. Check URLs (http/https) and field lengths.'); }
    });
  }
}
