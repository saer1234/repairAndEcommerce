import { DecimalPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WhishManagerComponent } from '../../shared/components/whish-manager.component/whish-manager.component';
import { WhishPaymentComponent } from '../../shared/components/whish-payment.component/whish-payment.component';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { Order, Invoice, OrderProfit, DeliverySettings } from '../../core/models/model';

@Component({
  imports: [DecimalPipe,DatePipe,FormsModule,WhishPaymentComponent,WhishManagerComponent],
  selector: 'app-orders.page',
  styleUrl: './orders.page.css',
  templateUrl: './orders.page.html',
})
export class OrdersPage {
  private api=inject(Api);ctx=inject(CompanyContext);auth=inject(AuthService);
 orders=signal<Order[]>([]);invoices=signal<Invoice[]>([]);selected=signal<Order|null>(null);
 profit=signal<OrderProfit|null>(null);error=signal('');message=signal('');busy=signal(false);
 orderIdFilter='';clientIdFilter='';
 deliveryEnabled=false;deliveryFee=0;deliveryCurrency='';deliverySettingsBusy=signal(false);
 loadDeliverySettings(){const id=this.currentCompanyId();if(!id||!this.ctx.canManage())return;
  this.api.get<DeliverySettings>(`/catalog/companies/${id}/delivery-settings`).subscribe({next:v=>{
    this.deliveryEnabled=v.deliveryEnabled;this.deliveryFee=Number(v.deliveryFee);this.deliveryCurrency=v.currencyCode;
  },error:()=>this.error.set('Unable to load delivery settings.')});
 }
 saveDeliverySettings(){const id=this.currentCompanyId();if(!id||!this.ctx.canManage()||this.deliverySettingsBusy())return;
  if(!Number.isFinite(this.deliveryFee)||this.deliveryFee<0||this.deliveryFee>999999){this.error.set('Delivery fee must be a non-negative amount.');return;}
  this.deliverySettingsBusy.set(true);
  this.api.put<DeliverySettings>(`/companies/${id}/delivery-settings`,{deliveryEnabled:this.deliveryEnabled,deliveryFee:this.deliveryFee})
    .subscribe({next:v=>{this.deliveryEnabled=v.deliveryEnabled;this.deliveryFee=Number(v.deliveryFee);
       this.deliveryCurrency=v.currencyCode;this.message.set('Delivery settings saved.');this.deliverySettingsBusy.set(false);},
      error:e=>{this.error.set(e?.error?.message??'Could not save delivery settings.');this.deliverySettingsBusy.set(false);}});
 }
 taxAmount=0;dueDate=new Date(Date.now()+14*86400000).toISOString().slice(0,10);
 currentCompanyId=()=>this.ctx.canManage()?this.ctx.managedSelected():this.ctx.selected();
 constructor(){effect(()=>{const id=this.currentCompanyId();const authenticated=this.auth.signedIn();if(id&&authenticated)this.load();});}
 load(){const id=this.currentCompanyId();if(!id)return;
  const manager=this.ctx.canManage();if(manager)this.loadDeliverySettings();this.error.set('');this.selected.set(null);this.profit.set(null);this.orders.set([]);this.invoices.set([]);
  this.api.get<Order[]>(`/companies/${id}/orders${manager?'':'/mine'}`).subscribe({next:arr=>this.orders.set(arr),error:()=>this.error.set('Could not load orders.')});
  this.api.get<Invoice[]>(`/companies/${id}/invoices${manager?'':'/mine'}`).subscribe({next:arr=>this.invoices.set(arr),error:()=>this.error.set('Could not load invoices.')});
 }

 filteredOrders():Order[]{
  if(!this.ctx.canManage())return this.orders();
  const orderId=this.orderIdFilter.trim(),clientId=this.clientIdFilter.trim();
  return this.orders().filter(o=>(!orderId||String(o.id)===orderId)&&(!clientId||String(o.customerId)===clientId));
 }
 clearOrderFilters(){this.orderIdFilter='';this.clientIdFilter='';}
 select(o:Order){this.selected.set(o);this.message.set('');this.profit.set(null);
  const id=this.currentCompanyId();if(id&&this.ctx.canManage())this.api.get<OrderProfit>(`/companies/${id}/orders/${o.id}/profit`).subscribe({next:v=>this.profit.set(v),error:()=>this.error.set('Unable to read internal order costs.')});
 }
 invoiceFor(id:number){return this.invoices().find(i=>i.orderId===id&&i.status!=='VOID')??null;}
 mapUrl(o:Order){const loc=o.delivery;return `https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=16/${loc.latitude}/${loc.longitude}`;}
 private refreshSelected(id:number){const company=this.currentCompanyId();if(!company)return;
  this.api.get<Order>(`/companies/${company}/orders/${id}`).subscribe({next:o=>{this.selected.set(o);this.orders.update(arr=>arr.map(x=>x.id===id?o:x));},error:()=>this.error.set('Unable to update order details.')});
  this.api.get<Invoice[]>(`/companies/${company}/invoices`).subscribe({next:list=>this.invoices.set(list)});
 }
 complete(o:Order){const id=this.currentCompanyId();if(!id||!this.ctx.canManage()||this.busy())return;
  this.busy.set(true);this.api.post<Order>(`/companies/${id}/orders/${o.id}/complete`,{}).subscribe({next:v=>{this.selected.set(v);this.orders.update(arr=>arr.map(x=>x.id===o.id?v:x));this.message.set('Order marked fulfilled; invoice can now be created.');this.busy.set(false);},error:()=>{this.error.set('Unable to complete order.');this.busy.set(false);}});
 }
 createInvoice(o:Order){const id=this.currentCompanyId();if(!id||!this.ctx.canManage()||this.busy())return;
  if(!Number.isFinite(this.taxAmount)||this.taxAmount<0){this.error.set('Tax must be a nonnegative amount.');return;}
  this.busy.set(true);this.api.post<Invoice>(`/companies/${id}/invoices`,{orderId:o.id,repairId:null,taxAmount:this.taxAmount}).subscribe({next:inv=>{this.invoices.update(list=>[inv,...list]);this.message.set('Draft invoice created. Review and issue before printing for the customer.');this.busy.set(false);},error:()=>{this.error.set('Could not create invoice (check the order is completed).');this.busy.set(false);}});
 }
 issueInvoice(inv:Invoice){const id=this.currentCompanyId();if(!id||!this.ctx.canManage()||this.busy())return;
  const due=new Date(`${this.dueDate}T23:59:00`);
  if(!Number.isFinite(due.getTime())||due.getTime()<=Date.now()){this.error.set('Due date must be in the future.');return;}
  this.busy.set(true);this.api.post<Invoice>(`/companies/${id}/invoices/${inv.id}/issue`,{dueAt:due.toISOString().slice(0,19)}).subscribe({next:i=>{this.invoices.update(list=>list.map(x=>x.id===i.id?i:x));this.message.set('Invoice issued and ready to print.');this.busy.set(false);},error:()=>{this.error.set('Unable to issue invoice.');this.busy.set(false);}});
 }
 // Escape all untrusted data before writing into printable HTML; print opens only upon user click.
 private esc(v:unknown){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]??c));}
 printInvoice(order:Order,inv:Invoice,internal:boolean){if(!['ISSUED','PAID'].includes(inv.status))return;
  const p=internal&&this.ctx.canManage()?this.profit():null;if(internal&&!p)return;
  const e=(v:unknown)=>this.esc(v);const fmt=(n:number)=>Number(n).toFixed(2);
  const seller=inv.sellerName||'Repair company';
  const rows=inv.lines.map(l=>`<tr><td>${e(l.description)}</td><td>${e(l.quantity)}</td><td>${fmt(l.unitPrice)}</td><td>${fmt(l.lineTotal)}</td></tr>`).join('');
  const costs=p?`<section style="page-break-before:always"><h2>INTERNAL ONLY — COST & GROSS PROFIT</h2><p>Not for customers; contains confidential supplier prices.</p><table><thead><tr><th>Item</th><th>Qty</th><th>Unit cost</th><th>Cost total</th><th>Gross profit</th></tr></thead><tbody>${p.lines.map(l=>`<tr><td>${e(l.name)}</td><td>${l.quantity}</td><td>${fmt(l.unitCostPrice)}</td><td>${fmt(l.lineCost)}</td><td>${fmt(l.lineGrossProfit)}</td></tr>`).join('')}</tbody></table><h3>Revenue ${fmt(p.revenue)} ${e(p.currencyCode)}</h3><h3>Acquisition cost ${fmt(p.acquisitionCost)} ${e(p.currencyCode)}</h3><h2>Gross profit ${fmt(p.grossProfit)} ${e(p.currencyCode)}</h2><p>Excludes delivery cost, operating expenses, payment fees, and taxes.</p></section>`:'';
  const address=[order.delivery.streetAddress,order.delivery.city,order.delivery.postalCode,order.delivery.country].filter(Boolean).map(e).join(', ');
  const body=`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${e(inv.invoiceNumber)}</title><style>body{font:14px Arial,sans-serif;color:#17283b;margin:35px}h1{font-size:25px}table{width:100%;border-collapse:collapse;margin:20px 0}td,th{padding:10px;text-align:left;border-bottom:1px solid #ccc}th{background:#eff3f9}.muted{color:#607082}section{margin-top:30px}@media print{body{margin:10mm}}</style></head><body><h1>INVOICE</h1><h2>${e(seller)}</h2><p>${e(inv.sellerAddress)}<br>${e(inv.sellerEmail)}<br>${e(inv.sellerPhone)}</p><p>Invoice: ${e(inv.invoiceNumber)} · ${e(inv.status)}<br>Issued: ${e(inv.issuedAt??'')}<br>Order: ${e(order.orderNumber)}</p><section><strong>Bill to / ${order.fulfillmentMethod==='PICKUP'?'Store pickup':'Deliver to'}</strong><p>${e(order.customerName)}<br>${e(order.customerEmail)}<br>${e(order.delivery.phone)}<br>${address}</p></section><table><thead><tr><th>Description</th><th>Qty</th><th>Unit price</th><th>Line total</th></tr></thead><tbody>${rows}</tbody></table><p>Subtotal: ${fmt(inv.subtotal)} ${e(inv.currencyCode)}</p><p>Recorded tax: ${fmt(inv.taxAmount)} ${e(inv.currencyCode)}</p><h2>Total: ${fmt(inv.totalAmount)} ${e(inv.currencyCode)}</h2>${costs}<script>window.addEventListener('load',()=>window.print());<\/script></body></html>`;
  const w=window.open('','_blank');if(!w){this.error.set('Allow pop-ups to print invoices.');return;}
  w.opener=null;w.document.open();w.document.write(body);w.document.close();
 }
}
