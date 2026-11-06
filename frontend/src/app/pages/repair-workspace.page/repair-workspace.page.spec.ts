import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepairWorkspacePage } from './repair-workspace.page';

describe('RepairWorkspacePage', () => {
  let component: RepairWorkspacePage;
  let fixture: ComponentFixture<RepairWorkspacePage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepairWorkspacePage]
    })
      .compileComponents();

    fixture = TestBed.createComponent(RepairWorkspacePage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
