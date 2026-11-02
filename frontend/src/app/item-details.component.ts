import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { CompatibleDevice } from './models';
import { itemImageSrc } from './item-image';

/** Shared read-only product detail view. Internal fields are optional and only passed by inventory management. */
export interface ItemDetailsItem {
  id: number;
  name: string;
  sku: string;
  description: string | null;
  category: string;
  deviceType: string;
  deviceBrand: string;
  compatibleDevices: CompatibleDevice[];
  imageUrl: string | null;
  sellingPrice: number;
  currencyCode: string;
  inStock?: boolean;
  stockQuantity?: number;
  costPrice?: number;
  potentialGrossProfitPerUnit?: number;
  active?: boolean;
  visibleToClients?: boolean;
}

@Component({
  selector: 'app-item-details',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="item-detail-backdrop">
      <section class="item-detail-dialog" role="dialog" aria-modal="true" [attr.aria-labelledby]="'item-detail-title-' + item.id" aria-describedby="item-detail-description">
        <header class="item-detail-header">
          <div>
            <p class="muted tiny">SKU: {{ item.sku }}</p>
            <h2 [id]="'item-detail-title-' + item.id">{{ item.name }}</h2>
          </div>
          <button #closeButton type="button" class="secondary" (click)="close.emit()" aria-label="Close product details">Close ✕</button>
        </header>
        <div class="item-detail-main">
          <div class="item-detail-photo">
            @if (item.imageUrl) {
              <img [src]="imageSrc(item.imageUrl)!" [alt]="item.name" />
            } @else {
              <p class="muted">No product photo available</p>
            }
          </div>
          <div class="item-detail-information">
            <div class="item-detail-tags">
              <span class="chip">{{ item.deviceType || 'Unspecified type' }}</span>
              <span class="chip">{{ item.deviceBrand || 'Unspecified brand' }}</span>
              <span class="chip">{{ item.category || 'General' }}</span>
            </div>
            <h3>Description</h3>
            <p id="item-detail-description" class="item-detail-description">{{ item.description?.trim() || 'No description added yet.' }}</p>
            <h3>Device compatibility</h3>
            @if (item.compatibleDevices?.length) {
              <ul class="item-detail-fits">
                @for (fit of item.compatibleDevices; track $index) {
                  <li>{{ fit.deviceType }} / {{ fit.deviceBrand }} / {{ fit.deviceModel }}</li>
                }
              </ul>
              <p class="muted tiny">Compatibility is based on shop records; confirm the part number before installation.</p>
            } @else {
              <p class="muted">No verified compatibility recorded.</p>
            }
            <div class="item-detail-price">
              <span>Selling price</span>
              <strong>{{ item.sellingPrice | number:'1.2-2' }} {{ item.currencyCode }}</strong>
            </div>
            @if (showInternal) {
              <div class="item-detail-internal" aria-label="Internal inventory information">
                <h3>Internal information — owners/managers only</h3>
                <dl>
                  <div><dt>Acquisition cost</dt><dd>{{ item.costPrice | number:'1.2-2' }} {{ item.currencyCode }}</dd></div>
                  <div><dt>Potential gross profit per unit</dt><dd>{{ item.potentialGrossProfitPerUnit | number:'1.2-2' }} {{ item.currencyCode }}</dd></div>
                  <div><dt>Quantity on hand</dt><dd>{{ item.stockQuantity ?? 0 }}</dd></div>
                  <div><dt>Store visibility</dt><dd>{{ item.active === false ? 'Archived' : item.visibleToClients ? 'Visible to clients' : 'Hidden from clients' }}</dd></div>
                </dl>
                <p class="muted tiny">Potential gross profit excludes operating expenses, fees, and taxes.</p>
              </div>
            } @else {
              <p class="muted">{{ item.inStock ? 'Available to order' : 'Out of stock' }}</p>
            }
          </div>
        </div>
      </section>
    </div>
  `
})
export class ItemDetailsComponent implements AfterViewInit {
  @Input({ required: true }) item!: ItemDetailsItem;
  @Input() showInternal = false;
  @Output() close = new EventEmitter<void>();
  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;
  imageSrc = itemImageSrc;

  ngAfterViewInit(): void { this.closeButton?.nativeElement.focus(); }
  @HostListener('document:keydown.escape') handleEscape(): void { this.close.emit(); }
}
