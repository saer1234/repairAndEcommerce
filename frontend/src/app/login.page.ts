import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
@Component({standalone:true,imports:[FormsModule,RouterLink],template:`
<main class="auth-layout"><section class="auth-card">
  <div class="logo">
	<div style="width:100%;display:flex;justify-content:center"><img style="width:80%;height:auto" src="assets/wolflog.jfif" alt="wolf Loginx"/></div>
  <p style="text-align:center;width:100%">Wolf Tech Repair</p></div>  <p class="muted">Sign in to your repair management workspace.</p>
  <form (ngSubmit)="submit()">
    <label>Email <input type="email" name="email" [(ngModel)]="email" autocomplete="username" required /></label>
    <label>Password <input type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required /></label>
    @if(error()){<p class="error" role="alert">{{error()}}</p>}
    <button class="primary" type="submit" [disabled]="busy()">{{busy()?'Signing in…':'Sign in'}}</button>
  </form>
  <p class="muted">New here? <a routerLink="/register">Create an account</a></p>
</section></main>`})
export class LoginPage {
  email='';password='';busy=signal(false);error=signal('');
  private auth=inject(AuthService);private router=inject(Router);
  submit(){this.error.set('');this.busy.set(true);
    this.auth.login(this.email,this.password).subscribe({next:()=>this.router.navigateByUrl('/dashboard'),
      error:()=>{this.error.set('Invalid credentials or unavailable service');this.busy.set(false);}});
  }
}
