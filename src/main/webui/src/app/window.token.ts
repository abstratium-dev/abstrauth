import { InjectionToken } from '@angular/core';

/**
 * Injection token for the Window object.
 * This allows us to inject and mock window in tests.
 */
export const WINDOW = new InjectionToken<Window>('Window', {
  providedIn: 'root',
  factory: () => window
});
