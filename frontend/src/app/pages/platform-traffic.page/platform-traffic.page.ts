import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api } from '../../api';

interface IpTraffic {day:string; ip:string; requests:number; inboundBytes:number;
  knownOutboundBytes:number;lastSeenUtc:string;}
@Component({selector:'app-platform-traffic',imports:[FormsModule,DecimalPipe],
 templateUrl:'./platform-traffic.page.html',styleUrl:'./platform-traffic.page.css'})
export class PlatformTrafficPage {
  private api=inject(Api);
  date=new Date().toISOString().slice(0,10);
  loading=signal(false);error=signal('');rows=signal<IpTraffic[]>([]);
  get requests(){return this.rows().reduce((n,r)=>n+r.requests,0);}
  load(){this.loading.set(true);this.error.set('');
    this.api.get<IpTraffic[]>(`/platform/traffic/daily?date=${this.date}`).subscribe({
      next:r=>{this.rows.set(r);this.loading.set(false);},
      error:e=>{this.error.set(e?.error?.message??'Unable to load traffic statistics.');this.loading.set(false);}
    });
  }
  constructor(){this.load();}
}
