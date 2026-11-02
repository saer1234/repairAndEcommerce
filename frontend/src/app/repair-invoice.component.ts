import { Component, Input, OnChanges, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Api } from './api';
import { WhishPaymentComponent } from './whish-payment.component';
import { RepairStatus } from './models';
import { RepairInvoiceReport, RepairInvoiceInternalReport } from './repair-invoice.models';

/** Shows the same itemized invoice to client and manager, but never sends costs to clients. */
@Component({
  selector: 'app-repair-invoice',
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe, WhishPaymentComponent],
  template: `
    <section class="repair-invoice panel" aria-label="Repair invoice">
      <div class="panel-heading"><div>
        <h3>Repair invoice & technical report</h3>
        <p class="muted tiny">Diagnosis, technician, parts, labor and the amount charged.</p>
      </div><button type="button" class="secondary" (click)="load()" [disabled]="busy()">Refresh</button></div>
      @if (error()) {<p class="error" role="alert">{{error()}}</p>}
      @if (message()) {<p class="success" role="status">{{message()}}</p>}
      @if (invoice(); as inv) {
        <p><strong>{{inv.invoiceNumber}}</strong> <span class="chip">{{inv.status}}</span></p>
        <div class="invoice-summary-grid">
          <div><span class="muted tiny">Technician</span><strong>{{inv.technicianName}}</strong></div>
          <div><span class="muted tiny">Device</span><strong>{{inv.device}}</strong></div>
          <div><span class="muted tiny">Customer</span><strong>{{inv.customerName}}</strong></div>
        </div>
        <div class="report-text"><h4>Why is this repair required?</h4><p>{{inv.diagnosis}}</p>
          <h4>What did the technician do?</h4><p>{{inv.workPerformed}}</p></div>
        <div class="table-scroll"><table class="responsive-price-table">
          <thead><tr><th>Service / part</th><th>Qty</th><th>Unit price</th><th>Total</th></tr></thead>
          <tbody>@for(line of inv.items;track $index){<tr>
            <td data-label="Description">{{line.description}}</td>
            <td data-label="Qty">{{line.quantity}}</td>
            <td data-label="Unit price">{{line.unitPrice | number:'1.2-2'}}</td>
            <td data-label="Total">{{line.lineTotal | number:'1.2-2'}}</td>
          </tr>}</tbody>
        </table></div>
        <div class="invoice-totals"><p>Subtotal <strong>{{inv.subtotal | number:'1.2-2'}} {{inv.currencyCode}}</strong></p>
          <p>Tax <strong>{{inv.taxAmount | number:'1.2-2'}} {{inv.currencyCode}}</strong></p>
          <p class="invoice-total">Total <strong>{{inv.totalAmount | number:'1.2-2'}} {{inv.currencyCode}}</strong></p>
        </div>
        @if(manager && inv.status==='DRAFT'){
          <div class="repair-action-box"><label>Due date <input type="date" [(ngModel)]="dueDate" /></label>
          <button class="primary" type="button" (click)="issue()" [disabled]="busy()">Issue invoice to client</button></div>
        }
        @if(inv.status==='ISSUED'||inv.status==='PAID'){
          <div class="table-actions"><button type="button" class="primary" (click)="print(false)">Print customer invoice</button>
            @if(manager){<button type="button" class="secondary" (click)="print(true)" [disabled]="!internal()">Print internal profit copy</button>}
          </div>
        }
        @if(!manager && inv.status==='ISSUED'){<app-whish-payment [companyId]="companyId" [invoiceId]="inv.invoiceId" />}
        @if(manager && internal(); as finance){
          <div class="margin-preview"><span>Internal direct costs: {{finance.totalDirectCost | number:'1.2-2'}} {{inv.currencyCode}}</span>
            <strong>Gross profit (before tax): {{finance.grossProfitBeforeTax | number:'1.2-2'}} {{inv.currencyCode}}</strong>
            <small class="muted">Restricted to owners/managers. Excludes operating costs and payment fees.</small></div>
        }
      } @else if(manager && (status==='COMPLETED'||status==='DELIVERED')) {
        <div class="repair-action-box">
          <p class="muted">No invoice for this repair yet. Before issuing, save the technician's diagnosis, work performed, and labor charges.</p>
          <label>Tax amount ({{currency()}})<input type="number" min="0" step="0.01" [(ngModel)]="taxAmount" /></label>
          <button type="button" class="primary" [disabled]="busy()" (click)="create()">Create draft repair invoice</button>
        </div>
      } @else if(!manager) {
        <p class="muted">The repair invoice will appear once the company issues it.</p>
      }
    </section>
  `
})
export class RepairInvoicePanel implements OnChanges {
  @Input({required:true}) companyId!:number;
  @Input({required:true}) repairId!:number;
  @Input() status: RepairStatus='NEW';
  @Input() manager=false;
  private readonly api=inject(Api);
  invoice=signal<RepairInvoiceReport|null>(null);
  internal=signal<RepairInvoiceInternalReport|null>(null);
  busy=signal(false);error=signal('');message=signal('');
  taxAmount=0;
  dueDate=new Date(Date.now()+14*86400000).toISOString().slice(0,10);
  ngOnChanges(){this.invoice.set(null);this.internal.set(null);if(this.companyId&&this.repairId)this.load();}
  currency(){return this.invoice()?.currencyCode??'company currency';}
  load(){if(!this.companyId||!this.repairId)return;
    const companyId=this.companyId,repairId=this.repairId;
    this.api.get<RepairInvoiceReport>(`/companies/${companyId}/repairs/${repairId}/invoice-report`).subscribe({
      next:v=>{if(this.companyId!==companyId||this.repairId!==repairId)return;
        this.invoice.set(v);this.error.set('');if(this.manager)this.loadInternal();},
      error:e=>{if(this.companyId!==companyId||this.repairId!==repairId)return;
        this.invoice.set(null);this.internal.set(null);
        if(!(e instanceof HttpErrorResponse&&e.status===404))this.error.set('Could not load repair invoice.');}
    });
  }
  private loadInternal(){if(!this.manager)return;
    this.api.get<RepairInvoiceInternalReport>(`/companies/${this.companyId}/repairs/${this.repairId}/invoice-report/internal`).subscribe({
      next:r=>{if(r.invoice.repairId===this.repairId)this.internal.set(r);},
      error:()=>this.internal.set(null)
    });
  }
  create(){if(!this.manager||this.busy()||!Number.isFinite(this.taxAmount)||this.taxAmount<0)return;
    this.busy.set(true);this.error.set('');
    this.api.post(`/companies/${this.companyId}/invoices`,{orderId:null,repairId:this.repairId,taxAmount:this.taxAmount}).subscribe({
      next:()=>{this.busy.set(false);this.message.set('Draft repair invoice created. Review and issue it.');this.load();},
      error:()=>{this.busy.set(false);this.error.set('Cannot create invoice. Complete the repair, assign a technician and save a detailed report first.');}
    });
  }
  issue(){const inv=this.invoice();if(!this.manager||!inv||inv.status!=='DRAFT'||this.busy())return;
    const due=new Date(`${this.dueDate}T23:59:00`);
    if(!Number.isFinite(due.getTime())||due.getTime()<=Date.now()){
      this.error.set('Select a valid future due date.');return;
    }
    this.busy.set(true);this.api.post(`/companies/${this.companyId}/invoices/${inv.invoiceId}/issue`,{
      dueAt:due.toISOString().slice(0,19)
    }).subscribe({next:()=>{this.busy.set(false);this.message.set('Invoice issued to the client.');this.load();},
      error:()=>{this.busy.set(false);this.error.set('Could not issue the invoice.');}});
  }
  private esc(v:unknown){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]??c));}
  print(includeProfit:boolean){const inv=this.invoice();if(!inv||!['ISSUED','PAID'].includes(inv.status))return;
    if(includeProfit&&(!this.manager||!this.internal()))return;
    const e=(v:unknown)=>this.esc(v);const fmt=(x:number)=>Number(x).toFixed(2);
    const lines=inv.items.map(l=>`<tr><td>${e(l.description)}</td><td>${e(l.quantity)}</td><td>${fmt(l.unitPrice)}</td><td>${fmt(l.lineTotal)}</td></tr>`).join('');
    const internal=this.internal();
    const profitPage=includeProfit&&internal?`<section class="internal"><h2>INTERNAL ONLY — REPAIR COST & GROSS PROFIT</h2><p>Confidential. Do not send to the customer.</p>
      <table><thead><tr><th>Direct cost</th><th>Qty</th><th>Unit cost</th><th>Cost total</th></tr></thead><tbody>
      ${internal.directCostLines.map(l=>`<tr><td>${e(l.description)}</td><td>${l.quantity}</td><td>${fmt(l.unitCost)}</td><td>${fmt(l.totalCost)}</td></tr>`).join('')}</tbody></table>
      <p>Parts cost: ${fmt(internal.partsCost)} ${e(inv.currencyCode)}</p>
      <p>Labor cost: ${fmt(internal.laborCost)} ${e(inv.currencyCode)}</p>
      <p>Total direct cost: ${fmt(internal.totalDirectCost)} ${e(inv.currencyCode)}</p>
      <h2>Gross profit before tax: ${fmt(internal.grossProfitBeforeTax)} ${e(inv.currencyCode)}</h2>
      <p>Not net profit: operating expenses, payment fees and other overhead excluded.</p></section>`:'';
    const html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>${e(inv.invoiceNumber)}</title><style>body{font:14px Arial,sans-serif;color:#19283c;margin:32px;line-height:1.55}h1{font-size:24px}h2{font-size:19px;margin-top:24px}table{width:100%;border-collapse:collapse;margin:20px 0}td,th{border-bottom:1px solid #ccc;padding:9px;text-align:left;overflow-wrap:anywhere}th{background:#edf3f9}p{white-space:pre-wrap}.muted{color:#58677c}.internal{break-before:page;margin-top:40px}@media print{body{margin:10mm}}</style></head><body>
    <h1>REPAIR INVOICE — ${e(inv.invoiceNumber)}</h1><h2>${e(inv.companyName)}</h2>
    <p>${e(inv.companyAddress)}<br>${e(inv.companyEmail)}<br>${e(inv.companyPhone)}</p>
    <p><strong>Customer:</strong> ${e(inv.customerName)} · ${e(inv.customerPhone)}<br>
    <strong>Repair:</strong> #${e(inv.repairId)} · ${e(inv.device)}<br><strong>Technician:</strong> ${e(inv.technicianName)}<br>
    <strong>Issued:</strong> ${e(inv.issuedAt)} · <strong>Due:</strong> ${e(inv.dueAt)}</p>
    <h2>Reported problem</h2><p>${e(inv.reportedProblem)}</p>
    <h2>Diagnosis — why the repair was necessary</h2><p>${e(inv.diagnosis)}</p>
    <h2>Work performed</h2><p>${e(inv.workPerformed)}</p>
    <table><thead><tr><th>Part / service</th><th>Qty</th><th>Unit price</th><th>Line total</th></tr></thead><tbody>${lines}</tbody></table>
    <p>Subtotal: ${fmt(inv.subtotal)} ${e(inv.currencyCode)}<br>Tax: ${fmt(inv.taxAmount)} ${e(inv.currencyCode)}</p>
    <h2>Invoice total: ${fmt(inv.totalAmount)} ${e(inv.currencyCode)}</h2>${profitPage}
    <script>window.addEventListener('load',()=>window.print());<\/script></body></html>`;
    const w=window.open('','_blank');if(!w){this.error.set('Allow pop-ups to print the invoice.');return;}
    w.opener=null;w.document.open();w.document.write(html);w.document.close();
  }
}
