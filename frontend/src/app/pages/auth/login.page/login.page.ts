import { Component, inject, signal , } from '@angular/core';

import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { StoreLanguageService, StoreLanguage } from '../../../core/services/store-language.service';

@Component({
  standalone:true,
  selector: 'app-login.page',
  imports: [RouterLink,FormsModule],
  templateUrl: './login.page.html',
  styleUrl: './login.page.css',
})
export class LoginPage {
  readonly lang = inject(StoreLanguageService);
  readonly t = (key: string): string => this.lang.t(key);
  setLanguage(language: StoreLanguage): void { this.lang.setLanguage(language); }

  email='';password='';busy=signal(false);error=signal('');
  private auth=inject(AuthService);private router=inject(Router);private route=inject(ActivatedRoute);
  submit(){this.error.set('');this.busy.set(true);
    this.auth.login(this.email,this.password).
    subscribe({
      next:(value)=>{
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        // Redirect back only to an explicitly allowed local shop URL.
        if (value.user.platformRole === 'CLIENT' && returnUrl &&
            /^\/shop(?:\?companyId=\d+)?$/.test(returnUrl)) {
          void this.router.navigateByUrl(returnUrl);
        } else if (value.user.platformRole === 'CLIENT') {
          void this.router.navigateByUrl('/shop');
        } else {
          void this.router.navigateByUrl('/dashboard');
        }
      },
      error:()=>{
        this.error.set(this.t('Invalid credentials or unavailable service'));
        this.busy.set(false);}});
  }
}
