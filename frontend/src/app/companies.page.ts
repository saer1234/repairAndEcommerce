import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api } from './api';
import { AuthService } from './auth.service';
import { CompanyContext } from './company-context';
import { Company, CompanyMember } from './models';

@Component({ standalone: true, imports: [FormsModule], template: `
<section class="page-heading"><div><h1>Companies & teams</h1>
<p class="muted">Explore any public company. Manage members only in your own company.</p></div></section>
@if(error()) {<p class="error" role="alert">{{error()}}</p>}
@if(message()) {<p class="success" role="status">{{message()}}</p>}
<div class="panel">
  <h3>All active companies</h3>
  <p class="muted">Browsing is open to every signed-in user; private management remains company-specific.</p>
  @for(c of ctx.available(); track c.id) {
    <div class="directory-line">
      <div><strong>{{c.name}}</strong><span class="muted tiny"> &nbsp; {{c.currencyCode}}</span></div>
      <div class="table-actions">
        @if(auth.canShop()) {<button type="button" class="secondary" (click)="browse(c.id)">Visit store</button>}
        @if(isMine(c.id)) {<button type="button" class="secondary" (click)="selectWorkspace(c.id)">Select my workspace</button>}
      </div>
    </div>
  }
  @if(!ctx.available().length) {<p class="muted">No active companies found.</p>}
</div>
@if(canCreateCompany()) {
<div class="panel narrow"><h3>Create company</h3>
  <form (ngSubmit)="create()">
    <label>Company name <input name="name" [(ngModel)]="name" required /></label>
    <label>Business email <input name="email" [(ngModel)]="email" type="email" /></label>
    <label>Phone <input name="phone" [(ngModel)]="phone" /></label>
    <label>Address <input name="address" [(ngModel)]="address" /></label>
    <label>Currency (ISO 4217) <input name="currencyCode" [(ngModel)]="currencyCode" required maxlength="3" /></label>
    <button class="primary" type="submit">Create company</button>
  </form>
</div>
} @else {
<div class="panel"><p class="muted">Client accounts can browse companies, shop, and request repairs. Only Owner accounts can register a new company.</p></div>
}
@if(ctx.mine().length) {
  <div class="panel narrow">
    <h3>My company workspaces</h3>
    <label>Choose the company to manage
      <select [ngModel]="ctx.managedSelected()" (ngModelChange)="selectWorkspace(+$event)" name="managedSelection">
        @for(c of ctx.mine(); track c.id) {<option [ngValue]="c.id">{{c.name}} — {{c.yourRole}}</option>}
      </select>
    </label>
  </div>
}
@if(canManage()) {
  <div class="panel narrow"><h3>Team for {{ctx.managedCompany()?.name}}</h3>
    <p class="muted">The user must register an account before you add them.</p>
    <form (ngSubmit)="addMember()"><label>User email <input name="memberEmail" [(ngModel)]="memberEmail" type="email" required /></label>
      <label>Role <select name="memberRole" [(ngModel)]="memberRole">
        <option value="EMPLOYEE">Employee</option>
        @if(ctx.role()==='OWNER'){<option value="MANAGER">Manager</option>}
      </select></label>
      <button class="primary">Add team member</button></form>
    <button class="secondary" type="button" (click)="members()">Load members</button>
    @for(m of team();track m.id) {<p>{{m.fullName}} ({{m.email}}) — {{m.role}}</p>}
  </div>
}` })
export class CompaniesPage {
  readonly ctx = inject(CompanyContext);
  private readonly api = inject(Api);
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  name = ''; email = ''; phone = ''; address = ''; currencyCode = 'EUR';
  memberEmail = ''; memberRole = 'EMPLOYEE';
  error = signal(''); message = signal(''); team = signal<CompanyMember[]>([]);
  constructor() {effect(() => {this.ctx.managedSelected(); this.team.set([]);});}
  canManage = () => this.ctx.canManage();
  canCreateCompany = () => this.auth.user()?.platformRole === 'OWNER' || this.auth.user()?.platformRole === 'SUPER_ADMIN';
  isMine(id: number): boolean {return this.ctx.mine().some(c => c.id === id);}
  browse(id: number): void {this.ctx.selected.set(id); void this.router.navigateByUrl('/shop');}
  selectWorkspace(id: number): void {
    if (!this.isMine(id)) return;
    this.ctx.managedSelected.set(id);
    this.memberRole = 'EMPLOYEE';
    this.message.set('');
  }
  create(): void {
    if (!this.canCreateCompany()) {
      this.error.set('Only Owner accounts can create companies.');
      return;
    }
    this.error.set('');
    this.api.post<Company>('/companies', {name: this.name, email: this.email, phone: this.phone,
      address: this.address, currencyCode: this.currencyCode.toUpperCase()})
      .subscribe({next: c => {
        this.ctx.managedSelected.set(c.id);
        this.ctx.selected.set(c.id);
        this.message.set('Company created');
        this.ctx.refresh();
      }, error: () => this.error.set('Unable to create company')});
  }
  addMember(): void {
    const id = this.ctx.managedSelected();
    if (!id || !this.ctx.canManage()) return;
    this.api.post(`/companies/${id}/members`, {email: this.memberEmail, role: this.memberRole})
      .subscribe({next: () => {this.message.set('Team member added'); this.members();},
        error: () => this.error.set('Unable to add member. Verify email and permissions.')});
  }
  members(): void {
    const id = this.ctx.managedSelected();
    if (!id || !this.ctx.canManage()) return;
    this.api.get<CompanyMember[]>(`/companies/${id}/members`).subscribe({
      next: list => {if (this.ctx.managedSelected() === id) this.team.set(list);},
      error: () => this.error.set('Could not load team members')
    });
  }
}
