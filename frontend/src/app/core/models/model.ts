export interface User { id: number; fullName: string; email: string; phone?: string | null; platformRole: 'USER'|'OWNER'|'CLIENT'|'SUPER_ADMIN'; }
export interface LoginResult { accessToken: string; tokenType: 'Bearer'; expiresAt: string; user: User; }
export interface Company { id: number; name: string; currencyCode: string; yourRole?: 'OWNER'|'MANAGER'|'EMPLOYEE'; }
export interface CompatibleDevice { deviceType: string; deviceBrand: string; deviceModel: string; }
export interface Product { id: number; sku: string; name: string; description: string; sellingPrice: number; effectivePrice: number; discountPercent: number; currencyCode: string; inStock: boolean; imageUrl: string | null; category: string; deviceType: string; deviceBrand: string; compatibleDevices: CompatibleDevice[]; }
export interface Cart { companyId:number; currencyCode:string; items:Array<{itemId:number; quantity:number; name:string; unitSellingPrice:number; originalUnitPrice:number; discountPercent:number; lineTotal:number}>; estimatedTotal:number; }
export interface DeliveryAddress {
 phone:string; streetAddress:string; city:string; postalCode:string|null;
 country:string; notes:string|null; latitude:number|null; longitude:number|null;
}
export interface CheckoutAddress {
 deliveryPhone:string; streetAddress:string; city:string; postalCode:string;
 country:string; deliveryNotes:string; latitude:number|null; longitude:number|null;
}
export interface OrderLine { itemId:number; name:string; quantity:number; unitSellingPrice:number; lineTotal:number; }
export interface Order {
 id:number; orderNumber:string; companyId:number; customerId:number; status:string; totalAmount:number;
 currencyCode:string; orderedAt:string; completedAt:string|null; customerName:string;
 customerEmail:string; delivery:DeliveryAddress; items:OrderLine[];
}
export interface OrderProfit { orderId:number; currencyCode:string; revenue:number; acquisitionCost:number;
 grossProfit:number; lines:Array<{name:string; quantity:number; unitSellingPrice:number;
 unitCostPrice:number; lineRevenue:number; lineCost:number; lineGrossProfit:number}>;
}
export interface Invoice { id:number; invoiceNumber:string; orderId:number|null; status:string;
 subtotal:number; taxAmount:number; totalAmount:number; currencyCode:string; issuedAt:string|null;
 dueAt:string|null; sellerName:string; sellerAddress:string|null; sellerEmail:string|null; sellerPhone:string|null; lines:Array<{description:string;quantity:number;unitPrice:number;lineTotal:number}>;
}
export type RepairStatus = 'NEW' | 'DIAGNOSING' | 'WAITING_PARTS' | 'IN_PROGRESS' | 'COMPLETED' | 'DELIVERED' | 'CANCELLED';
export interface RepairPart {
  id: number; itemId: number; itemName: string; quantity: number;
  unitSellingPrice: number; unitCostPrice?: number;
}
export interface RepairUpdate {
  id: number; updatedByUserId: number | null; status: RepairStatus; note: string | null; customerNote?: string | null; createdAt: string;
}
export interface Repair {
  id: number; companyId: number; customerId: number;
  clientName: string | null; clientPhone: string | null;
  assignedEmployeeId: number | null;
  deviceType: string; brand: string | null; model: string | null; issueDescription: string;
  diagnosis: string | null; workPerformed: string | null;
  status: RepairStatus; currencyCode: string;
  laborCharge: number; partsCharge: number; totalCharge: number;
  laborCost?: number; partsCost?: number; estimatedGrossProfit?: number;
  createdAt: string; completedAt: string | null;
  parts: RepairPart[]; updates: RepairUpdate[];
}
export interface CustomerRepairEvent {
  id: number; status: RepairStatus; note: string | null; createdAt: string;
}
export interface CustomerRepairTracking {
  id: number; companyId: number; companyName: string;
  deviceType: string; brand: string | null; model: string | null;
  issueDescription: string; status: RepairStatus; currencyCode: string;
  quotedTotal: number; createdAt: string; completedAt: string | null;
  timeline: CustomerRepairEvent[];
}
export interface CompanyMember {
  id: number; userId: number; fullName: string; email: string;
  role: 'OWNER' | 'MANAGER' | 'EMPLOYEE';
}
export interface RepairClientOption { id: number; fullName: string; email: string; phone: string | null; }
export interface StockPart {
  id: number; sku: string; name: string; category: string; stockQuantity: number; active: boolean;
  deviceType: string; deviceBrand: string; compatibleDevices: CompatibleDevice[];
}
export interface RepairProfitSummary {
  companyId: number; currencyCode: string; completedOrDeliveredRepairs: number;
  laborRevenue: number; partsRevenue: number; laborCost: number; partsCost: number;
  estimatedGrossProfit: number;
}

export interface Finance { currencyCode:string; completedSalesRevenue:number; completedRepairRevenue:number; grossProfit:number; estimatedOperatingResultBeforeTax:number; cashCollectedIncludingTax:number; unpaidIssuedInvoices:number; }
export interface Inventory { totalSkus:number; unitsOnHand:number; inventoryCostValue:number; potentialGrossProfit:number; }
export interface PlatformFinance { currencyCode:string; earnedFees:number; expenses:number; estimatedOperatingResultBeforeTax:number; }
