import { HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * HTTP Interceptor that adds JWT Bearer token to API requests.
 * 
 * Only adds the Authorization header to:
 * - Requests starting with /api/
 * - When a JWT token is available
 * 
 * The standard format is: Authorization: Bearer <JWT>
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  
  // Only add token to requests starting with /api/
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  
  // Only add token if user is authenticated
  if (!authService.isAuthenticated()) {
    return next(req);
  }
  
  // Get the JWT token
  const jwt = authService.getJwt();
  
  // If no token is available, proceed without modification
  if (!jwt) {
    return next(req);
  }
  
  // Clone the request and add the Authorization header
  const authReq = req.clone({
    setHeaders: {
      Authorization: `Bearer ${jwt}`
    }
  });
  
  return next(authReq);
};
