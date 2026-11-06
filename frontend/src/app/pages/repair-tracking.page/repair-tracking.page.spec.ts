import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepairTrackingPage } from './repair-tracking.page';

describe('RepairTrackingPage', () => {
  let component: RepairTrackingPage;
  let fixture: ComponentFixture<RepairTrackingPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepairTrackingPage]
    })
      .compileComponents();

    fixture = TestBed.createComponent(RepairTrackingPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
