import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { WINDOW } from './window.token';

/**
 * Service responsible for preserving and restoring routes during authentication flows.
 * 
 * This service handles:
 * - Saving the current route before sign-out
 * - Saving attempted routes when user is not authenticated
 * - Restoring routes after successful authentication
 * - Filtering out OAuth-related routes that should not be saved
 */
@Injectable({
  providedIn: 'root'
})
export class RouteRestorationService {
  private router = inject(Router);
  private window = inject(WINDOW);

  private readonly LOCALSTORAGE_KEY = 'routeBeforeSignIn';
  private readonly defaultRoute = '/accounts';
  private readonly ignoredRoutePrefixes = ['/signin', '/signup', '/authorize'];
  private readonly ignoredRoutes = ['/signout', '/'];

  /**
   * Save a route to be restored later.
   * Ignores OAuth flow routes and other special routes.
   */
  saveRoute(route: string): void {
    console.debug('[ROUTE RESTORATION] saveRoute called with:', route);
    
    if (this.shouldIgnoreRoute(route)) {
      console.debug('[ROUTE RESTORATION] Route is ignored, not saving:', route);
      return;
    }
    
    console.debug('[ROUTE RESTORATION] Saving route to localStorage:', route);
    localStorage.setItem(this.LOCALSTORAGE_KEY, route);
  }

  /**
   * Get the saved route, or return the default route if none is saved.
   */
  getSavedRoute(): string {
    const savedRoute = localStorage.getItem(this.LOCALSTORAGE_KEY) || this.defaultRoute;
    console.debug('[ROUTE RESTORATION] getSavedRoute returning:', savedRoute);
    return savedRoute;
  }

  /**
   * Clear the saved route from storage.
   */
  clearSavedRoute(): void {
    console.debug('[ROUTE RESTORATION] Clearing saved route from localStorage');
    localStorage.removeItem(this.LOCALSTORAGE_KEY);
  }

  /**
   * Determine which route to navigate to after authentication.
   * Prefers the initial URL (for manual entry) over saved route (for post-logout).
   * 
   * @param initialUrl The URL captured from window.location at initialization
   * @returns The target route to navigate to, or null if no navigation needed
   */
  determineTargetRoute(initialUrl: string): string | null {
    const savedRoute = this.getSavedRoute();
    const currentRoute = this.router.url;
    
    console.debug('[ROUTE RESTORATION] determineTargetRoute - initialUrl:', initialUrl, 'savedRoute:', savedRoute, 'currentRoute:', currentRoute);
    
    // Prefer initial URL if it's different from current route and not ignored
    // This handles the case where user manually enters a URL with query params
    let targetRoute = savedRoute;
    if (initialUrl !== currentRoute && !this.shouldIgnoreRoute(initialUrl)) {
      console.debug('[ROUTE RESTORATION] Using initial URL as target (user manually entered URL)');
      targetRoute = initialUrl;
    } else {
      console.debug('[ROUTE RESTORATION] Using saved route as target');
    }
    
    // Determine if we should navigate
    const isOnRoot = currentRoute === '/';
    const isIgnored = this.shouldIgnoreRoute(currentRoute);
    const shouldNavigate = currentRoute !== targetRoute && (isOnRoot || !isIgnored);
    
    console.debug('[ROUTE RESTORATION] Navigation decision - isOnRoot:', isOnRoot, 'isIgnored:', isIgnored, 'shouldNavigate:', shouldNavigate);
    
    if (shouldNavigate) {
      return targetRoute;
    }
    
    console.debug('[ROUTE RESTORATION] No navigation needed');
    return null;
  }

  /**
   * Navigate to the target route and optionally clear the saved route.
   * 
   * @param targetRoute The route to navigate to
   * @param clearSaved Whether to clear the saved route after navigation
   */
  async navigateToTarget(targetRoute: string, clearSaved: boolean = false): Promise<boolean> {
    console.debug('[ROUTE RESTORATION] Navigating to:', targetRoute);
    
    try {
      const success = await this.router.navigateByUrl(targetRoute);
      console.debug('[ROUTE RESTORATION] Navigation result:', success);
      
      if (success && clearSaved) {
        this.clearSavedRoute();
      }
      
      return success;
    } catch (err) {
      console.error('[ROUTE RESTORATION] Navigation failed:', err);
      return false;
    }
  }

  /**
   * Handle post-authentication navigation.
   * Checks for password change requirement and email mismatch before normal navigation.
   * 
   * @param initialUrl The URL captured from window.location at initialization
   * @param authenticatedEmail The email of the authenticated user
   */
  handlePostAuthenticationNavigation(initialUrl: string, authenticatedEmail: string): void {
    console.debug('[ROUTE RESTORATION] handlePostAuthenticationNavigation called with initialUrl:', initialUrl);
    
    // Check if password change is required (for native invite flow)
    const requirePasswordChange = sessionStorage.getItem('requirePasswordChange');
    if (requirePasswordChange) {
      console.debug('[ROUTE RESTORATION] Password change required, navigating to /change-password');
      this.router.navigate(['/change-password']);
      return;
    }

    // Check for email mismatch with invite
    const inviteDataStr = sessionStorage.getItem('inviteData');
    if (inviteDataStr) {
      try {
        const inviteData = JSON.parse(inviteDataStr);
        
        if (inviteData.email && authenticatedEmail && inviteData.email !== authenticatedEmail) {
          console.warn(
            `[ROUTE RESTORATION] Email mismatch: Invite was for '${inviteData.email}' but authenticated as '${authenticatedEmail}'`
          );
          
          // Store mismatch info and navigate to home with error state
          sessionStorage.setItem('emailMismatchWarning', 
            `You signed in with ${authenticatedEmail}, but the invite was for ${inviteData.email}. The invitation has not been applied.`
          );
          sessionStorage.removeItem('inviteData');
          
          // Navigate to home page where the warning will be displayed
          this.router.navigate(['/']);
          return;
        }
        
        // Emails match - clean up invite data
        sessionStorage.removeItem('inviteData');
        sessionStorage.removeItem('requirePasswordChange');
      } catch (err) {
        console.error('[ROUTE RESTORATION] Error parsing invite data:', err);
        sessionStorage.removeItem('inviteData');
      }
    }
    
    // Normal navigation flow
    const targetRoute = this.determineTargetRoute(initialUrl);
    const savedRoute = this.getSavedRoute();
    
    if (targetRoute) {
      console.debug('[ROUTE RESTORATION] Navigating to target route:', targetRoute);
      // Clear saved route only if we're using it (not if using initial URL)
      const shouldClear = (targetRoute === savedRoute);
      this.navigateToTarget(targetRoute, shouldClear);
    } else {
      console.debug('[ROUTE RESTORATION] No navigation needed');
    }
  }

  /**
   * Save the current route before signing out.
   * Captures the current URL from window.location.
   */
  saveCurrentRouteBeforeSignout(): void {
    const currentRoute = this.window.location.pathname + this.window.location.search;
    console.debug('[ROUTE RESTORATION] Saving current route before signout:', currentRoute);
    this.saveRoute(currentRoute);
  }

  /**
   * Check if a route should be ignored (not saved for restoration).
   * 
   * Ignored routes include:
   * - OAuth flow routes (with state parameter)
   * - Sign-in/sign-up pages
   * - Authorization pages
   * - Root path
   */
  private shouldIgnoreRoute(route: string): boolean {
    // Check exact matches
    if (this.ignoredRoutes.includes(route)) {
      return true;
    }
    
    // Check prefix matches
    if (this.ignoredRoutePrefixes.some(prefix => route.startsWith(prefix))) {
      return true;
    }
    
    // Ignore routes with OAuth state parameter (part of OAuth flow)
    if (route.includes('?state=') || route.includes('&state=')) {
      return true;
    }
    
    return false;
  }
}
