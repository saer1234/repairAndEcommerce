import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
import { itemImageSrc } from '../../../shared/utils/item-image';
import { CompatibleDevice } from '../../../core/models/model';
import { DecimalPipe } from '@angular/common';

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
  effectivePrice: number;
  discountPercent: number;
  currencyCode: string;
  inStock?: boolean;
  stockQuantity?: number;
  costPrice?: number;
  potentialGrossProfitPerUnit?: number;
  active?: boolean;
  visibleToClients?: boolean;
}

@Component({
  imports: [DecimalPipe],
  selector: 'app-item-detail',
  styleUrl: './item-detail.component.css',
  templateUrl: './item-detail.component.html',
})
export class ItemDetailComponent implements AfterViewInit{
  @Input({ required: true }) item!: ItemDetailsItem;
  @Input() showInternal = false;
  @Output() close = new EventEmitter<void>();
  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;
  imageSrc = itemImageSrc;

  ngAfterViewInit(): void { this.closeButton?.nativeElement.focus(); }
  @HostListener('document:keydown.escape') handleEscape(): void { this.close.emit(); }
}
