import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { Api } from '../../api';
import { CustomerRepairTracking, RepairStatus } from '../../core/models/model';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RepairInvoicePanel } from '../../shared/components/repair-invoice.component/repair-invoice.component';
const STEPS: RepairStatus[] = ['NEW', 'DIAGNOSING', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED'];

@Component({
  imports: [DatePipe, DecimalPipe, RepairInvoicePanel],
  selector: 'app-repair-tracking.page',
  styleUrl: './repair-tracking.page.css',
  templateUrl: './repair-tracking.page.html',
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
