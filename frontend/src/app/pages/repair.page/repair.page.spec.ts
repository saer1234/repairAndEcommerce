import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepairPage } from './repair.page';

describe('RepairPage', () => {
  let component: RepairPage;
  let fixture: ComponentFixture<RepairPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepairPage]
    })
      .compileComponents();

    fixture = TestBed.createComponent(RepairPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
