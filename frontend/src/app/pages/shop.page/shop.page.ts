import { DecimalPipe } from '@angular/common';
import { StoreLanguageService, StoreLanguage } from '../../core/services/store-language.service';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { Api } from '../../api';
import { AuthService } from '../../core/services/auth.service';
import { CompanyContext } from '../../core/services/company-context';
import { componentCategories, componentCategory, isDeviceTypeValue } from '../../shared/utils/device-classification';
import { ItemDetailComponent } from '../../shared/components/item-detail.component/item-detail.component';
import { itemImageSrc } from '../../shared/utils/item-image';
import { Product, Cart, Order, CheckoutAddress, DeliverySettings } from '../../core/models/model';
import { verifiedFitSelected, verifiedFitTypes, verifiedFitBrands, verifiedFitModels, matchesVerifiedFit } from '../../shared/utils/verified-fit';
import { WhishPaymentComponent } from '../../shared/components/whish-payment.component/whish-payment.component';

@Component({
  imports: [FormsModule,DecimalPipe,RouterLink,ItemDetailComponent,WhishPaymentComponent],
  selector: 'app-shop.page',
  styleUrl: './shop.page.css',
  templateUrl: './shop.page.html',
})
export class ShopPage {
  readonly lang=inject(StoreLanguageService);
  t=(key:string)=>this.lang.t(key);
  setLanguage(value:StoreLanguage){this.lang.setLanguage(value);}
  imageSrc=itemImageSrc;
 private api=inject(Api);ctx=inject(CompanyContext);auth=inject(AuthService);
 private router=inject(Router);
 private route=inject(ActivatedRoute);
 private companyQueryApplied = false;
 readonly isGuest = () => !this.auth.signedIn();
 requestSignIn(): void {
   const companyId = this.ctx.selected();
   const returnUrl = companyId ? `/shop?companyId=${companyId}` : '/shop';
   void this.router.navigate(['/login'], {queryParams: {returnUrl}});
 }

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
 fulfillmentMethod:'PICKUP'|'DELIVERY'='PICKUP';
 deliverySettings=signal<DeliverySettings>({deliveryEnabled:false,deliveryFee:0,currencyCode:''});
 deliveryConfirmOpen=signal(false);
 payableTotal(){return Math.round((Number(this.cart()?.estimatedTotal??0)+(this.fulfillmentMethod==='DELIVERY' ? Number(this.deliverySettings().deliveryFee):0))*100)/100;}
 chooseFulfillment(value:'PICKUP'|'DELIVERY'){
   this.fulfillmentMethod=value;
   this.error.set('');
 }
 requestCheckout(){
   if (this.isGuest()) {this.requestSignIn();return;}
   if(this.fulfillmentMethod==='DELIVERY') {
     if(!this.deliverySettings().deliveryEnabled){this.error.set('Delivery is unavailable for this shop.');return;}
     if(!this.validCoordinates() || !this.delivery.deliveryPhone.trim() || !this.delivery.streetAddress.trim() ||
       !this.delivery.city.trim() || !this.delivery.country.trim()) {
         this.error.set('For delivery, complete the address, phone and map location.');return;
       }
     this.deliveryConfirmOpen.set(true);return;
   }
   this.checkout();
 }
 cancelDelivery(){this.deliveryConfirmOpen.set(false);}
 confirmDelivery(){this.deliveryConfirmOpen.set(false);this.checkout();}
 delivery:CheckoutAddress={fulfillmentMethod:'PICKUP',deliveryPhone:'',streetAddress:'',city:'',postalCode:'',country:'',deliveryNotes:'',latitude:null,longitude:null};
 constructor(){this.delivery.deliveryPhone=this.auth.user()?.phone??'';
  effect(()=>{
    const companies = this.ctx.available();
    if (!this.ctx.loaded() || !companies.length) return;
    if (!this.companyQueryApplied) {
      this.companyQueryApplied = true;
      const requestedCompany = Number(this.route.snapshot.queryParamMap.get('companyId'));
      if (Number.isSafeInteger(requestedCompany) && requestedCompany > 0 &&
          companies.some(c => c.id === requestedCompany) && this.ctx.selected() !== requestedCompany) {
        this.ctx.selected.set(requestedCompany);
        return;
      }
    }
    if (this.ctx.selected()) {this.resetPage();this.refresh();}
  });}

 refresh(){this.selectedItem.set(null);const id=this.ctx.selected();if(!id)return;if(this.lastOrder() && this.lastOrder()!.companyId!==id)this.lastOrder.set(null);this.error.set('');
  this.api.get<Product[]>(`/companies/${id}/items`).subscribe({next:v=>{this.products.set(v);this.setPage(this.page());},error:()=>this.error.set('Unable to load items')});
  this.api.get<DeliverySettings>(`/catalog/companies/${id}/delivery-settings`).subscribe({
    next:s=>{this.deliverySettings.set(s);if(!s.deliveryEnabled)this.fulfillmentMethod='PICKUP';},
    error:()=>{this.deliverySettings.set({deliveryEnabled:false,deliveryFee:0,currencyCode:''});this.fulfillmentMethod='PICKUP';}
  });
  if (this.auth.canShop()) {
    this.api.get<Cart>(`/companies/${id}/cart`).subscribe({next:v=>this.useCart(v),error:()=>this.cart.set(null)});
  } else this.cart.set(null);
 }
 private useCart(c:Cart){this.cart.set(c);this.quantityDraft=Object.fromEntries(c.items.map(i=>[i.itemId,i.quantity]));}
 add(p:Product){if (this.isGuest()) {this.requestSignIn();return;}
  const id=this.ctx.selected();if(!id || !this.auth.canShop() || this.busy())return;
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
 checkout(){const id=this.ctx.selected();if(!id||!this.auth.canShop()||this.busy())return;
  if(this.fulfillmentMethod==='DELIVERY' && (!this.validCoordinates() || !this.delivery.deliveryPhone.trim() ||
     !this.delivery.streetAddress.trim() || !this.delivery.city.trim() || !this.delivery.country.trim()))return;
  const changed=(this.cart()?.items??[]).some(i=>this.quantityDraft[i.itemId]!==i.quantity);
  if(changed){this.error.set('Save quantity changes using Update before placing the order.');return;}
  this.error.set('');this.message.set('');this.busy.set(true);
  this.api.post<Order>(`/companies/${id}/checkout`,{...this.delivery, fulfillmentMethod:this.fulfillmentMethod, expectedTotal:this.payableTotal()}).subscribe({next:order=>{this.busy.set(false);this.lastOrder.set(order);this.message.set(`Order ${order.orderNumber} submitted. Use Whish transfer below if you want to pay now, or view My orders.`);this.refresh();},error:()=>{this.error.set('Checkout failed. Prices may have changed; refresh your cart and confirm the total, then retry.');this.busy.set(false);this.refresh();}});
 }
}
