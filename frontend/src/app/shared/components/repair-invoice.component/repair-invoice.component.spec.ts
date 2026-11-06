import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepairInvoiceComponent } from './repair-invoice.component';

describe('RepairInvoiceComponent', () => {
  let component: RepairInvoiceComponent;
  let fixture: ComponentFixture<RepairInvoiceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepairInvoiceComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(RepairInvoiceComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
