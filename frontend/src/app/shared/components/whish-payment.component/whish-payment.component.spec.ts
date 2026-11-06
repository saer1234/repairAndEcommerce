import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WhishPaymentComponent } from './whish-payment.component';

describe('WhishPaymentComponent', () => {
  let component: WhishPaymentComponent;
  let fixture: ComponentFixture<WhishPaymentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WhishPaymentComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(WhishPaymentComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
