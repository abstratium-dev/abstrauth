import { inject, Injectable } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Injectable({
  providedIn: 'root',
})
export class DomainService {
  readonly isAbstratiumDomain: boolean;

  constructor() {
    const hostname = inject(DOCUMENT).location.hostname;
    this.isAbstratiumDomain =
      hostname.endsWith('.abstratium.dev') ||
      hostname === 'localhost';
  }
}
