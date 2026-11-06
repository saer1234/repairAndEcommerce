import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestHello } from './test-hello';

describe('TestHello', () => {
  let component: TestHello;
  let fixture: ComponentFixture<TestHello>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHello]
    })
      .compileComponents();

    fixture = TestBed.createComponent(TestHello);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
