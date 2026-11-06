import { DecimalPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { Repair, RepairStatus } from '../../core/models/model';


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
  imports: [FormsModule, DecimalPipe, DatePipe],
  selector: 'app-repair.page',
  styleUrl: './repair.page.css',
  templateUrl: './repair.page.html',
})
export class RepairPage {

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
