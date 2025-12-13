import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

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
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Redirect to authorize page (which will redirect to signin)
  router.navigate(['/authorize']);
  return false;
};
