import { Component, inject, signal, effect, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Api } from './api';
import { AuthService } from './auth.service';
import { CompanyContext } from './company-context';
import { Company, Finance, Inventory, Order, PlatformFinance, Repair } from './models';
import { DecimalPipe } from '@angular/common';
@Component({standalone:true,imports:[DecimalPipe],template:`
<section class="page-heading"><div><h1>Dashboard</h1><p class="muted">Overview of your workspace</p></div>
<button (click)="load()" class="secondary">Refresh</button></section>
@if(error()){<p class="error" role="alert">{{error()}}</p>}
@if(loading()){<p class="muted">Loading dashboard…</p>}
@if(auth.user()?.platformRole==='SUPER_ADMIN'){
  <h3>Platform owner — fee revenue and operating expenses</h3>
  <div class="metrics">
    @for(x of platform();track x.currencyCode){
      <div class="metric"><small>Earned fees · {{x.currencyCode}}</small><strong>{{x.earnedFees | number:'1.2-2'}}</strong></div>
      <div class="metric"><small>Expenses · {{x.currencyCode}}</small><strong>{{x.expenses | number:'1.2-2'}}</strong></div>
      <div class="metric"><small>Estimated result · {{x.currencyCode}}</small><strong>{{x.estimatedOperatingResultBeforeTax | number:'1.2-2'}}</strong></div>
    }
  </div>
  @if(!platform().length && !loading()){<p class="muted">No platform fees or expenses recorded yet.</p>}
}
@if(selectedRole()==='OWNER'||selectedRole()==='MANAGER'){
 <h3>Company manager · {{ctx.managedCompany()?.name}}</h3>
 @if(finance(); as f){
 <div class="metrics">
  <div class="metric"><small>Completed sales</small><strong>{{f.completedSalesRevenue | number:'1.2-2'}} {{f.currencyCode}}</strong></div>
  <div class="metric"><small>Completed repairs</small><strong>{{f.completedRepairRevenue | number:'1.2-2'}} {{f.currencyCode}}</strong></div>
  <div class="metric"><small>Gross profit</small><strong>{{f.grossProfit | number:'1.2-2'}} {{f.currencyCode}}</strong></div>
  <div class="metric"><small>Estimated operating result</small><strong>{{f.estimatedOperatingResultBeforeTax | number:'1.2-2'}} {{f.currencyCode}}</strong></div>
  <div class="metric"><small>Collected (including tax)</small><strong>{{f.cashCollectedIncludingTax | number:'1.2-2'}} {{f.currencyCode}}</strong></div>
  <div class="metric"><small>Unpaid issued invoices</small><strong>{{f.unpaidIssuedInvoices | number:'1.2-2'}} {{f.currencyCode}}</strong></div>
 </div>}
 @if(inventory();as i){<div class="panel"><h3>Inventory</h3><p>{{i.totalSkus}} SKUs · {{i.unitsOnHand}} units</p><p>Inventory cost: {{i.inventoryCostValue | number:'1.2-2'}} · Potential gross profit: {{i.potentialGrossProfit | number:'1.2-2'}}</p></div>}
}
@if(selectedRole()==='EMPLOYEE'){
 <h3>Employee workspace</h3>
 <div class="metrics"><div class="metric"><small>Repair requests</small><strong>{{repairs().length}}</strong></div><div class="metric"><small>Stock records</small><strong>{{stockCount()}}</strong></div></div>
}
@if(!selectedRole() && auth.canShop()){
 <h3>Customer workspace</h3><div class="metrics"><div class="metric"><small>My orders</small><strong>{{orders().length}}</strong></div><div class="metric"><small>My repairs</small><strong>{{repairs().length}}</strong></div></div>
}
@if(repairs().length){<div class="panel"><h3>Recent repairs</h3><table><thead><tr><th>Device</th><th>Status</th><th>Request</th></tr></thead><tbody>
 @for(r of repairs().slice(0,10);track r.id){<tr><td>{{r.brand}} {{r.model}}</td><td><span class="chip">{{r.status}}</span></td><td>#{{r.id}}</td></tr>}
 </tbody></table></div>}
@if(orders().length){<div class="panel"><h3>Recent orders</h3><table><thead><tr><th>Order</th><th>Status</th><th>Total</th></tr></thead><tbody>
 @for(o of orders().slice(0,10);track o.id){<tr><td>{{o.orderNumber}}</td><td><span class="chip">{{o.status}}</span></td><td>{{o.totalAmount | number:'1.2-2'}} {{o.currencyCode}}</td></tr>}
 </tbody></table></div>}
`})
export class DashboardPage implements OnInit {
 auth=inject(AuthService);ctx=inject(CompanyContext);private api=inject(Api);
 error=signal('');loading=signal(false);finance=signal<Finance|null>(null);
 inventory=signal<Inventory|null>(null);platform=signal<PlatformFinance[]>([]);
 repairs=signal<Repair[]>([]);orders=signal<Order[]>([]);stockCount=signal(0);
 selectedRole=()=>this.ctx.role();
 constructor(){effect(()=>{const workspace=this.ctx.managedSelected(),store=this.ctx.selected();if(workspace || store) this.load();});}
 ngOnInit(){if(this.auth.user()?.platformRole==='SUPER_ADMIN')this.load();}
 load(){const role=this.selectedRole(), id=role ? this.ctx.managedSelected() : this.ctx.selected();this.error.set('');this.finance.set(null);this.inventory.set(null);this.repairs.set([]);this.orders.set([]);
  this.loading.set(true);
  if (this.auth.user()?.platformRole==='SUPER_ADMIN'){
   this.api.get<PlatformFinance[]>('/platform/finance/overview').subscribe({next:x=>{this.platform.set(x);this.loading.set(false);},error:()=>this.fail()});
   return;
  }
  if (!id){this.loading.set(false);return;}
  const prefix=`/companies/${id}`;
  if(role==='OWNER'||role==='MANAGER'){
   forkJoin({f:this.api.get<Finance>(prefix+'/finance/overview'),i:this.api.get<Inventory>(prefix+'/inventory/summary'),r:this.api.get<Repair[]>(prefix+'/repairs'),o:this.api.get<Order[]>(prefix+'/orders')})
    .subscribe({next:v=>{this.finance.set(v.f);this.inventory.set(v.i);this.repairs.set(v.r);this.orders.set(v.o);this.loading.set(false);},error:()=>this.fail()});
  }else if(role==='EMPLOYEE'){
   forkJoin({r:this.api.get<Repair[]>(prefix+'/repairs'),s:this.api.get<unknown[]>(prefix+'/inventory/stock')})
    .subscribe({next:v=>{this.repairs.set(v.r);this.stockCount.set(v.s.length);this.loading.set(false);},error:()=>this.fail()});
  }else if(this.auth.canShop()){
   forkJoin({r:this.api.get<Repair[]>(prefix+'/repairs/mine'),o:this.api.get<Order[]>(prefix+'/orders/mine')})
    .subscribe({next:v=>{this.repairs.set(v.r);this.orders.set(v.o);this.loading.set(false);},error:()=>this.fail()});
  }else{
    this.loading.set(false);
  }
 }
 private fail(){this.error.set('Dashboard failed to load. Verify permissions and server availability.');this.loading.set(false);}
}
