import { DecimalPipe } from '@angular/common';
import { Component, inject, Input, OnChanges, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../../api';
import { AuthService } from '../../../core/services/auth.service';
import { TestHello } from '../test-hello/test-hello';

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
@Component({
  imports: [DecimalPipe,FormsModule,TestHello],
  selector: 'app-whish-payment',
  styleUrl: './whish-payment.component.css',
  templateUrl: './whish-payment.component.html',
  standalone:true
})
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
