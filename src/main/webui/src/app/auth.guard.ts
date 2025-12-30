import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';
import { RouteRestorationService } from './route-restoration.service';

/**
 * Authentication guard that protects routes requiring user authentication.
 * 
 * If the user is not authenticated, they are redirected to the /authorize page
 * and the attempted URL is stored for post-login redirection.
 * 
 * Apply this guard to routes that should only be accessible to authenticated users.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const routeRestoration = inject(RouteRestorationService);
  const router = inject(Router);

  console.debug('[AUTH GUARD] Checking authentication for route:', state.url);
  
  if (authService.isAuthenticated()) {
    console.debug('[AUTH GUARD] User is authenticated, allowing access');
    return true;
  }

  console.debug('[AUTH GUARD] User is NOT authenticated, saving attempted URL and redirecting');
  // Save the attempted URL for post-login redirection
  routeRestoration.saveRoute(state.url);
  
  // Redirect to authorize page (which will redirect to signin)
  router.navigate(['/authorize']);
  return false;
};
