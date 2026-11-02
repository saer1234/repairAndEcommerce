import { Component, Input, OnChanges, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api } from './api';
import { AuthService } from './auth.service';

export interface WhishTransfer {
  id:number; orderId:number|null; invoiceId:number|null; customerId:number;
  clientAccountName:string; senderName:string|null; senderPhone:string|null;
  reference:string; amount:number; currencyCode:string; receiverPhone:string;
  status:'PENDING'|'VERIFIED'|'REJECTED'; submittedAt:string;
  reviewedAt:string|null; reviewNote:string|null; recordedOnInvoice:boolean;
}
export interface WhishInstructions {
  receiverPhone:string; amount:number; currencyCode:string;
  orderNumber:string|null; invoiceNumber:string|null;
}

/** Client-only component. No transfer happens within the application. */
@Component({selector:'app-whish-payment',standalone:true,imports:[DecimalPipe,FormsModule],template:`
<section class="panel" aria-label="Whish wallet transfer">
  <h4>Pay by Whish-to-Whish transfer</h4>
  <p class="muted tiny">Send money in your Whish app. This website never accesses your wallet or sends funds.</p>
  @if(error()){<p class="error" role="alert">{{error()}}</p>}
  @if(message()){<p class="success" role="status">{{message()}}</p>}
  @if(instructions();as w){
    <div class="repair-action-box">
      <p>Pay <strong>{{w.amount | number:'1.2-2'}} {{w.currencyCode}}</strong> to this shop's Whish number:</p>
      <p><strong>{{w.receiverPhone}}</strong></p>
      <button type="button" class="secondary" (click)="copy(w.receiverPhone)">Copy receiving number</button>
      <p class="tiny muted">Check the recipient's identity inside Whish before transferring. Use the exact amount and currency shown. Do not send a second transfer for a pending reference.</p>
      @if(!hasUnresolvedTransfer()){
        <p class="tiny muted">Account: <strong>{{auth.user()?.fullName}}</strong> · Client #{{auth.user()?.id}}</p>
        <label>Sender name (as shown in Whish)
          <input type="text" name="whishSenderName" [(ngModel)]="senderName" minlength="2" maxlength="150" autocomplete="name" placeholder="Name on sending wallet" />
        </label>
        <label>Sender Whish phone number
          <input type="tel" name="whishSenderPhone" [(ngModel)]="senderPhone" minlength="6" maxlength="40" autocomplete="tel" placeholder="+961xxxxxxxx" />
        </label>
        <label>Transfer reference shown by Whish
          <input type="text" [(ngModel)]="reference" maxlength="120" minlength="5" placeholder="Whish transaction reference" autocomplete="off" />
        </label>
        <button type="button" class="primary" [disabled]="busy()||!validSender()||reference.trim().length<5" (click)="submit()">Submit transfer for verification</button>
      }
    </div>
  }@else{
    <p class="muted tiny">Payment instructions are only available for an unpaid order or issued invoice with a configured Whish wallet.</p>
  }
  <h5>Transfer verification status</h5>
  @for(t of transfers();track t.id){
    <p><strong>{{t.reference}}</strong> — {{t.amount | number:'1.2-2'}} {{t.currencyCode}}
      <strong>{{t.status}}</strong>
      <span class="tiny muted">· Sender: {{t.senderName || 'Not recorded'}} · {{t.senderPhone || 'No phone recorded'}}</span>
      @if(t.recordedOnInvoice){<span class="muted tiny">· Recorded on invoice</span>}
      @if(t.reviewNote){<span class="muted tiny">· {{t.reviewNote}}</span>}
    </p>
  }@empty{<p class="muted tiny">No transfer reference submitted yet.</p>}
  <button type="button" class="secondary" [disabled]="busy()" (click)="load()">Refresh payment status</button>
</section>
`})
export class WhishPaymentComponent implements OnChanges {
  @Input({required:true}) companyId!:number;
  @Input() orderId:number|null=null;
  @Input() invoiceId:number|null=null;
  private api=inject(Api);auth=inject(AuthService);
  instructions=signal<WhishInstructions|null>(null);
  transfers=signal<WhishTransfer[]>([]);
  error=signal('');message=signal('');busy=signal(false);reference='';senderName='';senderPhone='';
  ngOnChanges(){this.reference='';this.senderName=this.auth.user()?.fullName??'';this.senderPhone=this.auth.user()?.phone??'';this.instructions.set(null);this.transfers.set([]);if(this.companyId)this.load();}
  private base(){
    if(this.invoiceId)return `/companies/${this.companyId}/invoices/${this.invoiceId}/whish`;
    if(this.orderId)return `/companies/${this.companyId}/orders/${this.orderId}/whish`;
    return null;
  }
  validSender(){return this.senderName.trim().length>=2 && this.senderName.trim().length<=150 && /^[+0-9() .-]{6,40}$/.test(this.senderPhone.trim());}
  hasUnresolvedTransfer(){return this.transfers().some(t=>t.status!=='REJECTED');}
  load(){const path=this.base();if(!path)return;
    this.api.get<WhishTransfer[]>(`${path}/transfers`).subscribe({next:t=>this.transfers.set(t),error:()=>this.error.set('Cannot load payment status.')});
    this.api.get<WhishInstructions>(`${path}/instructions`).subscribe({next:v=>{this.instructions.set(v);this.error.set('');},error:()=>this.instructions.set(null)});
  }
  submit(){const path=this.base();if(!path||this.busy()||this.hasUnresolvedTransfer())return;
    const ref=this.reference.trim();if(ref.length<5||!this.validSender()){this.error.set('Enter sender name, phone and transaction reference.');return;}
    this.busy.set(true);this.error.set('');
    this.api.post<WhishTransfer>(`${path}/transfers`,{reference:ref,senderName:this.senderName.trim(),senderPhone:this.senderPhone.trim()}).subscribe({
      next:v=>{this.transfers.update(x=>[v,...x]);this.reference='';this.message.set('Transfer submitted. NOT YET PAID — the shop must verify receipt in Whish.');this.busy.set(false);},
      error:()=>{this.error.set('Submission rejected. Check the reference, invoice status or previous submissions.');this.busy.set(false);}
    });
  }
  copy(s:string){if(navigator.clipboard)void navigator.clipboard.writeText(s);}
}
