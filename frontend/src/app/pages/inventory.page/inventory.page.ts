import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, ElementRef, inject, signal, untracked, ViewChild } from '@angular/core';
import { Api } from '../../api';
import { CompanyContext } from "../../core/services/company-context";
import { componentCategories, componentCategory, isDeviceTypeValue } from '../../shared/utils/device-classification';
import { itemImageSrc } from '../../shared/utils/item-image';
import { verifiedFitSelected, verifiedFitTypes, verifiedFitBrands, verifiedFitModels, matchesVerifiedFit } from '../../shared/utils/verified-fit';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ItemDetailComponent } from '../../shared/components/item-detail.component/item-detail.component';
import { CompatibleDevice } from '../../core/models/model';
import { FormsModule } from '@angular/forms';


interface BulkImportPreview {
  totalRows: number;
  validRows: number;
  skippedExisting: number;
  errors: {row:number;message:string}[];
  sampleSkus: string[];
}

interface ManagedItem {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  category: string;
  deviceType: string;
  deviceBrand: string;
  compatibleDevices: CompatibleDevice[];
  costPrice: number;
  sellingPrice: number;
  currencyCode: string;
  potentialGrossProfitPerUnit: number;
  stockQuantity: number;
  active: boolean;
  visibleToClients: boolean;
  imageUrl: string | null;
}

interface ItemForm {
  sku: string;
  name: string;
  description: string;
  category: string;
  deviceType: string;
  deviceBrand: string;
  compatibleDevices: CompatibleDevice[];
  costPrice: number | null;
  sellingPrice: number | null;
  openingStock: number | null;
}

function blankForm(): ItemForm {
  return { sku: '', name: '', description: '', category: 'General', deviceType: 'Phone', deviceBrand: '', compatibleDevices: [], costPrice: null, sellingPrice: null, openingStock: 0 };
}

@Component({
  imports: [FormsModule, DecimalPipe,RouterLink,ItemDetailComponent],
  selector: 'app-inventory.page',
  styleUrl: './inventory.page.css',
  templateUrl: './inventory.page.html',
})
export class InventoryPage {
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild('bulkFileInput') private bulkFileInput?: ElementRef<HTMLInputElement>;
  readonly ctx = inject(CompanyContext);
  private readonly api = inject(Api);
  readonly items = signal<ManagedItem[]>([]);
  readonly selectedItem = signal<ManagedItem | null>(null);
  private detailTrigger: HTMLElement | null = null;
  openDetails(item:ManagedItem):void { this.detailTrigger=document.activeElement instanceof HTMLElement?document.activeElement:null;this.selectedItem.set(item); }
  closeDetails():void { this.selectedItem.set(null);this.detailTrigger?.focus();this.detailTrigger=null; }
  readonly editingId = signal<number | null>(null);
  readonly restocking = signal<ManagedItem | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly savingStock = signal(false);
  readonly busyItemId = signal<number | null>(null);
  readonly error = signal('');
  readonly message = signal('');
  readonly previewUrl = signal<string | null>(null);
  readonly editingPhoto = signal<string | null>(null);
  private selectedPhoto: File | null = null;
  private photoVersion = signal(Date.now());
  readonly page = signal(1);
  readonly pageSize = signal(10);
  readonly bulkFileName = signal('');
  readonly bulkPreview = signal<BulkImportPreview | null>(null);
  readonly bulkBusy = signal(false);
  readonly bulkError = signal('');
  bulkFile: File | null = null;

  draft: ItemForm = blankForm();
  stockQuantity: number | null = 1;
  stockNote = '';
  inventorySearch = '';
  typeFilter = 'ALL';
  brandFilter = 'ALL';
  categoryFilter = 'ALL';
  categories=()=>componentCategories(this.items());
  itemCategory=componentCategory;
  isValidDeviceType=isDeviceTypeValue;
  itemStatusFilter = 'ACTIVE';
  fitDeviceType='ALL';fitDeviceBrand='ALL';fitDeviceModel='';
  fitQuery(){return {deviceType:this.fitDeviceType,deviceBrand:this.fitDeviceBrand,deviceModel:this.fitDeviceModel};}
  fitActive(){return verifiedFitSelected(this.fitQuery());}
  fitTypes=()=>verifiedFitTypes(this.items());
  fitBrands=()=>verifiedFitBrands(this.items(),this.fitDeviceType);
  fitModels=()=>verifiedFitModels(this.items(),this.fitDeviceType,this.fitDeviceBrand);
  itemVerified(item:ManagedItem){return matchesVerifiedFit(item,this.fitQuery());}
  chooseFitType(type:string){this.fitDeviceType=type;this.fitDeviceBrand='ALL';this.fitDeviceModel='';this.resetPage();}
  chooseFitBrand(brand:string){this.fitDeviceBrand=brand;this.fitDeviceModel='';this.resetPage();}
  clearVerifiedFit(){this.fitDeviceType='ALL';this.fitDeviceBrand='ALL';this.fitDeviceModel='';this.resetPage();}
  fitType = '';
  fitBrand = '';
  fitModel = '';
  deviceTypes = () => [...new Set(this.items().map(i => i.deviceType).filter(isDeviceTypeValue))].sort();
  deviceBrands = () => [...new Set(this.items().filter(i => this.typeFilter === 'ALL' || i.deviceType === this.typeFilter).map(i => i.deviceBrand))].sort();
  validFitInput(): boolean {
    return isDeviceTypeValue(this.fitType) && this.fitType.length <= 100 && !!this.fitBrand.trim() && this.fitBrand.length <= 100
      && !!this.fitModel.trim() && this.fitModel.length <= 120 && this.draft.compatibleDevices.length < 50;
  }
  addCompatibleDevice(): void {
    if (!this.validFitInput()) return;
    const candidate = {deviceType:this.fitType.trim(),deviceBrand:this.fitBrand.trim(),deviceModel:this.fitModel.trim()};
    if (!this.draft.compatibleDevices.some(f => [f.deviceType,f.deviceBrand,f.deviceModel].every((v,i) =>
        v.toLowerCase() === [candidate.deviceType,candidate.deviceBrand,candidate.deviceModel][i].toLowerCase()))) {
      this.draft.compatibleDevices = [...this.draft.compatibleDevices, candidate];
    }
    this.fitModel = '';
  }
  removeCompatibleDevice(index:number):void {
    this.draft.compatibleDevices = this.draft.compatibleDevices.filter((_,i) => i !== index);
  }
  resetPage():void { this.page.set(1); }
  pageCount():number { return Math.max(1, Math.ceil(this.filteredItems().length / this.pageSize())); }
  setPage(n:number):void { this.page.set(Math.min(this.pageCount(), Math.max(1,n))); }
  setPageSize(n:number):void { this.pageSize.set(Number(n)); this.resetPage(); }
  pageStart():number { return this.filteredItems().length ? (this.page()-1)*this.pageSize()+1 : 0; }
  pageEnd():number { return Math.min(this.filteredItems().length, this.page()*this.pageSize()); }
  pagedItems():ManagedItem[] { return this.filteredItems().slice((this.page()-1)*this.pageSize(), this.page()*this.pageSize()); }

  chooseBulkFile(event:Event):void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] || null;
    this.bulkFile = null;
    this.bulkFileName.set(''); this.bulkPreview.set(null); this.bulkError.set('');
    if (!file) return;
    if (!/\.(csv|xlsx)$/i.test(file.name) || file.size > 2*1024*1024 || file.size === 0) {
      this.bulkError.set('Choose a nonempty CSV or XLSX file under 2 MB.'); input.value=''; return;
    }
    this.bulkFile=file; this.bulkFileName.set(file.name);
  }
  previewBulk():void {
    const id=this.ctx.managedSelected();
    if (!id || !this.canManage() || !this.bulkFile || this.bulkBusy()) return;
    this.bulkBusy.set(true); this.bulkError.set(''); this.bulkPreview.set(null);
    const body=new FormData(); body.append('file',this.bulkFile);
    this.api.post<BulkImportPreview>(`/companies/${id}/items/import/preview`,body).subscribe({
      next:r=>{this.bulkPreview.set(r);this.bulkBusy.set(false);},
      error:e=>{this.bulkError.set(this.explain(e,'Could not read file'));this.bulkBusy.set(false);}
    });
  }
  importBulk():void {
    const id=this.ctx.managedSelected();
    const p=this.bulkPreview();
    if(!id||!this.canManage()||!this.bulkFile||!p||p.errors.length||this.bulkBusy())return;
    const body=new FormData();body.append('file',this.bulkFile);
    this.bulkBusy.set(true);this.bulkError.set('');
    this.api.post<{created:number;message:string}>(`/companies/${id}/items/import`,body).subscribe({
      next:r=>{this.bulkBusy.set(false);this.bulkPreview.set(null);this.bulkFile=null;this.bulkFileName.set('');
        if(this.bulkFileInput) this.bulkFileInput.nativeElement.value='';
        this.message.set(`${r.created} products imported successfully.`);this.resetPage();this.load(id);},
      error:e=>{this.bulkBusy.set(false);this.bulkError.set(this.explain(e,'Import rejected. Preview again.'));}
    });
  }

  filteredItems(): ManagedItem[] {
    const q = this.inventorySearch.trim().toLocaleLowerCase();
    return this.items().filter(item => {
      if (this.itemStatusFilter === 'ACTIVE' && !item.active) return false;
      if (this.itemStatusFilter === 'VISIBLE' && (!item.active || !item.visibleToClients)) return false;
      if (this.itemStatusFilter === 'HIDDEN' && (!item.active || item.visibleToClients)) return false;
      if (this.itemStatusFilter === 'DELETED' && item.active) return false;
      // A product can fit devices beyond its primary type or brand.
      if (!this.fitActive() && this.typeFilter !== 'ALL' && item.deviceType !== this.typeFilter) return false;
      if (!this.fitActive() && this.brandFilter !== 'ALL' && item.deviceBrand !== this.brandFilter) return false;
      if (this.fitActive() && !this.itemVerified(item)) return false;
      if (this.categoryFilter !== 'ALL' && this.itemCategory(item) !== this.categoryFilter) return false;
      if (!q) return true;
      return [item.name,item.sku,item.deviceType,item.deviceBrand,item.category,item.description ?? '', 
        ...item.compatibleDevices.flatMap(c=>[c.deviceType,c.deviceBrand,c.deviceModel])]
        .some(v=>v.toLocaleLowerCase().includes(q));
    });
  }

  constructor() {
    effect(() => {
      // Track ONLY workspace ID and membership role here. clearForm() reads
      // previewUrl() while revoking the old image preview. Without untracked(),
      // choosing a photo retriggers this effect and resets the entire form.
      const companyId = this.ctx.managedSelected();
      const role = this.ctx.role();
      untracked(() => {
        this.clearForm();
        this.restocking.set(null);
        this.bulkFile=null;this.bulkFileName.set('');this.bulkPreview.set(null);this.bulkError.set('');this.resetPage();
        this.items.set([]);this.selectedItem.set(null);
        if (companyId && (role === 'OWNER' || role === 'MANAGER')) this.load(companyId);
      });
    });
  }

  canManage(): boolean {
    return this.ctx.canManage();
  }
  imageSrc(path: string | null): string | null { return itemImageSrc(path, this.photoVersion()); }

  onPhotoChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    // Do not call clearSelectedPhoto() here: it clears the file input itself
    // during its change event. Only revoke the previous object URL.
    const previousPreview = this.previewUrl();
    if (previousPreview) URL.revokeObjectURL(previousPreview);
    this.previewUrl.set(null);
    this.selectedPhoto = null;
    if (!file) return;
    if (file.size === 0 || file.size > 20 * 1024 * 1024 ||
        !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.error.set('Choose a JPEG, PNG, or WebP image under 20 MB.');
      input.value = '';
      return;
    }
    this.error.set('');
    this.selectedPhoto = file;
    this.previewUrl.set(URL.createObjectURL(file));
  }

  private clearSelectedPhoto(): void {
    if (this.previewUrl()) URL.revokeObjectURL(this.previewUrl()!);
    this.previewUrl.set(null);
    this.selectedPhoto = null;
    if (this.fileInput) this.fileInput.nativeElement.value = '';
  }
  ngOnDestroy(): void { this.clearSelectedPhoto(); }

  removePhoto(): void {
    const companyId = this.ctx.managedSelected(), itemId = this.editingId();
    if (!companyId || itemId === null || !this.canManage() || this.saving()) return;
    this.saving.set(true);
    this.api.delete<void>(`/companies/${companyId}/items/${itemId}/image`).subscribe({
      next: () => {
        this.saving.set(false); this.editingPhoto.set(null);
        this.photoVersion.set(Date.now());
        this.message.set('Photo removed.'); this.load(companyId);
      },
      error: () => { this.saving.set(false); this.error.set('Unable to remove item photo.'); }
    });
  }

  currency(): string { return this.ctx.managedCompany()?.currencyCode || 'EUR'; }
  validPrices(): boolean {
    return this.isMoney(this.draft.costPrice) && this.isMoney(this.draft.sellingPrice);
  }
  profitPerUnit(): number {
    return Math.round(((this.draft.sellingPrice ?? 0) - (this.draft.costPrice ?? 0)) * 100) / 100;
  }
  marginPercent(): number {
    const price = this.draft.sellingPrice ?? 0;
    return price > 0 ? (this.profitPerUnit() / price) * 100 : 0;
  }
  private isMoney(value: number | null): boolean {
    return typeof value === 'number' && Number.isFinite(value) && value >= 0 &&
      value <= 9999999999.99 && Math.abs(Math.round(value * 100) - value * 100) < 1e-6;
  }
  validDraft(): boolean {
    return !!this.draft.sku.trim() && !!this.draft.name.trim() && isDeviceTypeValue(this.draft.deviceType) &&
      !!this.draft.category.trim() && this.draft.category.length<=80 &&
      !!this.draft.deviceBrand.trim() && this.draft.deviceType.length<=100 && this.draft.deviceBrand.length<=100 && this.validPrices() &&
      (this.editingId() !== null || this.isNonNegativeInt(this.draft.openingStock));
  }
  private isNonNegativeInt(value: number | null): boolean {
    return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 2147483647;
  }
  validRestock(): boolean {
    return this.isNonNegativeInt(this.stockQuantity) && (this.stockQuantity ?? 0) > 0;
  }
  load(companyId = this.ctx.managedSelected()): void {
    if (!companyId || !this.canManage()) return;
    this.loading.set(true);
    this.api.get<ManagedItem[]>(`/companies/${companyId}/inventory/items`).subscribe({
      next: data => {
        if (this.ctx.managedSelected() === companyId) { this.items.set(data);this.setPage(this.page()); }
        this.loading.set(false);
      },
      error: err => { this.error.set(this.explain(err, 'Could not load inventory')); this.loading.set(false); }
    });
  }
  save(): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId || !this.canManage() || !this.validDraft() || this.saving()) return;
    this.error.set(''); this.message.set(''); this.saving.set(true);
    const payload = {
      sku: this.draft.sku.trim().toUpperCase(),
      name: this.draft.name.trim(),
      description: this.draft.description.trim(),
      category: this.draft.category.trim().slice(0,80),
      deviceType: this.draft.deviceType.trim(),
      deviceBrand: this.draft.deviceBrand.trim(),
      compatibleDevices: this.draft.compatibleDevices,
      costPrice: this.draft.costPrice,
      sellingPrice: this.draft.sellingPrice,
    };
    const itemId = this.editingId();
    const request = itemId === null
      ? this.api.post<ManagedItem>(`/companies/${companyId}/items`, { ...payload, openingStock: this.draft.openingStock })
      : this.api.put<ManagedItem>(`/companies/${companyId}/items/${itemId}`, payload);
    request.subscribe({
      next: item => {
        const successMessage = `${item.name} ${itemId === null ? 'added to the store' : 'updated'} successfully.`;
        const photo = this.selectedPhoto;
        if (!photo) {
          this.saving.set(false);
          this.message.set(successMessage);
          this.clearForm();
          this.load(companyId);
          return;
        }
        const body = new FormData();
        body.append('file', photo, photo.name);
        this.api.post<ManagedItem>(`/companies/${companyId}/items/${item.id}/image`, body).subscribe({
          next: () => {
            this.saving.set(false); this.photoVersion.set(Date.now());
            this.message.set(successMessage + ' Photo uploaded.');
            this.clearForm(); this.load(companyId);
          },
          error: err => {
            this.saving.set(false);
            // Item creation has succeeded; switch to edit mode so retrying the photo
            // cannot accidentally create another product with the same SKU.
            this.editingId.set(item.id);
            this.editingPhoto.set(item.imageUrl);
            this.error.set(successMessage + ' However, photo upload failed. Retry saving this item to upload the photo. ' +
              this.explain(err, 'Please try again.'));
            this.load(companyId);
          }
        });
      },
      error: err => { this.saving.set(false); this.error.set(this.explain(err, 'Unable to save item')); }
    });
  }
  edit(item: ManagedItem): void {
    this.error.set(''); this.message.set(''); this.restocking.set(null);
    this.editingId.set(item.id);
    this.clearSelectedPhoto();
    this.editingPhoto.set(item.imageUrl);
    this.fitType = item.deviceType; this.fitBrand = item.deviceBrand; this.fitModel = '';
    this.draft = {
      sku: item.sku, name: item.name, description: item.description ?? '', category: item.category || 'General',
      deviceType: item.deviceType || item.category || 'General', deviceBrand: item.deviceBrand || 'Universal',
      compatibleDevices: [...(item.compatibleDevices || [])],
      costPrice: item.costPrice, sellingPrice: item.sellingPrice, openingStock: null
    };
  }
  clearForm(): void {
    this.editingId.set(null);
    this.editingPhoto.set(null);
    this.clearSelectedPhoto();
    this.draft = blankForm();
    this.fitType = this.draft.deviceType;
    this.fitBrand = this.draft.deviceBrand;
    this.fitModel = '';
  }
  beginRestock(item: ManagedItem): void {
    this.error.set(''); this.message.set(''); this.restocking.set(item);
    this.stockQuantity = 1; this.stockNote = '';
  }
  restock(): void {
    const companyId = this.ctx.managedSelected(), item = this.restocking();
    if (!companyId || !item || !this.validRestock() || this.savingStock()) return;
    this.error.set(''); this.savingStock.set(true);
    this.api.post(`/companies/${companyId}/inventory/items/${item.id}/movements`, {
      movementType: 'RESTOCK', quantityChange: this.stockQuantity,
      note: this.stockNote.trim() || 'Restock through inventory screen'
    }).subscribe({
      next: () => {
        this.savingStock.set(false);
        this.restocking.set(null);
        this.message.set(`Stock updated for ${item.name}.`);
        this.load(companyId);
      },
      error: err => { this.savingStock.set(false); this.error.set(this.explain(err, 'Unable to update stock')); }
    });
  }
  changeVisibility(item: ManagedItem): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId || !this.canManage() || !item.active || this.busyItemId() !== null) return;
    const visible = !item.visibleToClients;
    this.error.set(''); this.message.set(''); this.busyItemId.set(item.id);
    this.api.patch<ManagedItem>(`/companies/${companyId}/items/${item.id}/visibility`,
      {visibleToClients:visible}).subscribe({
      next: updated => {
        this.busyItemId.set(null);
        this.items.update(current => current.map(x => x.id === updated.id ? updated : x));
        this.message.set(`${item.name} is now ${visible ? 'visible in' : 'hidden from'} the client store.`);
        this.setPage(this.page());
      },
      error: err => { this.busyItemId.set(null); this.error.set(this.explain(err, 'Unable to change item visibility')); }
    });
  }

  deleteItem(item: ManagedItem): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId || !this.canManage() || !item.active || this.busyItemId() !== null) return;
    if (!window.confirm(`Delete ${item.name} (${item.sku}) from active inventory?\n\n` +
      `It will disappear from the client store and current inventory, but past orders and repair records will be kept. You can restore it later.`)) return;
    this.error.set(''); this.message.set(''); this.busyItemId.set(item.id);
    this.api.delete<void>(`/companies/${companyId}/items/${item.id}`).subscribe({
      next: () => {
        this.busyItemId.set(null);
        if (this.editingId() === item.id) this.clearForm();
        if (this.restocking()?.id === item.id) this.restocking.set(null);
        this.message.set(`${item.name} deleted from active inventory. Use the Deleted filter to restore it.`);
        this.load(companyId);
      },
      error: err => { this.busyItemId.set(null); this.error.set(this.explain(err, 'Unable to delete item')); }
    });
  }

  restoreItem(item: ManagedItem): void {
    const companyId = this.ctx.managedSelected();
    if (!companyId || !this.canManage() || item.active || this.busyItemId() !== null) return;
    this.error.set(''); this.message.set(''); this.busyItemId.set(item.id);
    this.api.post<ManagedItem>(`/companies/${companyId}/items/${item.id}/restore`, {}).subscribe({
      next: () => {
        this.busyItemId.set(null);
        this.message.set(`${item.name} restored to active inventory, hidden from clients until you publish it.`);
        this.load(companyId);
      },
      error: err => { this.busyItemId.set(null); this.error.set(this.explain(err, 'Unable to restore item')); }
    });
  }

  private explain(error: unknown, fallback: string): string {
    const err = error as HttpErrorResponse;
    if (err?.status === 409) return 'Conflict: the SKU may already exist, or stock has changed. Refresh and try again.';
    if (err?.status === 403) return 'You do not have permission to modify this company inventory.';
    if (err?.status === 400) return 'Please check the item details or the selected image size.';
    if (err?.status === 413) return 'Photo is too large (max 5 MB).';
    if (err?.status === 415) return 'Unsupported image format. Use JPEG, PNG or WebP.';
    return fallback;
  }

}
