import { HttpClient, provideHttpClient, withXsrfConfiguration, withXhr } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './auth.service';
import { Controller } from './controller';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withXhr(), 
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      })
    ),
    provideAppInitializer(() => {
      const controller = inject(Controller);
      // Load config first (doesn't require authentication)
      return controller.loadConfig();
    }),
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      // Convert Observable to Promise so Angular waits for initialization
      return firstValueFrom(authService.initialize());
    }),
  ]
};
