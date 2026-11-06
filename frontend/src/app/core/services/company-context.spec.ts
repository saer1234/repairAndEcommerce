import { TestBed } from '@angular/core/testing';
import { CompanyContext } from './company-context';

describe('CompanyContext', () => {
  let service: CompanyContext;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CompanyContext);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
