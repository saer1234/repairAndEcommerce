import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
@Component({standalone:true,imports:[FormsModule,RouterLink],template:`
<main class="auth-layout"><section class="auth-card"><div class="logo">🔧 RepairFlow</div><h1>Create account</h1>
<form (ngSubmit)="submit()">
<label>Full name <input name="fullName" [(ngModel)]="fullName" required maxlength="150" /></label>
<label>Email <input name="email" [(ngModel)]="email" type="email" required /></label>
<label>Password <input name="password" [(ngModel)]="password" type="password" autocomplete="new-password" minlength="8" required /></label>
<label>Register as
  <select name="accountType" [(ngModel)]="accountType" required>
    <option value="CLIENT">Client — shop and track repairs</option>
    <!--<option value="OWNER">Owner — register and manage a company</option>-->
  </select>
</label>
<label>Phone (optional) <input name="phone" [(ngModel)]="phone" /></label>
@if(error()){<p class="error" role="alert">{{error()}}</p>}
<button class="primary" [disabled]="busy()">{{busy()?'Registering…':'Create account'}}</button></form>
<p class="muted">Already registered? <a routerLink="/login">Sign in</a></p></section></main>`})
export class RegisterPage {
 fullName='';email='';password='';phone='';accountType: 'OWNER'|'CLIENT'='CLIENT';error=signal('');busy=signal(false);
 private auth=inject(AuthService);private router=inject(Router);
 submit(){this.error.set('');this.busy.set(true);
  this.auth.register(this.fullName,this.email,this.password,this.phone,this.accountType).subscribe({
   next:()=>this.router.navigateByUrl('/login'),
   error:e=>{this.error.set(e?.error?.detail??'Registration failed');this.busy.set(false);}});
 }
}
