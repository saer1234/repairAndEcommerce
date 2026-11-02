import { DecimalPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Api } from './api';
import { AuthService } from './auth.service';
import { CompanyContext } from './company-context';
import { Repair, RepairStatus } from './models';

function apiMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'Could not reach the API. Check the server connection and try again.';
    if (error.status === 403) return 'You do not have permission to view this repair.';
    if (error.status === 404) return 'This repair or company could not be found.';
    if (error.status === 409) return 'The repair changed. Refresh and try again.';
  }
  return fallback;
}

@Component({
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe],
  template: `
    <section class="page-heading">
      <div>
        <h1>My repair requests</h1>
        <p class="muted">Submit a device to any active shop, and follow your own repair history.</p>
      </div>
      <button class="secondary" type="button" (click)="refresh()" [disabled]="loading()">Refresh</button>
    </section>

    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (message()) { <p class="success" role="status">{{ message() }}</p> }

    @if (!ctx.selected()) {
      <div class="panel">Select a shop in <strong>Browse customer store (public)</strong> to submit a repair.</div>
    } @else {
      <div class="repair-customer-layout">
        @if (auth.user()?.platformRole === 'CLIENT' && !ctx.mine().length) {
        <section class="panel">
          <h3>New request — {{ ctx.company()?.name }}</h3>
          <p class="muted tiny">An employee can assess the problem and update your request after submission.</p>
          <form #requestForm="ngForm" (ngSubmit)="submit()">
            <h4>Client contact details</h4>
            <div class="field-pair">
              <label>Client full name (from your account)
                <input [value]="auth.user()?.fullName || ''" readonly autocomplete="name" />
              </label>
              <label>Client phone number *
                <input name="clientPhone" type="tel" [(ngModel)]="clientPhone"
                       required minlength="6" maxlength="40" autocomplete="tel"
                       placeholder="+961 70 123 456" />
              </label>
            </div>
            <p class="muted tiny">The client name is taken from the signed-in account and cannot be changed in this form. You can update the contact phone number.</p>
            <h4>Device details</h4>
            <label>Device type *
              <input name="deviceType" [(ngModel)]="deviceType" required maxlength="100" placeholder="Mobile phone" />
            </label>
            <div class="field-pair">
              <label>Brand
                <input name="brand" [(ngModel)]="brand" maxlength="100" placeholder="Samsung" />
              </label>
              <label>Model
                <input name="model" [(ngModel)]="model" maxlength="120" placeholder="Galaxy S24" />
              </label>
            </div>
            <label>Issue description *
              <textarea name="issueDescription" [(ngModel)]="issueDescription" required maxlength="4000" placeholder="Describe what stopped working"></textarea>
            </label>
            <button class="primary" type="submit" [disabled]="requestForm.invalid || saving()">
              {{ saving() ? 'Submitting…' : 'Submit repair request' }}
            </button>
          </form>
        </section>
        } @else {
          <section class="panel"><p class="muted">Only registered client accounts without company staff roles can submit personal repair requests. Staff can create requests for clients in the Repair Workspace.</p></section>
        }

        <section class="panel">
          <h3>Requests at {{ ctx.company()?.name }}</h3>
          @if (loading()) { <p class="muted">Loading repairs…</p> }
          @if (!loading() && !customerRepairs().length) {
            <p class="muted">You have no repair requests at this shop yet.</p>
          }
          <div class="repair-list">
            @for (r of customerRepairs(); track r.id) {
              <button type="button" class="repair-list-card" [class.selected]="selected()?.id === r.id"
                      (click)="selectRepair(r)">
                <span><strong>#{{ r.id }} — {{ deviceName(r) }}</strong>
                  <small class="muted">{{ r.createdAt | date:'mediumDate' }}</small></span>
                <span class="chip">{{ readableStatus(r.status) }}</span>
              </button>
            }
          </div>
        </section>
      </div>
      @if (selected(); as r) {
        <section class="panel">
          <div class="panel-heading">
            <div>
              <h3>Repair #{{ r.id }} — {{ deviceName(r) }}</h3>
              <p class="muted">{{ r.issueDescription }}</p>
              <p class="muted tiny">Contact: {{ r.clientName || 'Not recorded' }} · {{ r.clientPhone || 'Not recorded' }}</p>
            </div>
            <span class="chip">{{ readableStatus(r.status) }}</span>
          </div>
          <div class="repair-detail-grid">
            <div><span class="muted tiny">Parts charge</span><strong>{{ r.partsCharge | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
            <div><span class="muted tiny">Labor charge</span><strong>{{ r.laborCharge | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
            <div><span class="muted tiny">Current total</span><strong>{{ r.totalCharge | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
          </div>
          <p class="muted tiny">Charges may change until the repair is finalized. Internal company costs are not displayed.</p>
          <div class="repair-detail-columns">
            <div>
              <h4>Parts used</h4>
              @for (part of r.parts; track part.id) {
                <p>{{ part.itemName }} × {{ part.quantity }} — {{ part.unitSellingPrice * part.quantity | number:'1.2-2' }} {{ r.currencyCode }}</p>
              }
              @if (!r.parts?.length) { <p class="muted">No parts recorded.</p> }
            </div>
            <div>
              <h4>Repair history</h4>
              @for (update of r.updates; track update.id) {
                <div class="repair-timeline-entry">
                  <strong>{{ readableStatus(update.status) }}</strong>
                  <small class="muted">{{ update.createdAt | date:'medium' }}</small>
                  @if (update.note) { <p>{{ update.note }}</p> }
                </div>
              }
              @if (!r.updates?.length) { <p class="muted">No updates yet.</p> }
            </div>
          </div>
        </section>
      }
    }
  `
})
export class RepairsPage {
  private readonly api = inject(Api);
  readonly auth = inject(AuthService);
  readonly ctx = inject(CompanyContext);
  readonly customerRepairs = signal<Repair[]>([]);
  readonly selected = signal<Repair | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  clientPhone = this.auth.user()?.phone ?? '';
  deviceType = '';
  brand = '';
  model = '';
  issueDescription = '';

  constructor() {
    effect(() => {
      const id = this.ctx.selected();
      untracked(() => {
        this.customerRepairs.set([]);
        this.selected.set(null);
        this.error.set('');
        this.message.set('');
        if (id) this.loadCustomer(id);
      });
    });
  }

  readonly readableStatus = (status: RepairStatus): string => status.replace(/_/g, ' ');
  deviceName(r: Repair): string { return [r.brand, r.model].filter(Boolean).join(' ') || r.deviceType; }

  refresh(): void {
    const id = this.ctx.selected();
    if (id) this.loadCustomer(id);
  }

  private loadCustomer(companyId: number): void {
    this.loading.set(true);
    this.api.get<Repair[]>(`/companies/${companyId}/repairs/mine`).subscribe({
      next: repairs => {
        if (this.ctx.selected() !== companyId) return;
        this.customerRepairs.set(repairs);
        const existingId = this.selected()?.id;
        if (existingId != null) this.selected.set(repairs.find(r => r.id === existingId) ?? null);
        this.loading.set(false);
      },
      error: err => {
        if (this.ctx.selected() !== companyId) return;
        this.error.set(apiMessage(err, 'Could not load your repair requests.'));
        this.loading.set(false);
      }
    });
  }

  selectRepair(repair: Repair): void {
    const companyId = this.ctx.selected();
    if (!companyId) return;
    this.selected.set(repair);
    this.api.get<Repair>(`/companies/${companyId}/repairs/${repair.id}`).subscribe({
      next: current => {
        if (this.ctx.selected() === companyId && this.selected()?.id === repair.id) this.selected.set(current);
      },
      error: err => this.error.set(apiMessage(err, 'Could not load repair details.'))
    });
  }

  submit(): void {
    const companyId = this.ctx.selected();
    if (!companyId || this.saving() || !this.auth.user()?.fullName?.trim() ||
        this.clientPhone.trim().length < 6 || !this.deviceType.trim() || !this.issueDescription.trim()) return;
    this.saving.set(true);
    this.error.set('');
    this.message.set('');
    this.api.post<Repair>(`/companies/${companyId}/repairs`, {
      clientPhone: this.clientPhone.trim(),
      deviceType: this.deviceType.trim(), brand: this.brand.trim(), model: this.model.trim(),
      issueDescription: this.issueDescription.trim()
    }).subscribe({
      next: repair => {
        this.saving.set(false);
        if (this.ctx.selected() !== companyId) return;
        this.message.set(`Repair #${repair.id} submitted successfully.`);
        this.deviceType = ''; this.brand = ''; this.model = ''; this.issueDescription = '';
        // Keep the client contact populated for subsequent requests.
        this.selected.set(repair);
        this.loadCustomer(companyId);
      },
      error: err => {
        this.saving.set(false);
        this.error.set(apiMessage(err, 'Unable to submit your repair.'));
      }
    });
  }
}
