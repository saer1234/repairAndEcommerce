import { compatibilityStatus, Fit } from './device-fit';
import { matchesVerifiedFit } from './verified-fit';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { switchMap } from 'rxjs';
import { Api } from './api';
import { AuthService } from './auth.service';
import { CompanyContext } from './company-context';
import { CompanyMember, Repair, RepairClientOption, RepairPart, RepairProfitSummary, RepairStatus, StockPart } from './models';
import { RepairInvoicePanel } from './repair-invoice.component';

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
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RepairInvoicePanel],
  template: `
    <section class="page-heading">
      <div>
        <h1>Repair workspace</h1>
        <p class="muted">Company-private repair queue, assignment, status, parts and labor.</p>
      </div>
      <button class="secondary" type="button" [disabled]="loading()" (click)="refresh()">Refresh</button>
    </section>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (message()) { <p class="success" role="status">{{ message() }}</p> }
    @if (!ctx.managedSelected() || !isStaff()) {
      <div class="panel"><p>Select a company under <strong>My workspace (private)</strong>, where you are an employee, manager or owner.</p></div>
    } @else {
      <div class="repair-summary-strip">
        <div class="metric"><small>Company repairs</small><strong>{{ repairs().length }}</strong></div>
        <div class="metric"><small>Open repairs</small><strong>{{ openCount() }}</strong></div>
        <div class="metric"><small>Completed / delivered</small><strong>{{ doneCount() }}</strong></div>
        @if (canManage() && summary(); as s) {
          <div class="metric"><small>Estimated gross profit (finalized)</small><strong>{{ s.estimatedGrossProfit | number:'1.2-2' }} {{ s.currencyCode }}</strong></div>
        }
      </div>
      <section class="panel">
        <h3>Register a repair for a client</h3>
        <p class="muted tiny">Employees and managers must choose an active registered Client account. The repair belongs to that account, and its name is saved automatically.</p>
        <div class="field-pair">
          <label>Search client account
            <input type="search" name="clientSearch" [(ngModel)]="clientSearch" (ngModelChange)="onClientSearchChanged()"
                   placeholder="At least 2 characters of name or email" maxlength="80" />
          </label>
          <button class="secondary" type="button" (click)="searchClients()"
                  [disabled]="clientSearch.trim().length < 2 || clientsLoading()">{{ clientsLoading() ? 'Searching…' : 'Find client' }}</button>
        </div>
        @if (clientSearchError()) { <p class="error" role="alert">{{ clientSearchError() }}</p> }
        @if (clientResults().length) {
          <form #createRepairForm="ngForm" (ngSubmit)="createForClient()">
            <label>Registered client account *
              <select name="selectedClientId" [(ngModel)]="selectedClientId" (ngModelChange)="onClientSelected()" required>
                <option [ngValue]="null" disabled>Choose client</option>
                @for (client of clientResults(); track client.id) {
                  <option [ngValue]="client.id">{{ client.fullName }} — {{ client.email }} (account #{{ client.id }})</option>
                }
              </select>
            </label>
            <div class="field-pair">
              <label>Contact phone for this repair *
                <input name="newClientPhone" type="tel" [(ngModel)]="newClientPhone" minlength="6" maxlength="40" required />
              </label>
              <label>Device type *
                <input name="newDeviceType" [(ngModel)]="newDeviceType" maxlength="100" required placeholder="Mobile phone" />
              </label>
            </div>
            <div class="field-pair">
              <label>Brand <input name="newBrand" [(ngModel)]="newBrand" maxlength="100" /></label>
              <label>Model <input name="newModel" [(ngModel)]="newModel" maxlength="120" /></label>
            </div>
            <label>Problem description *
              <textarea name="newIssue" [(ngModel)]="newIssue" maxlength="4000" required></textarea>
            </label>
            <button class="primary" type="submit" [disabled]="createRepairForm.invalid || selectedClientId === null || creatingRepair()">
              {{ creatingRepair() ? 'Creating…' : 'Create repair for client' }}
            </button>
          </form>
        } @else if (clientSearched() && !clientsLoading() && !clientSearchError()) {
          <p class="muted">No eligible client accounts matched your search.</p>
        }
      </section>
      <div class="repair-workspace-layout">
        <section class="panel repair-queue">
          <h3>{{ ctx.managedCompany()?.name }} — Repair queue</h3>
          <div class="field-pair">
            <label>Status
              <select name="filterStatus" [(ngModel)]="filterStatus">
                <option value="ALL">All statuses</option>
                @for (status of statuses; track status) {<option [value]="status">{{ readableStatus(status) }}</option>}
              </select>
            </label>
            <label>Search
              <input name="filterQuery" [(ngModel)]="filterQuery" placeholder="ID, client name, phone, device" />
            </label>
          </div>
          @if (loading()) { <p class="muted">Loading repair queue…</p> }
          <div class="repair-list">
            @for (r of filteredRepairs(); track r.id) {
              <button type="button" class="repair-list-card" [class.selected]="selected()?.id === r.id"
                      (click)="selectRepair(r)">
                <span><strong>#{{ r.id }} — {{ deviceName(r) }}</strong>
                  <small class="muted">{{ r.createdAt | date:'mediumDate' }} · {{ r.issueDescription }}</small>
                  <small class="muted">Client: {{ r.clientName || 'Not recorded' }} · {{ r.clientPhone || 'No phone recorded' }}</small>
                  <small class="muted">Assigned: {{ employeeName(r.assignedEmployeeId) }}</small>
                </span>
                <span class="chip">{{ readableStatus(r.status) }}</span>
              </button>
            }
            @if (!loading() && !filteredRepairs().length) {
              <p class="muted">No matching repair requests.</p>
            }
          </div>
        </section>

        <section class="panel repair-detail-panel">
          @if (selected(); as r) {
            <div class="panel-heading">
              <div><h3>Repair #{{ r.id }}</h3><p class="muted">{{ deviceName(r) }} · {{ r.deviceType }}</p></div>
              <span class="chip">{{ readableStatus(r.status) }}</span>
            </div>
            <div class="repair-action-box">
              <h4>Client contact information</h4>
              <p><strong>Name:</strong> {{ r.clientName || 'Not recorded' }}</p>
              <p><strong>Phone:</strong>
                @if (r.clientPhone) {
                  <a [href]="'tel:' + r.clientPhone">{{ r.clientPhone }}</a>
                } @else { Not recorded }
              </p>
              <p class="muted tiny">Submitting account #{{ r.customerId }}</p>
            </div>
            <p><strong>Problem:</strong> {{ r.issueDescription }}</p>
            <p><strong>Assigned to:</strong> {{ employeeName(r.assignedEmployeeId) }}</p>
            <div class="repair-detail-grid">
              <div><span class="muted tiny">Parts charge</span><strong>{{ r.partsCharge | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
              <div><span class="muted tiny">Labor charge</span><strong>{{ r.laborCharge | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
              <div><span class="muted tiny">Customer total</span><strong>{{ r.totalCharge | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
              @if (canManage()) {
                <div><span class="muted tiny">Internal parts cost</span><strong>{{ (r.partsCost ?? 0) | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
                <div><span class="muted tiny">Internal labor cost</span><strong>{{ (r.laborCost ?? 0) | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
                <div><span class="muted tiny">Estimated gross profit</span><strong>{{ (r.estimatedGrossProfit ?? 0) | number:'1.2-2' }} {{ r.currencyCode }}</strong></div>
              }
            </div>

            @if (canManage() && isOpen(r)) {
              <form class="repair-action-box" (ngSubmit)="assign()">
                <h4>Assign employee</h4>
                <label>Company team member
                  <select name="employeeId" [(ngModel)]="employeeId" required>
                    <option [ngValue]="null" disabled>Choose a team member</option>
                    @for (member of members(); track member.userId) {
                      <option [ngValue]="member.userId">{{ member.fullName }} ({{ member.role }})</option>
                    }
                  </select>
                </label>
                <button class="secondary" type="submit" [disabled]="busy() || employeeId === null">Assign</button>
                @if (!members().length) {<p class="muted tiny">Add people to the company using Companies & teams first.</p>}
              </form>
            }

            @if (canEdit(r) && nextStatuses(r).length) {
              <form class="repair-action-box" (ngSubmit)="updateStatus()">
                <h4>Update repair status</h4>
                <label>Next status
                  <select name="nextStatus" [(ngModel)]="nextStatus" required>
                    @for (option of nextStatuses(r); track option) {
                      <option [ngValue]="option">{{ readableStatus(option) }}</option>
                    }
                  </select>
                </label>
                @if (nextStatus === 'COMPLETED') {
                  @if (r.assignedEmployeeId === null) {
                    <p class="error" role="alert">Assign a technician before completing this repair.</p>
                  } @else if (!reportDiagnosis.trim() || !reportWork.trim()) {
                    <p class="error" role="alert">Before completing, fill in both fields in the Technical diagnosis &amp; work report below.</p>
                  } @else {
                    <p class="muted tiny">The technical report will be saved before the repair is marked completed.</p>
                  }
                }
                <label>Internal note (employees only)
                  <textarea name="statusNote" [(ngModel)]="statusNote" maxlength="2000" placeholder="Private technical diagnosis / work details"></textarea>
                </label>
                <label>Customer-facing update (shown in tracking)
                  <textarea name="customerNote" [(ngModel)]="customerNote" maxlength="2000" placeholder="Example: We have diagnosed the issue and ordered a replacement part"></textarea>
                </label>
                <button class="primary" type="submit" [disabled]="busy() || !nextStatus || !nextStatuses(r).includes(nextStatus)">
                  {{ nextStatus === 'COMPLETED' ? 'Save report & complete repair' : 'Save status update' }}
                </button>
              </form>
            } @else if (isStaff() && !canManage()) {
              <p class="muted tiny">Only the assigned employee or a manager can update this repair. Only managers can mark it delivered.</p>
            }

            @if (canEdit(r) && isOpen(r)) {
              <form class="repair-action-box" (ngSubmit)="publishProgress()">
                <h4>Send a progress update to the customer</h4>
                <p class="muted tiny">Publish an update without changing the repair status.</p>
                <label>Progress update
                  <textarea name="progressMessage" [(ngModel)]="progressMessage" required maxlength="2000"
                            placeholder="Example: Your device is being tested"></textarea>
                </label>
                <button class="secondary" type="submit" [disabled]="busy() || !progressMessage.trim()">
                  Publish update
                </button>
              </form>
            }

            @if (canEdit(r) && (isOpen(r) || (canManage() && r.status === 'COMPLETED'))) {
              <form class="repair-action-box" (ngSubmit)="saveReport()">
                <h4>Technical diagnosis & work report</h4>
                <p class="muted tiny">This explanation and the assigned technician's name appear on the customer's final repair invoice.</p>
                <label>Why the device needed repair *
                  <textarea name="reportDiagnosis" [(ngModel)]="reportDiagnosis" required maxlength="4000" rows="3" placeholder="Diagnosis, affected component, diagnostic findings"></textarea>
                </label>
                <label>What work was performed *
                  <textarea name="reportWork" [(ngModel)]="reportWork" required maxlength="4000" rows="3" placeholder="Testing, replacement work, calibration, post-repair checks"></textarea>
                </label>
                <button type="submit" class="secondary" [disabled]="busy() || !reportDiagnosis.trim() || !reportWork.trim()">Save technical report</button>
              </form>
            }

            @if (canManage() && (r.status==='COMPLETED'||r.status==='DELIVERED')) {
              <app-repair-invoice [companyId]="r.companyId" [repairId]="r.id" [status]="r.status" [manager]="true" />
            }

            @if (canEdit(r) && partsAllowed(r.status)) {
              <form class="repair-action-box" (ngSubmit)="addPart()">
                <h4>Compatible parts in stock</h4>
                <p class="muted tiny">Repair device: <strong>{{ r.deviceType }} / {{ r.brand || 'Brand missing' }} / {{ r.model || 'Model missing' }}</strong></p>
                <div class="repair-fit-counts" aria-live="polite">
                  <span class="repair-fit-exact">{{ verifiedStockCount(r) }} exact model matches in stock</span>
                  <span class="repair-fit-check">{{ unverifiedStockCount(r) }} need fit verification</span>
                </div>
                @if (!r.brand?.trim() || !r.model?.trim()) {
                  <p class="error">This repair has no complete brand and model. Exact fit cannot be established until these device details are recorded.</p>
                }
                <div class="field-pair">
                  <label>Search stock
                    <input type="search" name="partSearch" [(ngModel)]="partSearch" (ngModelChange)="resetPartChoice()" placeholder="Part name, SKU, category" />
                  </label>
                  <label>Component category
                    <select name="partCategory" [(ngModel)]="partCategory" (ngModelChange)="resetPartChoice()">
                      <option value="ALL">All components</option>
                      @for (category of stockCategories(r); track category) { <option [value]="category">{{ category }}</option> }
                    </select>
                  </label>
                </div>
                <label>Fit level
                  <select name="partFitMode" [(ngModel)]="partFitMode" (ngModelChange)="resetPartChoice()">
                    <option value="verified">Exact recorded fit only (recommended)</option>
                    <option value="include-unverified">Include parts that need verification</option>
                  </select>
                </label>
                <label>Available stock item
                  <select name="partItemId" [(ngModel)]="partItemId" (ngModelChange)="confirmUnverified=false" required>
                    <option [ngValue]="null" disabled>Choose a stocked part</option>
                    @for (item of availableStock(); track item.id) {
                      <option [ngValue]="item.id">{{ item.name }} · {{ item.category || 'General' }} · {{ item.sku }} · Qty {{ item.stockQuantity }} · {{ partFit(item,r)==='compatible' ? 'Exact recorded fit' : 'Needs verification' }}</option>
                    }
                  </select>
                </label>
                @if (availableStock().length === 0) {
                  <p class="muted">No matching stocked parts. Try a different category or include unverified fits. If the part is missing, ask a manager to update inventory compatibility.</p>
                }
                <label>Quantity to use
                  <input type="number" name="partQuantity" [(ngModel)]="partQuantity" min="1" step="1" required />
                </label>
                @if (selectedPartFit(r)==='unspecified') {
                  <label class="repair-fit-confirm"><input type="checkbox" name="confirmUnverified" [(ngModel)]="confirmUnverified" /> I checked this part's physical compatibility with the device.</label>
                }
                <p class="muted tiny">An exact match means the compatibility was recorded by the store; it is not a manufacturer certification. Check part numbers before installation. Adding a part deducts quantity and records the technician.</p>
                <button class="secondary" type="submit" [disabled]="busy() || !validPart()">Use part from stock</button>
              </form>
            }

            @if (canManage() && isOpen(r)) {
              <form class="repair-action-box" (ngSubmit)="updateLabor()">
                <h4>Labor pricing (manager only)</h4>
                <div class="field-pair">
                  <label>Customer labor charge
                    <input type="number" name="laborCharge" [(ngModel)]="laborCharge" min="0" step="0.01" required />
                  </label>
                  <label>Internal labor cost
                    <input type="number" name="laborCost" [(ngModel)]="laborCost" min="0" step="0.01" required />
                  </label>
                </div>
                <button class="secondary" type="submit" [disabled]="busy() || !validLabor()">Save labor amounts</button>
              </form>
            }

            <div class="repair-detail-columns">
              <div>
                <h4>Used parts</h4>
                @for (p of r.parts; track p.id) {
                  <div class="repair-part-line">
                    <strong>{{ p.itemName }} × {{ p.quantity }}</strong>
                    <small>{{ p.unitSellingPrice * p.quantity | number:'1.2-2' }} {{ r.currencyCode }} billed</small>
                    @if(canEdit(r) && partsAllowed(r.status)){
                      <button class="secondary" type="button" [disabled]="busy()" (click)="returnPart(p)">Return to stock</button>
                    }
                    @if (canManage() && p.unitCostPrice !== undefined) {
                      <small class="muted">Cost: {{ p.unitCostPrice * p.quantity | number:'1.2-2' }} {{ r.currencyCode }}</small>
                    }
                  </div>
                }
                @if (!r.parts.length) { <p class="muted">No parts used yet.</p> }
              </div>
              <div>
                <h4>History</h4>
                @for (u of r.updates; track u.id) {
                  <div class="repair-timeline-entry">
                    <strong>{{ readableStatus(u.status) }}</strong>
                    <small class="muted">{{ u.createdAt | date:'medium' }} · User #{{ u.updatedByUserId }}</small>
                    @if (u.note) { <p><strong>Private:</strong> {{ u.note }}</p> }
                    @if (u.customerNote) { <p><strong>Published to client:</strong> {{ u.customerNote }}</p> }
                  </div>
                }
                @if (!r.updates.length) { <p class="muted">No status updates yet.</p> }
              </div>
            </div>
          } @else { <p class="muted">Select a repair from the queue to view and manage it.</p> }
        </section>
      </div>
    }
  `
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
