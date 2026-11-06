import { Component, inject, signal , } from '@angular/core';

import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  standalone:true,
  selector: 'app-login.page',
  imports: [RouterLink,FormsModule],
  templateUrl: './login.page.html',
  styleUrl: './login.page.css',
})
export class LoginPage {
  email='';password='';busy=signal(false);error=signal('');
  private auth=inject(AuthService);private router=inject(Router);
  submit(){this.error.set('');this.busy.set(true);
    this.auth.login(this.email,this.password).
    subscribe({
      next:(value)=>{
        if(value.user.platformRole==='CLIENT')
          this.router.navigateByUrl('/shop');
        else
        this.router.navigateByUrl('/dashboard')

      },
      error:()=>{
        this.error.set('Invalid credentials or unavailable service');
        this.busy.set(false);}});
  }
}
