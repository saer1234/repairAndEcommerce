export interface RepairInvoiceLine {
  description:string;quantity:number;unitPrice:number;lineTotal:number;
}
export interface RepairInvoiceReport {
  invoiceId:number;invoiceNumber:string;status:'DRAFT'|'ISSUED'|'PAID'|'VOID';
  repairId:number;device:string;reportedProblem:string;
  diagnosis:string;workPerformed:string;technicianName:string;
  customerName:string;customerPhone:string;
  companyName:string;companyAddress:string|null;companyEmail:string|null;companyPhone:string|null;
  currencyCode:string;issuedAt:string|null;dueAt:string|null;
  subtotal:number;taxAmount:number;totalAmount:number;items:RepairInvoiceLine[];
}
export interface RepairInvoiceInternalReport {
  invoice:RepairInvoiceReport;laborCost:number;partsCost:number;
  totalDirectCost:number;grossProfitBeforeTax:number;
  directCostLines:Array<{description:string;quantity:number;unitCost:number;totalCost:number}>;
}
