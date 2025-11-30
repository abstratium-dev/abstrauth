import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'setRouteBeforeSignIn']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  it('should allow access when user is authenticated', () => {
    authService.isAuthenticated.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() => 
      authGuard({} as any, { url: '/clients' } as any)
    );

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should deny access and redirect when user is not authenticated', () => {
    authService.isAuthenticated.and.returnValue(false);

    const result = TestBed.runInInjectionContext(() => 
      authGuard({} as any, { url: '/clients' } as any)
    );

    expect(result).toBe(false);
    expect(authService.setRouteBeforeSignIn).toHaveBeenCalledWith('/clients');
    expect(router.navigate).toHaveBeenCalledWith(['/authorize']);
  });

  it('should store the attempted URL before redirecting', () => {
    authService.isAuthenticated.and.returnValue(false);

    TestBed.runInInjectionContext(() => 
      authGuard({} as any, { url: '/user/123' } as any)
    );

    expect(authService.setRouteBeforeSignIn).toHaveBeenCalledWith('/user/123');
  });
});
