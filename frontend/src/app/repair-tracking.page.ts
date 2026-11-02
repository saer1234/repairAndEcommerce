import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { Api } from './api';
import { CustomerRepairTracking, RepairStatus } from './models';
import { RepairInvoicePanel } from './repair-invoice.component';

const STEPS: RepairStatus[] = ['NEW', 'DIAGNOSING', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED'];

@Component({
  standalone: true,
  imports: [DatePipe, DecimalPipe, RepairInvoicePanel],
  template: `
    <section class="page-heading">
      <div>
        <h1>Track my repairs</h1>
        <p class="muted">Track devices you've submitted, across all repair shops on the platform.</p>
      </div>
      <button type="button" class="secondary" (click)="refresh()" [disabled]="loading()">Refresh</button>
    </section>
    @if (error()) {<p class="error" role="alert">{{ error() }}</p>}
    <p class="muted tiny">Updates are checked every 30 seconds while this page is open.</p>
    @if (loading() && !repairs().length) { <p class="panel muted">Loading your repairs…</p> }
    @if (!loading() && !repairs().length) {
      <div class="panel">No repair requests yet. Open <a href="/repairs">Request a Repair</a> to submit a device.</div>
    }
    <div class="repair-tracking-layout">
      <section class="panel">
        <h3>My devices ({{ repairs().length }})</h3>
        <div class="repair-list">
          @for (r of repairs(); track r.id) {
            <button type="button" class="repair-list-card" [class.selected]="selected()?.id === r.id"
                    (click)="select(r)">
              <span><strong>#{{ r.id }} — {{ device(r) }}</strong>
                <small class="muted">{{ r.companyName }}</small>
                <small class="muted">{{ asUtc(r.createdAt) | date:'mediumDate' }}</small>
              </span>
              <span class="chip">{{ label(r.status) }}</span>
            </button>
          }
        </div>
      </section>
      <section class="panel" aria-live="polite">
        @if (selected(); as r) {
          <div class="panel-heading">
            <div><h3>Repair #{{ r.id }} — {{ device(r) }}</h3>
              <p class="muted">{{ r.companyName }}</p></div>
            <span class="chip">{{ label(r.status) }}</span>
          </div>
          <p><strong>Reported issue:</strong> {{ r.issueDescription }}</p>
          @if (r.status === 'WAITING_PARTS') {
            <p class="tracking-wait">Waiting for a replacement part. The repair team will update you when work continues.</p>
          }
          @if (r.status === 'CANCELLED') {
            <p class="tracking-wait">This repair was cancelled.</p>
          } @else {
            <h4>Repair progress</h4>
            <div class="tracking-steps" aria-label="Repair milestones">
              @for (step of steps; track step; let i = $index) {
                <div class="tracking-step" [class.done]="stepIndex(r.status) >= i"
                     [attr.aria-current]="r.status === step ? 'step' : null">
                  <span class="tracking-dot">{{ stepIndex(r.status) >= i ? '✓' : i + 1 }}</span>
                  <small>{{ label(step) }}</small>
                </div>
              }
            </div>
          }
          <div class="repair-detail-grid">
            <div><span class="muted tiny">Submitted</span><strong>{{ asUtc(r.createdAt) | date:'medium' }}</strong></div>
            <div><span class="muted tiny">Current listed charges</span>
              <strong>{{ r.quotedTotal | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
          </div>
          <p class="muted tiny">Charges can change until the shop finalizes your repair. The amount shown is not a payment receipt.</p>
          @if (r.status === 'COMPLETED' || r.status === 'DELIVERED') {
            <app-repair-invoice [companyId]="r.companyId" [repairId]="r.id" [status]="r.status" [manager]="false" />
          }
          <h4>Updates from the repair shop</h4>
          <div class="tracking-timeline">
            @for (event of r.timeline; track event.id) {
              <div class="tracking-event">
                <strong>{{ label(event.status) }}</strong>
                <small class="muted">{{ asUtc(event.createdAt) | date:'medium' }}</small>
                @if (event.note) { <p>{{ event.note }}</p> }
              </div>
            }
            @if (!r.timeline.length) { <p class="muted">The shop has not published any updates yet.</p> }
          </div>
        } @else if (repairs().length) {
          <p class="muted">Select a device to view its repair progress.</p>
        }
      </section>
    </div>
  `
})
export class RepairTrackingPage implements OnInit {
  private readonly api = inject(Api);
  readonly repairs = signal<CustomerRepairTracking[]>([]);
  readonly selected = signal<CustomerRepairTracking | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly steps = STEPS;

  constructor() {
    interval(30_000).pipe(takeUntilDestroyed()).subscribe(() => this.refresh(true));
  }

  ngOnInit(): void { this.refresh(); }

  label(s: RepairStatus): string { return s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()); }
  asUtc(value: string): string { return value.endsWith('Z') ? value : `${value}Z`; }
  device(r: CustomerRepairTracking): string { return [r.brand, r.model].filter(Boolean).join(' ') || r.deviceType; }
  stepIndex(s: RepairStatus): number {
    if (s === 'WAITING_PARTS') return 1;
    return Math.max(0, STEPS.indexOf(s));
  }
  select(repair: CustomerRepairTracking): void {
    this.selected.set(repair);
    this.api.get<CustomerRepairTracking>(`/repairs/tracking/${repair.id}`).subscribe({
      next: latest => { if (this.selected()?.id === repair.id) this.selected.set(latest); },
      error: e => this.error.set(this.errorMessage(e))
    });
  }
  refresh(silent = false): void {
    if (this.loading()) return;
    this.loading.set(true);
    if (!silent) this.error.set('');
    this.api.get<CustomerRepairTracking[]>('/repairs/tracking').subscribe({
      next: list => {
        this.repairs.set(list);
        const id = this.selected()?.id;
        this.selected.set(list.find(v => v.id === id) ?? list[0] ?? null);
        this.loading.set(false);
      },
      error: e => { this.loading.set(false); this.error.set(this.errorMessage(e)); }
    });
  }
  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) return 'Cannot reach Spring Boot on port 8081.';
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Your login expired. Please sign in again.';
    return 'Unable to load repair progress.';
  }
}
