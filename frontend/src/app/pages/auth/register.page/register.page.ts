import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
@Component({
  selector: 'app-register.page',
  imports: [RouterLink,FormsModule],
  templateUrl: './register.page.html',
  styleUrl: './register.page.css',
})
export class RegisterPage {
 fullName='';email='';password='';phone='';accountType: 'OWNER'|'CLIENT'='CLIENT';error=signal('');busy=signal(false);
 private auth=inject(AuthService);private router=inject(Router);
 submit(){this.error.set('');this.busy.set(true);
  this.auth.register(this.fullName,this.email,this.password,this.phone,this.accountType).subscribe({
   next:()=>this.router.navigateByUrl('/login'),
   error:e=>{this.error.set(e?.error?.detail??'Registration failed');this.busy.set(false);}});
 }
}
