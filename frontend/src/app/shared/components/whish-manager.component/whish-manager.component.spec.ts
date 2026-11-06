import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WhishManagerComponent } from './whish-manager.component';

describe('WhishManagerComponent', () => {
  let component: WhishManagerComponent;
  let fixture: ComponentFixture<WhishManagerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WhishManagerComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(WhishManagerComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
