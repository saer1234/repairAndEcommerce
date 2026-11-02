import { Component, effect, inject, signal, untracked, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { CompanyContext } from './company-context';
import { itemImageSrc } from './item-image';
import { ItemDetailsComponent } from './item-details.component';
import { CompatibleDevice } from './models';
import { isDeviceTypeValue, componentCategory, componentCategories } from './device-classification';
import { matchesVerifiedFit, verifiedFitSelected, verifiedFitTypes, verifiedFitBrands, verifiedFitModels } from './verified-fit';

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
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink, ItemDetailsComponent],
  template: `
    <section class="page-heading">
      <div>
        <h1>Store inventory</h1>
        <p class="muted">Create products and manage prices and stock for {{ ctx.managedCompany()?.name || 'your company' }}.</p>
      </div>
      <a routerLink="/shop" class="button-link secondary">View customer store →</a>
    </section>

    @if (!ctx.managedSelected()) {
      <p class="muted">Create a company or select your workspace on the <a routerLink="/companies">Companies page</a>.</p>
    } @else if (!canManage()) {
      <p class="error" role="alert">Only the company owner or manager can add or edit store items. Only your own company workspace can be managed.</p>
    } @else {
      @if (message()) { <p class="success" role="status">{{ message() }}</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

      <div class="inventory-layout">
        <section class="panel inventory-editor">
          <div class="panel-heading">
            <div>
              <h3>{{ editingId() === null ? 'Add a new item' : 'Edit item #' + editingId() }}</h3>
              <p class="muted tiny">Only the selling price is shown to customers.</p>
            </div>
            @if (editingId() !== null) {
              <button class="secondary" type="button" (click)="clearForm()">Cancel edit</button>
            }
          </div>

          <form #itemForm="ngForm" (ngSubmit)="save()" class="item-form">
            <label>SKU / product code *
              <input name="sku" [(ngModel)]="draft.sku" maxlength="90" required placeholder="SCREEN-001" />
            </label>
            <label>Item name *
              <input name="name" [(ngModel)]="draft.name" maxlength="180" required placeholder="Samsung screen replacement" />
            </label>
            <label>Description
              <textarea name="description" [(ngModel)]="draft.description" maxlength="2000" placeholder="Compatible models, color, specifications"></textarea>
            </label>
            <div class="field-pair">
              <label>Device type *
                <input name="deviceType" [(ngModel)]="draft.deviceType" required maxlength="100" list="deviceTypeSuggestions" placeholder="e.g. Phone, Tablet, Laptop" />
                <datalist id="deviceTypeSuggestions"><option value="Phone"></option><option value="Tablet"></option><option value="Laptop"></option><option value="Desktop"></option><option value="Game console"></option><option value="Other"></option></datalist>
              </label>
              <label>Primary device brand *
                <input name="deviceBrand" [(ngModel)]="draft.deviceBrand" required maxlength="100" list="deviceBrandSuggestions" placeholder="e.g. Samsung, Apple, Universal" />
                <datalist id="deviceBrandSuggestions"><option value="Samsung"></option><option value="Apple"></option><option value="Xiaomi"></option><option value="Huawei"></option><option value="Lenovo"></option><option value="Universal"></option></datalist>
              </label>
            </div>
            <label>Component category *
              <input name="category" [(ngModel)]="draft.category" required maxlength="80"
                     list="partCategorySuggestions" placeholder="Screen, Battery, Charging Port, ..." />
              <datalist id="partCategorySuggestions">
                <option value="Screen"></option><option value="Battery"></option>
                <option value="Charging Port"></option><option value="Camera Module"></option>
                <option value="Speaker"></option><option value="Microphone"></option>
                <option value="Back Cover"></option><option value="Other"></option>
              </datalist>
            </label>
            <div class="repair-action-box" aria-label="Device compatibility">
              <h4>Compatible devices</h4>
              <p class="muted tiny">A single stock item can work with several models or brands. Add each supported device here. Use * for any model in the selected brand (only when confirmed compatible). Empty means not verified.</p>
              <div class="field-pair">
                <label>Compatible device type
                  <input name="fitType" [(ngModel)]="fitType" list="deviceTypeSuggestions" maxlength="100" placeholder="Phone" />
                </label>
                <label>Compatible brand
                  <input name="fitBrand" [(ngModel)]="fitBrand" maxlength="100" placeholder="Samsung" />
                </label>
              </div>
              <label>Compatible model
                <input name="fitModel" [(ngModel)]="fitModel" maxlength="120" placeholder="Galaxy S23, or * for all models" />
              </label>
              <button type="button" class="secondary" (click)="fitType=draft.deviceType;fitBrand=draft.deviceBrand">Use primary type and brand</button>
              <button type="button" class="secondary" (click)="addCompatibleDevice()" [disabled]="!validFitInput()">Add compatible model</button>
              @for (fit of draft.compatibleDevices; track $index) {
                <div class="repair-part-line">
                  <span>{{fit.deviceType}} / {{fit.deviceBrand}} / {{fit.deviceModel}}</span>
                  <button type="button" class="secondary" (click)="removeCompatibleDevice($index)">Remove</button>
                </div>
              }
            </div>
            <div class="photo-upload">
              <label for="itemPhoto">Item photo (JPEG, PNG or WebP, max 5 MB)</label>
              <input #fileInput id="itemPhoto" type="file" accept="image/jpeg,image/png,image/webp" (change)="onPhotoChosen($event)" />
              @if (previewUrl() || editingPhoto()) {
                <div class="photo-preview">
                  <img [src]="previewUrl() || imageSrc(editingPhoto())" alt="Preview of item photo" />
                  <span class="muted tiny">{{ previewUrl() ? 'New photo selected' : 'Current photo' }}</span>
                </div>
              }
              @if (editingId() !== null && editingPhoto() && !previewUrl()) {
                <button class="secondary" type="button" (click)="removePhoto()" [disabled]="saving()">Remove current photo</button>
              }
            </div>
            <div class="field-pair">
              <label>Cost price ({{ currency() }}) *
                <input type="number" name="costPrice" [(ngModel)]="draft.costPrice" required min="0" max="9999999999.99" step="0.01" placeholder="50.00" />
              </label>
              <label>Selling price ({{ currency() }}) *
                <input type="number" name="sellingPrice" [(ngModel)]="draft.sellingPrice" required min="0" max="9999999999.99" step="0.01" placeholder="80.00" />
              </label>
            </div>
            @if (editingId() === null) {
              <label>Opening stock (units) *
                <input type="number" name="openingStock" [(ngModel)]="draft.openingStock" required min="0" max="2147483647" step="1" />
              </label>
            } @else {
              <p class="muted tiny">To change quantity, use Restock below. Price edits do not change historical order prices.</p>
            }
            @if (validPrices()) {
              <div class="margin-preview">
                <span>Potential gross profit / unit</span>
                <strong>{{ profitPerUnit() | number:'1.2-2' }} {{ currency() }}</strong>
                <small class="muted">{{ marginPercent() | number:'1.1-1' }}% gross margin, before other expenses</small>
              </div>
              @if (profitPerUnit() < 0) {
                <p class="error">Selling price is lower than cost price.</p>
              }
            }
            <button class="primary" type="submit" [disabled]="saving() || itemForm.invalid || !validDraft()">
              {{ saving() ? 'Saving…' : editingId() === null ? 'Add item to store' : 'Save changes' }}
            </button>
          </form>
        </section>

        <section class="panel inventory-list">
          <div class="panel-heading">
            <div><h3>Company items</h3><p class="muted tiny">{{ filteredItems().length }} of {{ items().length }} products</p></div>
            <button class="secondary" type="button" (click)="load()" [disabled]="loading()">Refresh</button>
          </div>
          <section class="inventory-import" aria-label="Bulk product import">
            <h4>Import multiple items (Excel or CSV)</h4>
            <p class="muted tiny">Import up to 500 new products per file. Existing SKUs are skipped, never added to stock a second time. Photos can be uploaded separately after import.</p>
            <a href="/assets/inventory_import_template.csv" download="inventory_import_template.csv">Download import template</a>
            <label>Choose CSV or XLSX file (up to 2 MB)
              <input #bulkFileInput type="file" accept=".csv,.xlsx" (change)="chooseBulkFile($event)" />
            </label>
            @if (bulkFileName()) {<p class="muted tiny">Selected: {{bulkFileName()}}</p>}
            <div class="table-actions">
              <button type="button" class="secondary" [disabled]="bulkBusy() || !bulkFile" (click)="previewBulk()">{{bulkBusy()?'Checking…':'Preview and validate'}}</button>
              <button type="button" class="primary" [disabled]="bulkBusy() || !bulkFile || !bulkPreview() || bulkPreview()!.errors.length > 0 || bulkPreview()!.validRows === 0" (click)="importBulk()">Import {{bulkPreview()?.validRows || 0}} items</button>
            </div>
            @if(bulkError()) {<p class="error" role="alert">{{bulkError()}}</p>}
            @if(bulkPreview(); as report) {
              <p class="muted">{{report.totalRows}} rows; {{report.validRows}} new; {{report.skippedExisting}} existing SKUs skipped; {{report.errors.length}} errors.</p>
              @if(report.sampleSkus.length) {<p class="muted tiny">Preview SKUs: {{report.sampleSkus.join(', ')}}</p>}
              @for(issue of report.errors; track $index) {<p class="error">Row {{issue.row}}: {{issue.message}}</p>}
            }
          </section>
          <section class="verified-fit-finder" aria-label="Check exact verified part fit">
            <div class="panel-heading"><div><h4>Search verified fit</h4>
              <p class="muted tiny">Find components explicitly recorded as fitting one device model, including cross-brand and universal-category parts.</p></div>
              <button type="button" class="secondary" (click)="clearVerifiedFit()">Clear fit</button>
            </div>
            <div class="fit-fields">
              <label>Device type
                <select [ngModel]="fitDeviceType" (ngModelChange)="chooseFitType($event)" aria-label="Inventory fit device type">
                  <option value="ALL">Choose type</option>
                  @for(type of fitTypes();track type){<option [value]="type">{{type}}</option>}
                </select>
              </label>
              <label>Device brand
                <select [ngModel]="fitDeviceBrand" (ngModelChange)="chooseFitBrand($event)" [disabled]="fitDeviceType==='ALL'" aria-label="Inventory fit device brand">
                  <option value="ALL">Choose brand</option>
                  @for(brand of fitBrands();track brand){<option [value]="brand">{{brand}}</option>}
                </select>
              </label>
              <label>Exact model
                <input type="search" list="inventory-fit-models" [ngModel]="fitDeviceModel" (ngModelChange)="fitDeviceModel=$event;resetPage()" [disabled]="fitDeviceBrand==='ALL'"
                  placeholder="e.g. iPhone 13" aria-label="Inventory exact model for verified fit" />
                <datalist id="inventory-fit-models">@for(model of fitModels();track model){<option [value]="model"></option>}</datalist>
              </label>
            </div>
            @if(fitActive()){<p class="verified-fit-result" role="status">{{filteredItems().length}} products with a recorded exact fit for {{fitDeviceBrand}} {{fitDeviceModel}}.</p>}
            @else{<p class="muted tiny">Select a type, brand, and exact model to filter. Primary categories and wildcard entries alone do not verify fit.</p>}
          </section>
          <div class="field-pair">
            <label>Component category
              <select [ngModel]="categoryFilter" (ngModelChange)="categoryFilter=$event;resetPage()" aria-label="Filter inventory by component category">
                <option value="ALL">All components</option>
                @for(category of categories();track category){<option [value]="category">{{category}}</option>}
              </select>
            </label>
            <label>Item status
              <select [ngModel]="itemStatusFilter" (ngModelChange)="itemStatusFilter=$event;resetPage()" aria-label="Filter inventory by visibility and deletion">
                <option value="ACTIVE">Active inventory (visible and hidden)</option>
                <option value="VISIBLE">Visible to clients</option>
                <option value="HIDDEN">Hidden from clients</option>
                <option value="DELETED">Deleted / archived</option>
                <option value="ALL">All products</option>
              </select>
            </label>
          </div>
          @if(items().some(item => !isValidDeviceType(item.deviceType))){
            <p class="muted tiny" role="status">Some older items have component names saved as device types. Edit those items to set the device type (e.g. Phone) and component category (e.g. Screen).</p>
          }
          <label>Search inventory
            <input type="search" name="inventorySearch" [ngModel]="inventorySearch" (ngModelChange)="inventorySearch=$event;resetPage()"
                   [ngModelOptions]="{standalone: true}"
                   placeholder="Search by name, SKU, device type, brand or compatible model" aria-label="Search inventory items" />
          </label>
          <div class="field-pair">
            <label>Device type
              <select [ngModel]="typeFilter" (ngModelChange)="typeFilter=$event;brandFilter='ALL';resetPage()"><option value="ALL">All device types</option>
                @for (type of deviceTypes(); track type) {<option [value]="type">{{type}}</option>}
              </select>
            </label>
            <label>Brand
              <select [ngModel]="brandFilter" (ngModelChange)="brandFilter=$event;resetPage()"><option value="ALL">All brands</option>
                @for (brand of deviceBrands(); track brand) {<option [value]="brand">{{brand}}</option>}
              </select>
            </label>
          </div>
          @if (loading()) { <p class="muted">Loading inventory…</p> }
          @if (!loading() && !items().length) {
            <p class="muted">No items yet. Create the first item using the form.</p>
          }
          @if (items().length && !filteredItems().length) {
            <p class="muted" role="status">{{fitActive()?'No item has explicit compatibility for that exact device model. Review your saved compatibility records.':'No matching items. Try another name, SKU or keyword.'}}</p>
          }
          @if (filteredItems().length) {
            <div class="pagination-toolbar" role="group" aria-label="Inventory pagination">
              <span>{{ pageStart() }}–{{ pageEnd() }} of {{ filteredItems().length }} items</span>
              <label>Per page <select [ngModel]="pageSize()" (ngModelChange)="setPageSize($event)" aria-label="Inventory page size"><option [ngValue]="10">10</option><option [ngValue]="20">20</option><option [ngValue]="50">50</option></select></label>
              <div class="pagination-actions"><button type="button" class="secondary" (click)="setPage(page()-1)" [disabled]="page()===1">Previous</button>
              <span>Page {{page()}} of {{pageCount()}}</span>
              <button type="button" class="secondary" (click)="setPage(page()+1)" [disabled]="page()>=pageCount()">Next</button></div>
            </div>
            <div class="table-scroll"><table>
              <thead><tr><th>Item</th><th>Stock</th><th>Cost</th><th>Sale</th><th>Profit / unit</th><th></th></tr></thead>
              <tbody>
                @for (item of pagedItems(); track item.id) {
                  <tr [class.inactive-row]="!item.active">
                    <td><div class="inventory-item-name">
                      @if (item.imageUrl) {
                        <img class="item-thumbnail" [src]="imageSrc(item.imageUrl)" [alt]="item.name" loading="lazy" />
                      } @else { <span class="item-thumbnail item-thumbnail-empty">No photo</span> }
                      <div><strong>{{ item.name }}</strong><div class="muted tiny">{{ item.sku }} · {{ item.deviceType }} → {{ item.deviceBrand }} · {{itemCategory(item)}} · {{item.compatibleDevices.length}} recorded compatibility entries</div>
                      @if(fitActive() && itemVerified(item)){<span class="verified-fit-label">✓ Exact fit recorded: {{fitDeviceBrand}} {{fitDeviceModel}}</span>}</div>
                    </div>
                      @if (!item.active) { <span class="chip">Deleted (archived)</span> }
                      @else if (!item.visibleToClients) { <span class="chip">Hidden from clients</span> }
                      @else { <span class="chip">Visible to clients</span> }
                    </td>
                    <td>{{ item.stockQuantity }}</td>
                    <td>{{ item.costPrice | number:'1.2-2' }}</td>
                    <td>{{ item.sellingPrice | number:'1.2-2' }}</td>
                    <td>{{ item.potentialGrossProfitPerUnit | number:'1.2-2' }}</td>
                    <td>
                      @if (item.active) {
                        <div class="table-actions">
                          <button class="secondary" type="button" (click)="openDetails(item)">Details</button>
                          <button class="secondary" type="button" (click)="edit(item)">Edit</button>
                          <button class="secondary" type="button" (click)="beginRestock(item)">Restock</button>
                          <button class="secondary" type="button" [disabled]="busyItemId()!==null" (click)="changeVisibility(item)">{{item.visibleToClients?'Hide from clients':'Show to clients'}}</button>
                          <button class="secondary" type="button" [disabled]="busyItemId()!==null" (click)="deleteItem(item)">Delete</button>
                        </div>
                      } @else {
                        <div class="table-actions"><button class="secondary" type="button" (click)="openDetails(item)">Details</button><button class="secondary" type="button" [disabled]="busyItemId()!==null" (click)="restoreItem(item)">Restore hidden</button></div>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table></div>
          }
          @if (filteredItems().length) {
            <div class="mobile-inventory-cards">
              @for (item of pagedItems(); track item.id) {
                <article class="mobile-inventory-card" [class.inactive-row]="!item.active">
                  <div class="inventory-item-name">
                    @if(item.imageUrl){<img class="item-thumbnail" [src]="imageSrc(item.imageUrl)" [alt]="item.name" loading="lazy" />}
                    <div><strong>{{item.name}}</strong><p class="muted tiny">{{item.sku}} · {{item.deviceType}} → {{item.deviceBrand}} · {{itemCategory(item)}}</p>
                    <span class="chip">{{!item.active?'Deleted':item.visibleToClients?'Visible to clients':'Hidden from clients'}}</span>
                    @if(fitActive() && itemVerified(item)){<span class="verified-fit-label">✓ Exact fit recorded</span>}</div>
                  </div>
                  <div class="inventory-mobile-stats">
                    <span>Stock <strong>{{item.stockQuantity}}</strong></span>
                    <span>Sale <strong>{{item.sellingPrice | number:'1.2-2'}} {{item.currencyCode}}</strong></span>
                    <span>Cost <strong>{{item.costPrice | number:'1.2-2'}} {{item.currencyCode}}</strong></span>
                  </div>
                  @if(item.active){
                    <div class="table-actions">
                      <button type="button" class="secondary" (click)="openDetails(item)">Details</button>
                      <button type="button" class="secondary" (click)="edit(item)">Edit</button>
                      <button type="button" class="secondary" (click)="beginRestock(item)">Restock</button>
                      <button type="button" class="secondary" [disabled]="busyItemId()!==null" (click)="changeVisibility(item)">{{item.visibleToClients?'Hide':'Show'}}</button>
                      <button type="button" class="secondary" [disabled]="busyItemId()!==null" (click)="deleteItem(item)">Delete</button>
                    </div>
                  } @else {
                    <div class="table-actions"><button type="button" class="secondary" (click)="openDetails(item)">Details</button><button type="button" class="secondary" [disabled]="busyItemId()!==null" (click)="restoreItem(item)">Restore hidden</button></div>
                  }
                </article>
              }
            </div>
          }
          @if (selectedItem(); as item) {
            <app-item-details [item]="item" [showInternal]="true" (close)="closeDetails()" />
          }
          @if (restocking(); as item) {
            <div class="restock-editor">
              <h3>Restock: {{ item.name }}</h3>
              <p class="muted tiny">Current stock: {{ item.stockQuantity }}. A stock movement will be recorded.</p>
              <form #stockForm="ngForm" (ngSubmit)="restock()">
                <label>Units to add *<input type="number" name="quantity" [(ngModel)]="stockQuantity" min="1" max="2147483647" step="1" required /></label>
                <label>Note<input name="stockNote" [(ngModel)]="stockNote" maxlength="255" /></label>
                <div class="table-actions">
                  <button class="primary" type="submit" [disabled]="stockForm.invalid || !validRestock() || savingStock()">{{ savingStock() ? 'Saving…' : 'Add stock' }}</button>
                  <button class="secondary" type="button" (click)="restocking.set(null)">Cancel</button>
                </div>
              </form>
            </div>
          }
        </section>
      </div>
    }
  `
})
export class InventoryPage implements OnDestroy {
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
    if (file.size === 0 || file.size > 5 * 1024 * 1024 ||
        !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.error.set('Choose a JPEG, PNG, or WebP image under 5 MB.');
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
