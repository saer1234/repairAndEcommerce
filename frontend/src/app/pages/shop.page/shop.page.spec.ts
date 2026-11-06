import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShopPage } from './shop.page';

describe('ShopPage', () => {
  let component: ShopPage;
  let fixture: ComponentFixture<ShopPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShopPage]
    })
      .compileComponents();

    fixture = TestBed.createComponent(ShopPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
