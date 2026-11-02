import { Component, inject, signal, computed, effect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WhishPaymentComponent } from './whish-payment.component';
import { Api } from './api';
import { CompanyContext } from './company-context';
import { AuthService } from './auth.service';
import { Product, Cart, CheckoutAddress, Order } from './models';
import { itemImageSrc } from './item-image';
import { ItemDetailsComponent } from './item-details.component';
import { matchesVerifiedFit, verifiedFitSelected, verifiedFitTypes, verifiedFitBrands, verifiedFitModels } from './verified-fit';
import { isDeviceTypeValue, componentCategory, componentCategories } from './device-classification';

@Component({standalone:true,imports:[FormsModule,DecimalPipe,RouterLink,ItemDetailsComponent,WhishPaymentComponent],template:`
<section class="page-heading"><div><h1>Customer store</h1><p class="muted">Set quantities, confirm the delivery address and drop a location pin before ordering.</p></div>
<div class="table-actions"><a routerLink="/orders" class="button-link secondary">My orders & invoices</a><button type="button" (click)="refresh()" class="secondary">Refresh</button></div></section>
@if(message()){<p class="success" role="status">{{message()}}</p>}
@if(lastOrder();as completedOrder){<app-whish-payment [companyId]="completedOrder.companyId" [orderId]="completedOrder.id" />}
@if(error()){<p class="error" role="alert">{{error()}}</p>}
<section class="panel verified-fit-finder" aria-label="Find an exact verified device fit">
  <div class="panel-heading"><div><h2>Find verified fit</h2>
    <p class="muted tiny">Choose your device type, brand and exact model. Only parts explicitly listed for that model will match.</p></div>
    <button type="button" class="secondary" (click)="clearVerifiedFit()">Clear fit</button></div>
  <div class="fit-fields">
    <label>Device type
      <select [ngModel]="fitDeviceType" (ngModelChange)="chooseFitType($event)" aria-label="Fit device type">
        <option value="ALL">Choose type</option>
        @for(type of fitTypes();track type){<option [value]="type">{{type}}</option>}
      </select>
    </label>
    <label>Device brand
      <select [ngModel]="fitDeviceBrand" (ngModelChange)="chooseFitBrand($event)" [disabled]="fitDeviceType==='ALL'" aria-label="Fit device brand">
        <option value="ALL">Choose brand</option>
        @for(brand of fitBrands();track brand){<option [value]="brand">{{brand}}</option>}
      </select>
    </label>
    <label>Exact device model
      <input type="search" list="customer-fit-models" [ngModel]="fitDeviceModel" (ngModelChange)="fitDeviceModel=$event;resetPage()" [disabled]="fitDeviceBrand==='ALL'"
        placeholder="e.g. Galaxy S24" aria-label="Exact model for verified fit" />
      <datalist id="customer-fit-models">@for(model of fitModels();track model){<option [value]="model"></option>}</datalist>
    </label>
  </div>
  @if(fitActive()) {<p class="verified-fit-result" role="status">{{filteredProducts().length}} matching product(s) recorded for {{fitDeviceBrand}} {{fitDeviceModel}}.</p>}
  @else {<p class="muted tiny">Exact fit requires all three fields. General product category or a wildcard does not count as verified.</p>}
</section>
<section class="store-filters panel" aria-label="Search and filter products">
  <label>Search products
    <input type="search" [ngModel]="searchText" (ngModelChange)="searchText=$event;resetPage()" placeholder="Search by name, SKU, brand or compatible model" aria-label="Search customer store" />
  </label>
  <label>Device type
    <select [ngModel]="selectedDeviceType" (ngModelChange)="selectedDeviceType=$event;selectedBrand='ALL';resetPage()" aria-label="Filter by device type">
      <option value="ALL">All device types</option>
      @for(type of deviceTypes();track type){<option [value]="type">{{type}}</option>}
    </select>
  </label>
  <label>Brand
    <select [ngModel]="selectedBrand" (ngModelChange)="selectedBrand=$event;resetPage()" aria-label="Filter by brand">
      <option value="ALL">All brands</option>
      @for(brand of brands();track brand){<option [value]="brand">{{brand}}</option>}
    </select>
  </label>
  <label>Component category
    <select [ngModel]="selectedCategory" (ngModelChange)="selectedCategory=$event;resetPage()" aria-label="Filter store by component category">
      <option value="ALL">All components</option>
      @for(category of categories();track category){<option [value]="category">{{category}}</option>}
    </select>
  </label>
  <div class="muted tiny filter-count">{{filteredProducts().length}} of {{products().length}} items</div>
</section>
@if(filteredProducts().length){
 <div class="pagination-toolbar" role="group" aria-label="Customer store pagination">
 <span>Showing {{pageStart()}}–{{pageEnd()}} of {{filteredProducts().length}} products</span>
 <label>Per page <select [ngModel]="pageSize()" (ngModelChange)="setPageSize($event)"><option [ngValue]="8">8</option><option [ngValue]="12">12</option><option [ngValue]="24">24</option></select></label>
 <div class="pagination-actions"><button type="button" class="secondary" (click)="setPage(page()-1)" [disabled]="page()===1">Previous</button>
 <span>Page {{page()}} of {{pageCount()}}</span>
 <button type="button" class="secondary" (click)="setPage(page()+1)" [disabled]="page()>=pageCount()">Next</button></div>
 <div class="view-toggle" role="group" aria-label="Product display mode"><button type="button" class="secondary" [attr.aria-pressed]="viewMode()==='cards'" (click)="viewMode.set('cards')">Cards</button><button type="button" class="secondary" [attr.aria-pressed]="viewMode()==='table'" (click)="viewMode.set('table')">Table</button></div>
 </div>
}
@if(viewMode()==='cards') {
<div class="product-grid">
 @for(item of pagedProducts();track item.id){<article class="product-card">
 @if(item.imageUrl){<img class="store-product-image" [src]="imageSrc(item.imageUrl)" [alt]="item.name" loading="lazy" />}
 @else{<div class="store-product-placeholder">No product photo</div>}
 <span class="chip product-category">{{item.deviceType}} → {{item.deviceBrand}} · {{itemCategory(item)}}</span>
 @if(fitActive() && itemVerified(item)){<span class="verified-fit-label">✓ Exact fit recorded: {{fitDeviceBrand}} {{fitDeviceModel}}</span>}
 @if(item.compatibleDevices.length){<div class="muted tiny">Recorded compatibility:
   @for(fit of item.compatibleDevices;track $index){<span class="fit-chip">{{fit.deviceBrand}} {{fit.deviceModel}}</span>}
 </div>} @else {<p class="muted tiny">Compatibility not yet verified</p>}<h3>{{item.name}}</h3><p class="muted tiny">{{item.sku}}</p><p class="muted item-description-teaser">{{item.description || 'No description added yet.'}}</p>
 <button type="button" class="secondary item-detail-button" (click)="openDetails(item)" [attr.aria-label]="'View details of '+item.name">View full description</button>
 <div class="product-bottom"><strong>{{item.sellingPrice | number:'1.2-2'}} {{item.currencyCode}}</strong><button type="button" class="secondary" [disabled]="!item.inStock" (click)="add(item)">{{item.inStock?'Add to cart':'Out of stock'}}</button></div>
 </article>}
</div>
} @else {
 <div class="panel table-scroll"><table class="customer-products-table"><thead><tr><th>Product</th><th>Device / Brand</th><th>Price</th><th>Stock</th><th></th></tr></thead><tbody>
 @for(item of pagedProducts();track item.id){<tr>
 <td><div class="inventory-item-name">@if(item.imageUrl){<img class="item-thumbnail" [src]="imageSrc(item.imageUrl)" [alt]="item.name" loading="lazy" />}<div><strong>{{item.name}}</strong><p class="muted tiny">{{item.sku}}</p></div></div></td>
 <td>{{item.deviceType}} / {{item.deviceBrand}} · {{itemCategory(item)}}
 @if(fitActive() && itemVerified(item)){<span class="verified-fit-label">✓ Exact fit recorded</span>}</td>
 <td>{{item.sellingPrice | number:'1.2-2'}} {{item.currencyCode}}</td>
 <td>{{item.inStock?'Available':'Out of stock'}}</td>
 <td><div class="table-actions"><button type="button" class="secondary" (click)="openDetails(item)">Details</button><button type="button" class="secondary" [disabled]="!item.inStock" (click)="add(item)">Add to cart</button></div></td>
 </tr>}</tbody></table></div>
}
@if(!products().length){<p class="muted">No items listed for this company.</p>}
@if(products().length && !filteredProducts().length){<p class="panel muted" role="status">{{fitActive()?'No explicitly recorded fit for that exact model. Do not assume a different model will fit.':'No matching products. Try a different search or category.'}}</p>}
@if(selectedItem(); as item){
 <app-item-details [item]="item" (close)="closeDetails()" />
}
<section class="panel"><h3>Review your cart</h3>
@if(cart();as c){
 @for(line of c.items;track line.itemId){<div class="cart-line checkout-cart-line">
 <div><strong>{{line.name}}</strong><div class="muted tiny">{{line.unitSellingPrice | number:'1.2-2'}} {{c.currencyCode}} each</div></div>
 <label class="qty-control">Quantity
 <input type="number" min="1" max="100000" step="1" [ngModel]="quantityDraft[line.itemId] ?? line.quantity" (ngModelChange)="quantityDraft[line.itemId]=$event" [attr.aria-label]="'Quantity for '+line.name" />
 </label><button type="button" class="secondary" [disabled]="busy()" (click)="updateQuantity(line.itemId)">Update</button>
 <span>{{line.lineTotal | number:'1.2-2'}} {{c.currencyCode}}</span><button type="button" class="secondary" [disabled]="busy()" (click)="remove(line.itemId)">Remove</button></div>}
 <p class="checkout-total"><strong>Total: {{c.estimatedTotal | number:'1.2-2'}} {{c.currencyCode}}</strong></p>
 @if(c.items.length){
 <form #deliveryForm="ngForm" (ngSubmit)="checkout()" class="delivery-form">
  <h3>Delivery details</h3><p class="muted tiny">Required before the order is placed. Your account identity is used as the customer name.</p>
  <div class="field-pair"><label>Delivery phone
   <input name="phone" [(ngModel)]="delivery.deliveryPhone" required maxlength="40" autocomplete="tel" />
  </label><label>Country
   <input name="country" [(ngModel)]="delivery.country" required maxlength="100" autocomplete="country-name" />
  </label></div>
  <label>Street address and building
    <input name="street" [(ngModel)]="delivery.streetAddress" required maxlength="255" autocomplete="street-address" />
  </label>
  <div class="field-pair"><label>City
   <input name="city" [(ngModel)]="delivery.city" required maxlength="120" autocomplete="address-level2" />
  </label><label>Postal code (optional)
   <input name="postalCode" [(ngModel)]="delivery.postalCode" maxlength="24" autocomplete="postal-code" />
  </label></div>
  <label>Delivery instructions (optional)
   <textarea name="notes" [(ngModel)]="delivery.deliveryNotes" maxlength="500" rows="2" placeholder="Floor, entry instructions, etc."></textarea>
  </label>
  <h4>Delivery map location <span class="muted tiny">(required pin)</span></h4>
  <button type="button" class="secondary" [disabled]="locating()" (click)="useCurrentLocation()">{{locating()?'Locating…':'Use my current location'}}</button>
  <p class="muted tiny">Allow location access on HTTPS or localhost, or enter coordinates from your map app. Check the pin before ordering.</p>
  <div class="field-pair"><label>Latitude
   <input type="number" name="latitude" [(ngModel)]="delivery.latitude" required min="-90" max="90" step="any" placeholder="e.g. 33.8938" />
  </label><label>Longitude
   <input type="number" name="longitude" [(ngModel)]="delivery.longitude" required min="-180" max="180" step="any" placeholder="e.g. 35.5018" />
  </label></div>
  @if(validCoordinates()) {<a [href]="mapUrl()" target="_blank" rel="noopener noreferrer">Verify pin on OpenStreetMap ↗</a>}
  <div class="checkout-actions"><button class="primary" type="submit" [disabled]="busy() || deliveryForm.invalid || !validCoordinates()">{{busy()?'Submitting…':'Place order'}}</button><p class="muted tiny">Stock and prices are checked again when you submit. The final quantities are the saved cart quantities.</p></div>
 </form>}
 }@else{<p class="muted">Your cart is empty or has not been loaded yet.</p>}
</section>`})
export class ShopPage {
 imageSrc=itemImageSrc;
 private api=inject(Api);ctx=inject(CompanyContext);auth=inject(AuthService);
 products=signal<Product[]>([]);
 selectedItem=signal<Product|null>(null);
 private detailTrigger: HTMLElement|null=null;
 openDetails(item:Product):void {this.detailTrigger=document.activeElement instanceof HTMLElement?document.activeElement:null;this.selectedItem.set(item);}
 closeDetails():void {this.selectedItem.set(null);this.detailTrigger?.focus();this.detailTrigger=null;}
 page=signal(1);pageSize=signal(12);viewMode=signal<'cards'|'table'>('cards');
 resetPage(){this.page.set(1);}
 pageCount(){return Math.max(1,Math.ceil(this.filteredProducts().length/this.pageSize()));}
 setPage(n:number){this.page.set(Math.min(this.pageCount(),Math.max(1,n)));}
 setPageSize(n:number){this.pageSize.set(Number(n));this.resetPage();}
 pageStart(){return this.filteredProducts().length?(this.page()-1)*this.pageSize()+1:0;}
 pageEnd(){return Math.min(this.filteredProducts().length,this.page()*this.pageSize());}
 pagedProducts(){return this.filteredProducts().slice((this.page()-1)*this.pageSize(),this.page()*this.pageSize());}
 searchText='';selectedDeviceType='ALL';selectedBrand='ALL';selectedCategory='ALL';
 categories=()=>componentCategories(this.products());
 itemCategory=componentCategory;
 fitDeviceType='ALL';fitDeviceBrand='ALL';fitDeviceModel='';
 fitQuery(){return {deviceType:this.fitDeviceType,deviceBrand:this.fitDeviceBrand,deviceModel:this.fitDeviceModel};}
 fitActive(){return verifiedFitSelected(this.fitQuery());}
 fitTypes=()=>verifiedFitTypes(this.products());
 fitBrands=()=>verifiedFitBrands(this.products(),this.fitDeviceType);
 fitModels=()=>verifiedFitModels(this.products(),this.fitDeviceType,this.fitDeviceBrand);
 itemVerified(item:Product){return matchesVerifiedFit(item,this.fitQuery());}
 chooseFitType(type:string){this.fitDeviceType=type;this.fitDeviceBrand='ALL';this.fitDeviceModel='';this.resetPage();}
 chooseFitBrand(brand:string){this.fitDeviceBrand=brand;this.fitDeviceModel='';this.resetPage();}
 clearVerifiedFit(){this.fitDeviceType='ALL';this.fitDeviceBrand='ALL';this.fitDeviceModel='';this.resetPage();}
 deviceTypes=computed(()=>[...new Set(this.products().map(p=>p.deviceType).filter(isDeviceTypeValue))].sort());
 brands=()=>[...new Set(this.products().filter(p=>this.selectedDeviceType==='ALL'||p.deviceType===this.selectedDeviceType).map(p=>p.deviceBrand||'Universal'))].sort();
 filteredProducts=()=>this.products().filter(p=>{
  const term=this.searchText.trim().toLowerCase();
  // When checking compatibility, primary category filters must not hide cross-brand compatible items.
  const matchesType=this.fitActive()||this.selectedDeviceType==='ALL'||p.deviceType===this.selectedDeviceType;
  const matchesBrand=this.fitActive()||this.selectedBrand==='ALL'||p.deviceBrand===this.selectedBrand;
  const matchesVerified=!this.fitActive() || this.itemVerified(p);
  const matchesCategory=this.selectedCategory==='ALL'||this.itemCategory(p)===this.selectedCategory;
  const matchesText=!term||`${p.name} ${p.sku} ${p.deviceType} ${p.deviceBrand} ${p.category||''} ${p.description||''} ${(p.compatibleDevices||[]).map(x=>`${x.deviceType} ${x.deviceBrand} ${x.deviceModel}`).join(' ')}`.toLowerCase().includes(term);
  return matchesType&&matchesBrand&&matchesText&&matchesVerified&&matchesCategory;
 });
 cart=signal<Cart|null>(null);lastOrder=signal<Order|null>(null);message=signal('');error=signal('');busy=signal(false);locating=signal(false);
 quantityDraft:Record<number,number>={};
 delivery:CheckoutAddress={deliveryPhone:'',streetAddress:'',city:'',postalCode:'',country:'',deliveryNotes:'',latitude:null,longitude:null};
 constructor(){this.delivery.deliveryPhone=this.auth.user()?.phone??'';
  effect(()=>{if(this.ctx.selected() && this.auth.canShop()){this.resetPage();this.refresh();}});}
 refresh(){this.selectedItem.set(null);const id=this.ctx.selected();if(!id || !this.auth.canShop())return;if(this.lastOrder() && this.lastOrder()!.companyId!==id)this.lastOrder.set(null);this.error.set('');
  this.api.get<Product[]>(`/companies/${id}/items`).subscribe({next:v=>{this.products.set(v);this.setPage(this.page());},error:()=>this.error.set('Unable to load items')});
  this.api.get<Cart>(`/companies/${id}/cart`).subscribe({next:v=>this.useCart(v),error:()=>this.cart.set(null)});
 }
 private useCart(c:Cart){this.cart.set(c);this.quantityDraft=Object.fromEntries(c.items.map(i=>[i.itemId,i.quantity]));}
 add(p:Product){const id=this.ctx.selected();if(!id || !this.auth.canShop() || this.busy())return;
  const current=this.cart()?.items.find(l=>l.itemId===p.id)?.quantity??0;
  this.busy.set(true);this.api.put<Cart>(`/companies/${id}/cart/items/${p.id}`,{quantity:current+1}).subscribe({next:v=>{this.useCart(v);this.message.set('Item added.');this.busy.set(false);},error:()=>{this.error.set('Could not add item (check stock).');this.busy.set(false);}});
 }
 updateQuantity(itemId:number){const id=this.ctx.selected(), quantity=Number(this.quantityDraft[itemId]);if(!id||this.busy())return;
  if(!Number.isSafeInteger(quantity)||quantity<1||quantity>100000){this.error.set('Enter a whole quantity of at least 1.');return;}
  this.busy.set(true);this.api.put<Cart>(`/companies/${id}/cart/items/${itemId}`,{quantity}).subscribe({next:v=>{this.useCart(v);this.message.set('Quantity updated.');this.error.set('');this.busy.set(false);},error:()=>{this.error.set('Quantity could not be updated; check available stock.');this.busy.set(false);}});
 }
 remove(itemId:number){const id=this.ctx.selected();if(!id||this.busy())return;this.busy.set(true);
  this.api.delete<Cart>(`/companies/${id}/cart/items/${itemId}`).subscribe({next:v=>{this.useCart(v);this.busy.set(false);},error:()=>{this.error.set('Could not remove item.');this.busy.set(false);}});
 }
 validCoordinates(){const lat=this.delivery.latitude,lon=this.delivery.longitude;
  return lat!==null&&lon!==null&&Number.isFinite(lat)&&Number.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;}
 mapUrl(){return `https://www.openstreetmap.org/?mlat=${this.delivery.latitude}&mlon=${this.delivery.longitude}#map=16/${this.delivery.latitude}/${this.delivery.longitude}`;}
 useCurrentLocation(){if(!navigator.geolocation){this.error.set('Geolocation unavailable. Enter latitude and longitude manually.');return;}
  this.locating.set(true);navigator.geolocation.getCurrentPosition(pos=>{this.delivery.latitude=+pos.coords.latitude.toFixed(7);this.delivery.longitude=+pos.coords.longitude.toFixed(7);this.locating.set(false);this.error.set('');},()=>{this.locating.set(false);this.error.set('Could not get your location. Enter coordinates from a map.');},{enableHighAccuracy:true,timeout:12000,maximumAge:0});
 }
 checkout(){const id=this.ctx.selected();if(!id||!this.auth.canShop()||this.busy()||!this.validCoordinates())return;
  if(!this.delivery.deliveryPhone.trim()||!this.delivery.streetAddress.trim()||!this.delivery.city.trim()||!this.delivery.country.trim())return;
  const changed=(this.cart()?.items??[]).some(i=>this.quantityDraft[i.itemId]!==i.quantity);
  if(changed){this.error.set('Save quantity changes using Update before placing the order.');return;}
  this.error.set('');this.message.set('');this.busy.set(true);
  this.api.post<Order>(`/companies/${id}/checkout`,{...this.delivery}).subscribe({next:order=>{this.busy.set(false);this.lastOrder.set(order);this.message.set(`Order ${order.orderNumber} submitted. Use Whish transfer below if you want to pay now, or view My orders.`);this.refresh();},error:()=>{this.error.set('Checkout failed. Check delivery details and available stock.');this.busy.set(false);}});
 }
}
