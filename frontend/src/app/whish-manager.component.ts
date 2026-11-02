import { Component, Input, OnChanges, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api } from './api';
import { WhishTransfer } from './whish-payment.component';

@Component({selector:'app-whish-manager',standalone:true,imports:[FormsModule,DecimalPipe],template:`
<section class="panel" aria-label="Manual Whish wallet verification">
 <h3>Whish receiving wallet & verification</h3>
 <p class="muted tiny">Only owners/managers can change the receiving number or confirm transfers. No automated Whish API is connected.</p>
 @if(error()){<p class="error" role="alert">{{error()}}</p>}
 @if(message()){<p class="success" role="status">{{message()}}</p>}
 <label>Shop's verified Whish receiving phone
   <input type="tel" [(ngModel)]="phone" placeholder="+961xxxxxxxx" autocomplete="tel" />
 </label>
 <div class="table-actions"><button type="button" class="primary" (click)="save()" [disabled]="busy()||!phone.trim()">Save Whish number</button>
 <button type="button" class="secondary" (click)="load()">Refresh transfers</button></div>
 <p class="muted tiny">Make sure the receiving wallet belongs to the company and is permitted for business use.</p>
 <h4>Transfers to review</h4>
 @for(t of transfers();track t.id){
   <div class="repair-action-box">
    <p><strong>{{t.status}}</strong> · {{t.amount | number:'1.2-2'}} {{t.currencyCode}}</p>
    <p>Reference: <strong>{{t.reference}}</strong> · Receiver: {{t.receiverPhone}}</p>
    <p>@if(t.orderId){Order #{{t.orderId}}}@else{Invoice #{{t.invoiceId}}} · Client #{{t.customerId}} — {{t.clientAccountName}}</p>
    <p><strong>Reported Whish sender:</strong> {{t.senderName || 'Not recorded'}} · {{t.senderPhone || 'Not recorded'}}</p>
    @if(t.reviewNote){<p class="muted tiny">Review: {{t.reviewNote}}</p>}
    @if(t.status==='PENDING'){
      <p class="muted tiny">Open your Whish app. Match receiving wallet, exact amount, currency and reference with a settled incoming transfer BEFORE verifying.</p>
      <label>Reason (required for rejection)
      <input type="text" [(ngModel)]="notes[t.id]" placeholder="Why is this transfer invalid?" maxlength="500" /></label>
      <div class="table-actions"><button type="button" class="primary" [disabled]="busy()" (click)="verify(t)">I verified receipt</button>
      <button type="button" class="secondary" [disabled]="busy()||!(notes[t.id]||'').trim()" (click)="reject(t)">Reject</button></div>
    }
   </div>
 }@empty{<p class="muted">No Whish submissions for this company.</p>}
</section>
`})
export class WhishManagerComponent implements OnChanges {
 @Input({required:true}) companyId!:number;
 private api=inject(Api);
 phone='';notes:Record<number,string>={};busy=signal(false);
 transfers=signal<WhishTransfer[]>([]);error=signal('');message=signal('');
 ngOnChanges(){if(this.companyId)this.load();}
 load(){if(!this.companyId)return;
  this.api.get<{phone:string}>(`/companies/${this.companyId}/whish/settings`).subscribe({next:r=>this.phone=r.phone||'',error:()=>this.error.set('Cannot load wallet settings.')});
  this.api.get<WhishTransfer[]>(`/companies/${this.companyId}/whish/transfers`).subscribe({next:r=>this.transfers.set(r),error:()=>this.error.set('Cannot load transfer queue.')});
 }
 save(){if(this.busy()||!this.phone.trim())return;this.busy.set(true);
   this.api.put<{phone:string}>(`/companies/${this.companyId}/whish/settings`,{phone:this.phone}).subscribe({
    next:()=>{this.message.set('Whish receiving phone saved.');this.busy.set(false);},
    error:()=>{this.error.set('Cannot save phone. Use digits and optional +country code.');this.busy.set(false);}
   });
 }
 verify(t:WhishTransfer){if(this.busy()||!confirm(`Have you PERSONALLY verified a settled Whish transfer of ${t.amount} ${t.currencyCode} to ${t.receiverPhone}, reference ${t.reference}?`))return;
  this.busy.set(true);this.api.post<WhishTransfer>(`/companies/${this.companyId}/whish/transfers/${t.id}/verify`,{}).subscribe({
   next:v=>{this.transfers.update(xs=>xs.map(x=>x.id===v.id?v:x));this.message.set('Transfer verified and recorded for reconciliation.');this.busy.set(false);},
   error:()=>{this.error.set('Could not verify. Check invoice/order balance.');this.busy.set(false);}
  });
 }
 reject(t:WhishTransfer){const note=(this.notes[t.id]||'').trim();if(!note||this.busy())return;
  this.busy.set(true);this.api.post<WhishTransfer>(`/companies/${this.companyId}/whish/transfers/${t.id}/reject`,{note}).subscribe({
   next:v=>{this.transfers.update(xs=>xs.map(x=>x.id===v.id?v:x));this.message.set('Transfer rejected.');this.busy.set(false);},
   error:()=>{this.error.set('Could not reject transfer.');this.busy.set(false);}
  });
 }
}
