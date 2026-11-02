import { Component, inject, signal, effect } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api } from './api';
import { WhishPaymentComponent } from './whish-payment.component';
import { WhishManagerComponent } from './whish-manager.component';
import { CompanyContext } from './company-context';
import { AuthService } from './auth.service';
import { Invoice, Order, OrderProfit } from './models';

@Component({standalone:true,imports:[DecimalPipe,DatePipe,FormsModule,WhishPaymentComponent,WhishManagerComponent],template:`
<section class="page-heading"><div><h1>{{ctx.canManage()?'Orders & deliveries':'My orders & invoices'}}</h1><p class="muted">{{ctx.canManage()?'Manage orders for your company, locate deliveries and print invoices.':'View your orders, delivery locations and issued invoices.'}}</p></div><button type="button" class="secondary" (click)="load()">Refresh</button></section>
@if(error()){<p class="error" role="alert">{{error()}}</p>}
@if(message()){<p class="success" role="status">{{message()}}</p>}
@if(ctx.canManage() && currentCompanyId(); as managedId){<app-whish-manager [companyId]="managedId" />}
@if(!currentCompanyId()){<p class="muted">Select a {{ctx.canManage()?'workspace':'store'}} company first.</p>}
<div class="orders-layout"><section class="panel"><h3>{{ctx.canManage()?'Company orders':'Your orders'}}</h3>
 @if(ctx.canManage()){
  <div class="order-search-fields" role="search" aria-label="Search company orders">
   <label>Order ID <input type="search" name="orderIdFilter" inputmode="numeric" [(ngModel)]="orderIdFilter" placeholder="e.g. 42" /></label>
   <label>Client ID <input type="search" name="clientIdFilter" inputmode="numeric" [(ngModel)]="clientIdFilter" placeholder="e.g. 15" /></label>
   <button type="button" class="secondary" (click)="clearOrderFilters()" [disabled]="!orderIdFilter && !clientIdFilter">Clear</button>
  </div>
  <p class="tiny muted" aria-live="polite">Showing {{filteredOrders().length}} of {{orders().length}} orders. IDs match exactly.</p>
 }
 @for(o of filteredOrders();track o.id){<button class="repair-list-card" type="button" [class.selected]="selected()?.id===o.id" (click)="select(o)"><span><strong>{{o.orderNumber}}</strong><small>Order #{{o.id}} · Client #{{o.customerId}} · {{o.customerName}} · {{o.orderedAt | date:'mediumDate'}}</small></span><span class="chip">{{o.status}}</span></button>}
 @if(!orders().length){<p class="muted">No orders in this company.</p>}
 @if(orders().length && !filteredOrders().length){<p class="muted">No orders match those IDs.</p>}
 </section><section class="panel">
 @if(selected();as order){
  <div class="panel-heading"><div><h3>{{order.orderNumber}}</h3><p class="muted">{{order.orderedAt | date:'medium'}} · {{order.status}}</p></div></div>
  <h4>Client and delivery</h4><p><strong>{{order.customerName}}</strong> · {{order.customerEmail}} · Client #{{order.customerId}}</p>
  <p>Phone: {{order.delivery.phone || 'Not available (legacy order)'}}</p>
  <p>{{order.delivery.streetAddress}}, {{order.delivery.city}}, {{order.delivery.postalCode}}<br/>{{order.delivery.country}}</p>
  @if(order.delivery.notes){<p><strong>Instructions:</strong> {{order.delivery.notes}}</p>}
  @if(order.delivery.latitude!==null && order.delivery.longitude!==null){<p><a [href]="mapUrl(order)" target="_blank" rel="noopener noreferrer">Open delivery pin on map ↗</a> · {{order.delivery.latitude}}, {{order.delivery.longitude}}</p>}
  <h4>Items</h4><div class="table-scroll"><table><thead><tr><th>Product</th><th>Quantity</th><th>Price</th><th>Total</th></tr></thead><tbody>
  @for(line of order.items;track line.itemId){<tr><td>{{line.name}}</td><td>{{line.quantity}}</td><td>{{line.unitSellingPrice | number:'1.2-2'}}</td><td>{{line.lineTotal | number:'1.2-2'}}</td></tr>}
  </tbody></table></div><p><strong>Order subtotal: {{order.totalAmount | number:'1.2-2'}} {{order.currencyCode}}</strong></p>
@if(!ctx.canManage() && order.status!=='CANCELLED' && order.status!=='REFUNDED'){
  @if(invoiceFor(order.id);as issuedInvoice){
    @if(issuedInvoice.status==='ISSUED'){<app-whish-payment [companyId]="currentCompanyId()!" [invoiceId]="issuedInvoice.id" />}
  }@else{<app-whish-payment [companyId]="currentCompanyId()!" [orderId]="order.id" />}
}
  @if(ctx.canManage()){
   <div class="repair-action-box"><h4>Owner / manager actions</h4>
    @if(order.status==='CONFIRMED'){<button type="button" [disabled]="busy()" (click)="complete(order)">Mark fulfilled</button>}
    @if(order.status==='COMPLETED'){
     @if(profit();as p){<div class="margin-preview"><span>Recorded sales: {{p.revenue | number:'1.2-2'}} {{p.currencyCode}}</span><span>Acquisition costs: {{p.acquisitionCost | number:'1.2-2'}} {{p.currencyCode}}</span><strong>Gross profit: {{p.grossProfit | number:'1.2-2'}} {{p.currencyCode}}</strong><span class="tiny muted">Before delivery expenses, fees and taxes.</span></div>}
     @if(!invoiceFor(order.id)){
      <p class="muted tiny">After fulfillment, create a formal invoice. Set the correct tax amount for your jurisdiction.</p>
      <label>Tax amount ({{order.currencyCode}})<input type="number" min="0" step="0.01" [(ngModel)]="taxAmount" /></label>
      <button type="button" [disabled]="busy()" (click)="createInvoice(order)">Create draft invoice</button>
     }
    }
   </div>
  }
  @if(invoiceFor(order.id);as inv){<div class="repair-action-box"><h4>Invoice {{inv.invoiceNumber}} · {{inv.status}}</h4>
    <p>Total including recorded tax: <strong>{{inv.totalAmount | number:'1.2-2'}} {{inv.currencyCode}}</strong></p>
    @if(ctx.canManage() && inv.status==='DRAFT'){
      <label>Payment due date <input type="date" [(ngModel)]="dueDate" /></label>
      <button type="button" [disabled]="busy()" (click)="issueInvoice(inv)">Issue invoice</button>
    }
    @if(inv.status==='ISSUED'||inv.status==='PAID'){
      <div class="table-actions"><button type="button" (click)="printInvoice(order,inv,false)">Print customer invoice</button>
      @if(ctx.canManage()){<button type="button" class="secondary" [disabled]="!profit()" (click)="printInvoice(order,inv,true)">Print internal profit copy</button>}</div>
    }@else{<p class="muted tiny">Customer printing is available once the invoice has been issued.</p>}
  </div>}
  @else{<p class="muted tiny">No issued invoice for this order yet.</p>}
 }@else{<p class="muted">Select an order to see its delivery and invoice details.</p>}
 </section></div>`,styles:[` .order-search-fields{display:flex;gap:.7rem;align-items:end;flex-wrap:wrap;margin:.75rem 0} .order-search-fields label{flex:1 1 130px;min-width:0} .order-search-fields input{width:100%} @media(max-width:600px){.order-search-fields{display:grid;grid-template-columns:1fr 1fr}.order-search-fields button{grid-column:1/-1}} `]})
export class OrdersPage {
 private api=inject(Api);ctx=inject(CompanyContext);auth=inject(AuthService);
 orders=signal<Order[]>([]);invoices=signal<Invoice[]>([]);selected=signal<Order|null>(null);
 profit=signal<OrderProfit|null>(null);error=signal('');message=signal('');busy=signal(false);
 orderIdFilter='';clientIdFilter='';
 taxAmount=0;dueDate=new Date(Date.now()+14*86400000).toISOString().slice(0,10);
 currentCompanyId=()=>this.ctx.canManage()?this.ctx.managedSelected():this.ctx.selected();
 constructor(){effect(()=>{const id=this.currentCompanyId();const authenticated=this.auth.signedIn();if(id&&authenticated)this.load();});}
 load(){const id=this.currentCompanyId();if(!id)return;
  const manager=this.ctx.canManage();this.error.set('');this.selected.set(null);this.profit.set(null);this.orders.set([]);this.invoices.set([]);
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
  const body=`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${e(inv.invoiceNumber)}</title><style>body{font:14px Arial,sans-serif;color:#17283b;margin:35px}h1{font-size:25px}table{width:100%;border-collapse:collapse;margin:20px 0}td,th{padding:10px;text-align:left;border-bottom:1px solid #ccc}th{background:#eff3f9}.muted{color:#607082}section{margin-top:30px}@media print{body{margin:10mm}}</style></head><body><h1>INVOICE</h1><h2>${e(seller)}</h2><p>${e(inv.sellerAddress)}<br>${e(inv.sellerEmail)}<br>${e(inv.sellerPhone)}</p><p>Invoice: ${e(inv.invoiceNumber)} · ${e(inv.status)}<br>Issued: ${e(inv.issuedAt??'')}<br>Order: ${e(order.orderNumber)}</p><section><strong>Bill to / Deliver to</strong><p>${e(order.customerName)}<br>${e(order.customerEmail)}<br>${e(order.delivery.phone)}<br>${address}</p></section><table><thead><tr><th>Description</th><th>Qty</th><th>Unit price</th><th>Line total</th></tr></thead><tbody>${rows}</tbody></table><p>Subtotal: ${fmt(inv.subtotal)} ${e(inv.currencyCode)}</p><p>Recorded tax: ${fmt(inv.taxAmount)} ${e(inv.currencyCode)}</p><h2>Total: ${fmt(inv.totalAmount)} ${e(inv.currencyCode)}</h2>${costs}<script>window.addEventListener('load',()=>window.print());<\/script></body></html>`;
  const w=window.open('','_blank');if(!w){this.error.set('Allow pop-ups to print invoices.');return;}
  w.opener=null;w.document.open();w.document.write(body);w.document.close();
 }
}
