import { Component, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../api';
import { AuthService } from "../../core/services/auth.service";
import { CompanyContext } from  "../../core/services/company-context";
import { CompanyMember, Company } from "../../core/models/model";
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@Component({
  imports: [FormsModule, ReactiveFormsModule],
  selector: 'app-companies.page',
  styleUrl: './companies.page.css',
  templateUrl: './companies.page.html',
})
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
