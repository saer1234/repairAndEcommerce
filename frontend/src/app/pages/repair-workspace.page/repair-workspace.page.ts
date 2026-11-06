import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompanyMember, Repair, RepairClientOption, RepairPart, RepairProfitSummary, RepairStatus, StockPart } from '../../core/models/model';
import { RepairInvoicePanel } from '../../shared/components/repair-invoice.component/repair-invoice.component';
import { switchMap } from 'rxjs';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { Fit, compatibilityStatus } from '../../shared/utils/device-fit';
import { matchesVerifiedFit } from '../../shared/utils/verified-fit';
const ALLOWED: Record<RepairStatus, RepairStatus[]> = {
  NEW: ['DIAGNOSING', 'CANCELLED'],
  DIAGNOSING: ['WAITING_PARTS', 'IN_PROGRESS', 'CANCELLED'],
  WAITING_PARTS: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['WAITING_PARTS', 'COMPLETED', 'CANCELLED'],
  COMPLETED: ['DELIVERED'],
  DELIVERED: [], CANCELLED: []
};

function errorText(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Cannot connect to Spring Boot on port 8081.';
    if (err.status === 403) return 'Your account does not have permission for this action.';
    if (err.status === 404) return 'Record not found, or this company is not available to you.';
    if (err.status === 409) {
      const details = err.error?.message || err.error?.detail;
      return typeof details === 'string' && details.trim()
        ? details
        : 'Repair cannot transition to this status. Check technician assignment and the technical report.';
    }
    if (err.status === 400) return 'Invalid value. Check the information entered.';
  }
  return fallback;
}
@Component({
  imports: [FormsModule, DatePipe, DecimalPipe, RepairInvoicePanel],
  selector: 'app-repair-workspace.page',
  styleUrl: './repair-workspace.page.css',
  templateUrl: './repair-workspace.page.html',
})
export class RepairWorkspacePage {

  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  readonly ctx = inject(CompanyContext);
  readonly repairs = signal<Repair[]>([]);
  readonly selected = signal<Repair | null>(null);
  readonly members = signal<CompanyMember[]>([]);
  readonly stock = signal<StockPart[]>([]);
  readonly summary = signal<RepairProfitSummary | null>(null);
  readonly clientResults = signal<RepairClientOption[]>([]);
  readonly clientsLoading = signal(false);
  readonly clientSearched = signal(false);
  readonly clientSearchError = signal('');
  readonly creatingRepair = signal(false);
  clientSearch = '';
  private clientSearchRequestId = 0;
  selectedClientId: number | null = null;
  newClientPhone = '';
  newDeviceType = '';
  newBrand = '';
  newModel = '';
  newIssue = '';
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly statuses: RepairStatus[] = ['NEW', 'DIAGNOSING', 'WAITING_PARTS', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED'];
  filterStatus = 'ALL';
  filterQuery = '';
  employeeId: number | null = null;
  nextStatus: RepairStatus | null = null;
  statusNote = '';
  customerNote = '';
  progressMessage = '';
  partItemId: number | null = null;
  partSearch = '';
  partCategory = 'ALL';
  partFitMode: 'verified' | 'include-unverified' = 'verified';
  confirmUnverified = false;
  partQuantity = 1;
  reportDiagnosis = '';
  reportWork = '';
  laborCharge = 0;
  laborCost = 0;

  constructor() {
    effect(() => {
      const companyId = this.ctx.managedSelected();
      const role = this.ctx.role();
      untracked(() => {
        this.selected.set(null);
        this.clientSearchRequestId++;
        this.clientResults.set([]);
        this.selectedClientId = null;
        this.clientSearch = '';
        this.clientsLoading.set(false);
        this.clientSearched.set(false);
        this.clientSearchError.set('');
        this.repairs.set([]);
        this.members.set([]);
        this.stock.set([]);
        this.summary.set(null);
        this.error.set('');
        this.message.set('');
        this.loading.set(false);
        this.busy.set(false);
        if (companyId && ['OWNER', 'MANAGER', 'EMPLOYEE'].includes(role ?? '')) this.refresh(companyId);
      });
    });
  }

  onClientSearchChanged(): void {
    this.clientSearchRequestId++;
    this.clientResults.set([]);
    this.selectedClientId = null;
    this.newClientPhone = '';
    this.clientsLoading.set(false);
    this.clientSearched.set(false);
    this.clientSearchError.set('');
  }
  searchClients(): void {
    const companyId = this.ctx.managedSelected();
    const q = this.clientSearch.trim();
    if (!companyId || !this.isStaff() || q.length < 2 || q.length > 80) return;
    this.clientSearchError.set('');
    this.clientResults.set([]);
    this.selectedClientId = null;
    this.clientSearched.set(false);
    this.clientsLoading.set(true);
    const requestId = ++this.clientSearchRequestId;
    this.api.get<RepairClientOption[]>(`/companies/${companyId}/repairs/eligible-clients?q=${encodeURIComponent(q)}`).subscribe({
      next: options => {
        if (requestId !== this.clientSearchRequestId || this.ctx.managedSelected() !== companyId ||
            this.clientSearch.trim() !== q) return;
        this.clientResults.set(options);
        this.clientsLoading.set(false);
        this.clientSearched.set(true);
      },
      error: err => {
        if (requestId !== this.clientSearchRequestId || this.ctx.managedSelected() !== companyId) return;
        this.clientsLoading.set(false);
        this.clientSearchError.set(errorText(err, 'Could not search client accounts.'));
      }
    });
  }
  onClientSelected(): void {
    this.newClientPhone = this.clientResults().find(c => c.id === this.selectedClientId)?.phone ?? '';
  }
  createForClient(): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId || !this.isStaff() || this.creatingRepair() || this.selectedClientId === null ||
        !this.clientResults().some(c => c.id === this.selectedClientId) ||
        this.newClientPhone.trim().length < 6 || !this.newDeviceType.trim() || !this.newIssue.trim()) return;
    this.creatingRepair.set(true); this.error.set(''); this.message.set('');
    this.api.post<Repair>(`/companies/${companyId}/repairs/for-client`, {
      clientId: this.selectedClientId,
      clientPhone: this.newClientPhone.trim(), deviceType: this.newDeviceType.trim(),
      brand: this.newBrand.trim(), model: this.newModel.trim(), issueDescription: this.newIssue.trim()
    }).subscribe({
      next: repair => {
        this.creatingRepair.set(false);
        if (this.ctx.managedSelected() !== companyId) return;
        this.message.set(`Repair #${repair.id} created for ${repair.clientName}.`);
        this.clientResults.set([]); this.clientSearched.set(false); this.selectedClientId = null;
        this.clientSearch = ''; this.newClientPhone = ''; this.newDeviceType = '';
        this.newBrand = ''; this.newModel = ''; this.newIssue = '';
        this.refresh(companyId);
      },
      error: err => {
        this.creatingRepair.set(false);
        this.error.set(errorText(err, 'Could not register repair for client.'));
      }
    });
  }

  isStaff(): boolean { return ['OWNER', 'MANAGER', 'EMPLOYEE'].includes(this.ctx.role() ?? ''); }
  canManage(): boolean { return this.ctx.canManage(); }
  isOpen(r: Repair): boolean { return !['COMPLETED', 'DELIVERED', 'CANCELLED'].includes(r.status); }
  partsAllowed(status: RepairStatus): boolean { return ['DIAGNOSING', 'WAITING_PARTS', 'IN_PROGRESS'].includes(status); }
  canEdit(r: Repair): boolean {
    return this.canManage() || (this.ctx.role() === 'EMPLOYEE' && r.assignedEmployeeId === this.auth.user()?.id);
  }
  nextStatuses(r: Repair): RepairStatus[] {
    return ALLOWED[r.status].filter(s => (s !== 'DELIVERED' || this.canManage()) &&
      (s !== 'CANCELLED' || r.parts.length === 0));
  }
  readonly readableStatus = (status: RepairStatus): string => status.replace(/_/g, ' ');
  deviceName(r: Repair): string { return [r.brand, r.model].filter(Boolean).join(' ') || r.deviceType; }
  employeeName(id: number | null): string {
    if (id === null) return 'Not assigned';
    const member = this.members().find(m => m.userId === id);
    return member ? member.fullName : `User #${id}`;
  }
  openCount(): number { return this.repairs().filter(r => this.isOpen(r)).length; }
  doneCount(): number { return this.repairs().filter(r => r.status === 'COMPLETED' || r.status === 'DELIVERED').length; }
  filteredRepairs(): Repair[] {
    const search = this.filterQuery.trim().toLowerCase();
    return this.repairs().filter(r => (this.filterStatus === 'ALL' || r.status === this.filterStatus) &&
      (!search || `${r.id} ${r.clientName ?? ''} ${r.clientPhone ?? ''} ${r.brand ?? ''} ${r.model ?? ''} ${r.deviceType} ${r.issueDescription}`.toLowerCase().includes(search)));
  }
  /** Exact model-specific records take precedence; wildcard matches are never labeled verified. */
  partFit(item: StockPart, repair: Repair): Fit {
    const exact = !!repair.brand?.trim() && !!repair.model?.trim() && matchesVerifiedFit(item, {
      deviceType: repair.deviceType, deviceBrand: repair.brand ?? '', deviceModel: repair.model ?? ''
    });
    if (exact) return 'compatible';
    const fit = compatibilityStatus(item, repair);
    return fit === 'incompatible' ? 'incompatible' : 'unspecified';
  }
  verifiedStockCount(repair: Repair): number {
    return this.stock().filter(item => item.active && item.stockQuantity > 0 && this.partFit(item, repair) === 'compatible').length;
  }
  unverifiedStockCount(repair: Repair): number {
    return this.stock().filter(item => item.active && item.stockQuantity > 0 && this.partFit(item, repair) === 'unspecified').length;
  }
  stockCategories(repair: Repair): string[] {
    return [...new Set(this.stock().filter(item => item.active && item.stockQuantity > 0 && this.partFit(item,repair) !== 'incompatible')
      .map(item => item.category?.trim() || 'General'))].sort((a,b) => a.localeCompare(b));
  }
  resetPartChoice(): void {
    this.partItemId = null;
    this.confirmUnverified = false;
  }
  selectedPartFit(repair: Repair): Fit | null {
    const item = this.stock().find(i => i.id === this.partItemId);
    return item ? this.partFit(item, repair) : null;
  }
  availableStock(): StockPart[] {
    const repair = this.selected();
    if (!repair) return [];
    const query = this.partSearch.toLowerCase().trim();
    return this.stock().filter(item => item.active && item.stockQuantity > 0 &&
      (this.partFit(item, repair) === 'compatible' ||
       (this.partFitMode === 'include-unverified' && this.partFit(item, repair) === 'unspecified')) &&
      (this.partCategory === 'ALL' || (item.category || 'General').toLowerCase() === this.partCategory.toLowerCase()) &&
      (!query || `${item.name} ${item.sku} ${item.category || ''} ${item.deviceType} ${item.deviceBrand} ${(item.compatibleDevices||[]).map(f=>f.deviceModel).join(' ')}`.toLowerCase().includes(query)))
      .sort((a,b) => (this.partFit(a,repair)==='compatible' ? 0 : 1) - (this.partFit(b,repair)==='compatible' ? 0 : 1) || a.name.localeCompare(b.name));
  }

  refresh(companyId = this.ctx.managedSelected()): void {
    if (!companyId || !this.isStaff()) return;
    this.error.set('');
    this.loading.set(true);
    this.api.get<Repair[]>(`/companies/${companyId}/repairs`).subscribe({
      next: list => {
        if (this.ctx.managedSelected() !== companyId) return;
        this.repairs.set(list);
        const selectedId = this.selected()?.id;
        if (selectedId !== undefined && selectedId !== null) {
          const current = list.find(r => r.id === selectedId) ?? null;
          this.selected.set(current);
          if (current) this.initEditor(current);
        }
        this.loading.set(false);
      },
      error: err => {
        if (this.ctx.managedSelected() !== companyId) return;
        this.loading.set(false);
        this.error.set(errorText(err, 'Could not load this company repair queue.'));
      }
    });
    this.api.get<StockPart[]>(`/companies/${companyId}/inventory/stock`).subscribe({
      next: list => { if (this.ctx.managedSelected() === companyId) this.stock.set(list); },
      error: err => { if (this.ctx.managedSelected() === companyId) this.error.set(errorText(err, 'Could not load available parts.')); }
    });
    if (this.canManage()) {
      this.api.get<CompanyMember[]>(`/companies/${companyId}/members`).subscribe({
        next: list => { if (this.ctx.managedSelected() === companyId) this.members.set(list); },
        error: err => { if (this.ctx.managedSelected() === companyId) this.error.set(errorText(err, 'Could not load company team members.')); }
      });
      this.api.get<RepairProfitSummary>(`/companies/${companyId}/finance/repair-summary`).subscribe({
        next: v => { if (this.ctx.managedSelected() === companyId) this.summary.set(v); },
        error: err => { if (this.ctx.managedSelected() === companyId) this.error.set(errorText(err, 'Could not load repair summary.')); }
      });
    }
  }

  selectRepair(r: Repair): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId) return;
    this.selected.set(r);
    this.initEditor(r);
    this.api.get<Repair>(`/companies/${companyId}/repairs/${r.id}/manage`).subscribe({
      next: latest => {
        if (this.ctx.managedSelected() !== companyId || this.selected()?.id !== r.id) return;
        this.selected.set(latest);
        this.initEditor(latest);
      },
      error: err => { if (this.ctx.managedSelected() === companyId) this.error.set(errorText(err, 'Could not open this repair.')); }
    });
  }

  private initEditor(r: Repair): void {
    this.employeeId = r.assignedEmployeeId;
    this.nextStatus = this.nextStatuses(r)[0] ?? null;
    this.statusNote = '';
    this.customerNote = '';
    this.progressMessage = '';
    this.reportDiagnosis = r.diagnosis ?? '';
    this.reportWork = r.workPerformed ?? '';
    this.laborCharge = r.laborCharge;
    this.laborCost = r.laborCost ?? 0;
    this.partItemId = null;
    this.partSearch = '';
    this.partCategory = 'ALL';
    this.partFitMode = 'verified';
    this.confirmUnverified = false;
    this.partQuantity = 1;
  }

  private updateAfterChange(updated: Repair, notice: string): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId || updated.companyId !== companyId) return;
    this.message.set(notice);
    if (this.selected()?.id === updated.id) this.selected.set(updated);
    this.repairs.update(list => list.map(r => r.id === updated.id ? updated : r));
    if (this.selected()?.id === updated.id) this.initEditor(updated);
    this.refresh(companyId);
  }

  private fail(error: unknown, fallback: string): void {
    this.busy.set(false);
    this.error.set(errorText(error, fallback));
  }
  saveReport(): void {
    const r=this.selected(),companyId=this.ctx.managedSelected();
    if(!r||!companyId||!this.canEdit(r)||this.busy()||!this.reportDiagnosis.trim()||!this.reportWork.trim())return;
    this.busy.set(true);
    this.api.put<Repair>(`/companies/${companyId}/repairs/${r.id}/report`,{
      diagnosis:this.reportDiagnosis.trim(),workPerformed:this.reportWork.trim()
    }).subscribe({
      next:v=>{this.busy.set(false);this.updateAfterChange(v,'Technician report saved.');},
      error:e=>this.fail(e,'Could not save technician report.')
    });
  }

  assign(): void {
    const r = this.selected(), companyId = this.ctx.managedSelected();
    if (!r || !companyId || !this.canManage() || !this.isOpen(r) || this.employeeId === null || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.api.put<Repair>(`/companies/${companyId}/repairs/${r.id}/assignment`, {employeeId: this.employeeId}).subscribe({
      next: value => { this.busy.set(false); this.updateAfterChange(value, 'Employee assigned.'); },
      error: err => this.fail(err, 'Could not assign employee.')
    });
  }
  updateStatus(): void {
    const r = this.selected(), companyId = this.ctx.managedSelected();
    const nextStatus = this.nextStatus;
    if (!r || !companyId || !this.canEdit(r) || !nextStatus ||
        !this.nextStatuses(r).includes(nextStatus) || this.busy()) return;

    const diagnosis = this.reportDiagnosis.trim();
    const workPerformed = this.reportWork.trim();
    if (nextStatus === 'COMPLETED') {
      if (r.assignedEmployeeId === null) {
        this.error.set('Assign a technician before completing the repair.');
        return;
      }
      if (!diagnosis || !workPerformed) {
        this.error.set('Fill in both Technical diagnosis and Work performed before completing the repair.');
        return;
      }
    }

    this.busy.set(true); this.error.set(''); this.message.set('');
    const statusRequest = () => this.api.post<Repair>(`/companies/${companyId}/repairs/${r.id}/status`, {
      status: nextStatus, note: this.statusNote.trim(), customerNote: this.customerNote.trim()
    });
    const reportChanged = diagnosis !== (r.diagnosis ?? '') ||
      workPerformed !== (r.workPerformed ?? '');
    // The server requires the technical report BEFORE a repair can become COMPLETED.
    // Save edited report fields first, then submit the status change in sequence.
    const request = nextStatus === 'COMPLETED' && reportChanged
      ? this.api.put<Repair>(`/companies/${companyId}/repairs/${r.id}/report`, {
          diagnosis, workPerformed
        }).pipe(switchMap(() => statusRequest()))
      : statusRequest();
    request.subscribe({
      next: value => { this.busy.set(false); this.updateAfterChange(value, 'Repair status updated.'); },
      error: err => this.fail(err, 'Could not change repair status.')
    });
  }
  publishProgress(): void {
    const r = this.selected(), companyId = this.ctx.managedSelected();
    if (!r || !companyId || !this.canEdit(r) || !this.isOpen(r) ||
        !this.progressMessage.trim() || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.api.post<Repair>(`/companies/${companyId}/repairs/${r.id}/progress`, {
      customerNote: this.progressMessage.trim()
    }).subscribe({
      next: value => { this.busy.set(false); this.updateAfterChange(value, 'Customer update published.'); },
      error: err => this.fail(err, 'Could not publish the customer update.')
    });
  }

  validPart(): boolean {
    const item = this.stock().find(i => i.id === this.partItemId);
    return !!item && item.active && this.availableStock().some(candidate => candidate.id === item.id) &&
      (!this.selected() || this.partFit(item, this.selected()!) !== 'unspecified' || this.confirmUnverified) && Number.isInteger(this.partQuantity) &&
      this.partQuantity > 0 && this.partQuantity <= item.stockQuantity;
  }
  addPart(): void {
    const r = this.selected(), companyId = this.ctx.managedSelected();
    if (!r || !companyId || !this.canEdit(r) || !this.partsAllowed(r.status) ||
        !this.validPart() || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.api.post<Repair>(`/companies/${companyId}/repairs/${r.id}/parts`, {
      itemId: this.partItemId, quantity: this.partQuantity, confirmUnverifiedCompatibility: this.confirmUnverified
    }).subscribe({
      next: value => { this.busy.set(false); this.updateAfterChange(value, 'Part added and inventory reduced.'); },
      error: err => this.fail(err, 'Could not add replacement part.')
    });
  }
  returnPart(part: RepairPart): void {
    const repair = this.selected(), companyId = this.ctx.managedSelected();
    if (!repair || !companyId || !this.canEdit(repair) || !this.partsAllowed(repair.status) || this.busy()) return;
    if (!window.confirm(`Return ${part.quantity} x ${part.itemName} to stock for repair #${repair.id}?`)) return;
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.api.delete<Repair>(`/companies/${companyId}/repairs/${repair.id}/parts/${part.id}`).subscribe({
      next: updated => {this.busy.set(false); this.updateAfterChange(updated,'Part returned and inventory updated.');},
      error: err => this.fail(err,'Could not return this part to inventory.')
    });
  }
  validLabor(): boolean {
    return Number.isFinite(this.laborCharge) && Number.isFinite(this.laborCost) &&
      this.laborCharge >= 0 && this.laborCost >= 0 &&
      Math.abs(Math.round(this.laborCharge * 100) - this.laborCharge * 100) < 1e-7 &&
      Math.abs(Math.round(this.laborCost * 100) - this.laborCost * 100) < 1e-7;
  }
  updateLabor(): void {
    const r = this.selected(), companyId = this.ctx.managedSelected();
    if (!r || !companyId || !this.canManage() || !this.isOpen(r) || !this.validLabor() || this.busy()) return;
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.api.put<Repair>(`/companies/${companyId}/repairs/${r.id}/labor`, {
      laborCharge: this.laborCharge, laborCost: this.laborCost
    }).subscribe({
      next: value => { this.busy.set(false); this.updateAfterChange(value, 'Labor amounts updated.'); },
      error: err => this.fail(err, 'Could not save labor amounts.')
    });
  }
}
