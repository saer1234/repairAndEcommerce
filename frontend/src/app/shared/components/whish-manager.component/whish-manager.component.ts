import { DecimalPipe } from '@angular/common';
import { Component, inject, Input, OnChanges, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../../api';
import { WhishTransfer } from '../../../shared/components/whish-payment.component/whish-payment.component';

@Component({
  imports: [FormsModule,DecimalPipe],
  selector: 'app-whish-manager',
  styleUrl: './whish-manager.component.css',
  templateUrl: './whish-manager.component.html',
  standalone: true
})
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
