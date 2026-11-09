import { Component, effect, inject, OnInit, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from  "../../core/services/company-context";
import { Finance, Inventory, PlatformFinance, Repair, Order } from  "../../core/models/model";
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CompanyRevenueRange } from '../../core/models/model';

@Component({
  imports: [DecimalPipe,FormsModule],
  selector: 'app-dashboard.page',
  styleUrl: './dashboard.page.css',
  templateUrl: './dashboard.page.html',
})
export class DashboardPage implements OnInit{
auth=inject(AuthService);ctx=inject(CompanyContext);private api=inject(Api);
 error=signal('');loading=signal(false);finance=signal<Finance|null>(null);
 inventory=signal<Inventory|null>(null);platform=signal<PlatformFinance[]>([]);
 repairs=signal<Repair[]>([]);orders=signal<Order[]>([]);stockCount=signal(0);
 selectedRole=()=>this.ctx.role();
 private today = new Date().toISOString().slice(0,10);
 rangeFrom=this.today.slice(0,8)+'01'; rangeTo=this.today;
 range=signal<CompanyRevenueRange|null>(null); rangeError=signal(''); rangeBusy=signal(false);
 loadRevenueRange(){const id=this.ctx.managedSelected();if(!id||!this.ctx.canManage())return;
  if(!this.rangeFrom||!this.rangeTo||this.rangeFrom>this.rangeTo){this.rangeError.set('Select a valid start and end date.');return;}
  this.rangeBusy.set(true);this.rangeError.set('');
  this.api.get<CompanyRevenueRange>(`/companies/${id}/finance/date-range?from=${this.rangeFrom}&to=${this.rangeTo}`)
   .subscribe({next:v=>{this.range.set(v);this.rangeBusy.set(false);},error:e=>{this.rangeError.set(e?.error?.message??'Unable to load revenue for that range.');this.rangeBusy.set(false);}});
 }
 constructor(){effect(()=>{const workspace=this.ctx.managedSelected(),store=this.ctx.selected();if(workspace || store) this.load();});}
 ngOnInit(){if(this.auth.user()?.platformRole==='SUPER_ADMIN')this.load();}
 load(){const role=this.selectedRole(), id=role ? this.ctx.managedSelected() : this.ctx.selected();this.error.set('');this.finance.set(null);this.inventory.set(null);this.repairs.set([]);this.orders.set([]);
  this.loading.set(true);if(this.ctx.canManage())this.loadRevenueRange();
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
