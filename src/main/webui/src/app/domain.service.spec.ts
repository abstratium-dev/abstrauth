import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { DomainService } from './domain.service';

function buildService(hostname: string): DomainService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: DOCUMENT, useValue: { location: { hostname } } }
    ]
  });
  return TestBed.inject(DomainService);
}

describe('DomainService', () => {
  it('should be true for a subdomain of abstratium.dev', () => {
    expect(buildService('app.abstratium.dev').isAbstratiumDomain).toBeTrue();
  });

  it('should be true for localhost', () => {
    expect(buildService('localhost').isAbstratiumDomain).toBeTrue();
  });

  it('should be false for a foreign domain', () => {
    expect(buildService('evil.example.com').isAbstratiumDomain).toBeFalse();
  });

  it('should be false for a domain that merely ends with abstratium.dev but is not a subdomain', () => {
    expect(buildService('notabstratium.dev').isAbstratiumDomain).toBeFalse();
  });
});
