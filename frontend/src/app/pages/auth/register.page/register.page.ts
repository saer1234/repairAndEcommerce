import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { StoreLanguageService, StoreLanguage } from '../../../core/services/store-language.service';
import { Router, RouterLink } from '@angular/router';
import { FormsModule, NgForm } from '@angular/forms';
@Component({
  selector: 'app-register.page',
  imports: [RouterLink,FormsModule],
  templateUrl: './register.page.html',
  styleUrl: './register.page.css',
})
export class RegisterPage {
  readonly lang = inject(StoreLanguageService);
  readonly t = (key: string): string => this.lang.t(key);
  setLanguage(language: StoreLanguage): void { this.lang.setLanguage(language); }

 fieldErrors=signal<Record<string,string>>({});submitted=false;
 fullName='';email='';password='';confirmPassword='';phone='';accountType: 'OWNER'|'CLIENT'='CLIENT';error=signal('');busy=signal(false);
 private auth=inject(AuthService);private router=inject(Router);
 submit(form:NgForm){this.submitted=true;this.error.set('');this.fieldErrors.set({});
   if(form.invalid){this.error.set(this.t('Please correct the highlighted fields.'));return;}
   if(this.password!==this.confirmPassword){this.error.set(this.t('Passwords do not match. Please re-enter them.'));return;}
   this.busy.set(true);
  this.auth.register(this.fullName,this.email,this.password,this.phone,this.accountType).subscribe({
   next:()=>this.router.navigateByUrl('/login'),
   error:e=>{
     const fields=e?.error?.fieldErrors;
     if(fields&&typeof fields==='object')this.fieldErrors.set(fields);
     const msg=e?.error?.message||e?.error?.detail||this.t('Registration failed. Please check your details.');
     this.error.set(this.t(msg));
     this.busy.set(false);
   }});
 }
}
