import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';
import { clientShopGuard } from './client-shop-guard';

describe('clientShopGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) => 
      TestBed.runInInjectionContext(() => clientShopGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
