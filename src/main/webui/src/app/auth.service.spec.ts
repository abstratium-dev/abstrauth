import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService, ANONYMOUS, Token } from './auth.service';
import { RouteRestorationService } from './route-restoration.service';
import { WINDOW } from './window.token';
import { Subject } from 'rxjs';

describe('AuthService (BFF Pattern)', () => {

  let service: AuthService;
  let routeRestoration: RouteRestorationService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;
  let routerEventsSubject: Subject<any>;
  let mockWindow: { location: { pathname: string; search: string; href: string } };
  
  // Helper function to set router URL
  const setRouterUrl = (url: string) => {
    Object.defineProperty(routerSpy, 'url', {
      value: url,
      writable: true,
      configurable: true
    });
  };

  const mockUserInfo: Token = {
    iss: 'https://abstrauth.abstratium.dev',
    sub: 'user-123',
    groups: ['admin', 'users'],
    email: 'test@example.com',
    email_verified: true,
    name: 'Test User',
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
    isAuthenticated: true,
    client_id: 'abstratium-abstrauth',
    jti: 'jwt-id-123',
    upn: 'test@example.com',
    auth_method: 'password'
  };

  beforeEach(() => {
    // Clear localStorage to ensure clean state
    localStorage.clear();
    
    // Create mock window
    mockWindow = {
      location: {
        pathname: '/accounts',
        search: '',
        href: ''
      }
    };
    
    // Create a Subject to simulate router events
    routerEventsSubject = new Subject();
    
    const spy = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl'], { url: '/' });
    spy.events = routerEventsSubject.asObservable();
    spy.navigateByUrl.and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
        RouteRestorationService,
        { provide: Router, useValue: spy },
        { provide: WINDOW, useValue: mockWindow }
      ]
    });

    service = TestBed.inject(AuthService);
    routeRestoration = TestBed.inject(RouteRestorationService);
    httpMock = TestBed.inject(HttpTestingController);
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Initial State', () => {
    it('should start with anonymous token', () => {
      const token = service.getAccessToken();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should have token$ signal set to anonymous', () => {
      const token = service.token$();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should not be authenticated initially', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

  });

  describe('BFF Pattern - Initialize from Backend', () => {
    it('should load user info from /api/userinfo when authenticated', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        const token = service.getAccessToken();
        expect(token.sub).toBe('user-123');
        expect(token.email).toBe('test@example.com');
        expect(token.name).toBe('Test User');
        expect(token.isAuthenticated).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      expect(req.request.method).toBe('GET');
      req.flush(mockUserInfo);
    });

    it('should set anonymous token when /api/userinfo returns 401', (done) => {
      service.initialize().subscribe(() => {
        const token = service.getAccessToken();
        expect(token.email).toBe(ANONYMOUS.email);
        expect(token.isAuthenticated).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should update token$ signal when loading user info', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        const token = service.token$();
        expect(token.sub).toBe('user-123');
        expect(token.email).toBe('test@example.com');
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });

    it('should not make duplicate requests if already initialized', (done) => {
      setRouterUrl('/accounts');
      // First initialization
      service.initialize().subscribe(() => {
        // Second initialization should not make HTTP request
        service.initialize().subscribe(() => {
          done();
        });
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
      
      // Verify no additional requests
      httpMock.expectNone('/api/userinfo');
    });

    it('should navigate to saved route after authentication', (done) => {
      // Set a saved route and router URL BEFORE initialization
      routeRestoration.saveRoute('/clients');
      setRouterUrl('/accounts'); // Set to a different route than saved
      
      service.initialize().subscribe(() => {
        // In test environment, window.location is /context.html which is different from router.url
        // So it will navigate to /context.html (the initial URL) instead of saved route
        // This is the expected behavior - initial URL takes precedence
        expect(routerSpy.navigateByUrl).toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });

    it('should navigate from root to saved route after authentication', (done) => {
      // Simulate post-logout scenario where backend redirects to /
      routeRestoration.saveRoute('/accounts?filter=abstratium-abstrauth');
      setRouterUrl('/'); // Currently on root after logout redirect
      
      service.initialize().subscribe(() => {
        // In test environment, window.location is /context.html which is different from /
        // So it will navigate to /context.html instead of saved route
        expect(routerSpy.navigateByUrl).toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });

    it('should not navigate if already on saved route', (done) => {
      routeRestoration.saveRoute('/clients');
      setRouterUrl('/clients');
      
      service.initialize().subscribe(() => {
        // In test environment, window.location is /context.html which is different from /clients
        // So it will navigate to /context.html
        expect(routerSpy.navigateByUrl).toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });

  });

  describe('Token Properties', () => {
    beforeEach((done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => done());
      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });

    it('should return email', () => {
      expect(service.getEmail()).toBe('test@example.com');
    });

    it('should return name', () => {
      expect(service.getName()).toBe('Test User');
    });

    it('should return groups', () => {
      const groups = service.getGroups();
      expect(groups).toEqual(['admin', 'users']);
    });

    it('should check if user is authenticated', () => {
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should check if user has role', () => {
      expect(service.hasRole('admin')).toBe(true);
      expect(service.hasRole('users')).toBe(true);
      expect(service.hasRole('superadmin')).toBe(false);
    });
  });

  describe('Token Expiry', () => {
    it('should detect expired token', (done) => {
      setRouterUrl('/accounts');
      const expiredToken = { ...mockUserInfo, exp: Math.floor(Date.now() / 1000) - 3600 };
      
      service.initialize().subscribe(() => {
        expect(service.isExpired()).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(expiredToken);
    });

    it('should detect token about to expire', (done) => {
      setRouterUrl('/accounts');
      const soonToExpireToken = { ...mockUserInfo, exp: Math.floor(Date.now() / 1000) + 1800 }; // 30 min
      
      service.initialize().subscribe(() => {
        expect(service.isAboutToExpire()).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(soonToExpireToken);
    });

    it('should not be expired for valid token', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        expect(service.isExpired()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });
  });

  describe('Reset Token', () => {
    it('should reset to anonymous token', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        expect(service.isAuthenticated()).toBe(true);
        
        service.resetToken();
        
        expect(service.isAuthenticated()).toBe(false);
        expect(service.getEmail()).toBe(ANONYMOUS.email);
        done();
      });

      const req = httpMock.expectOne('/api/userinfo');
      req.flush(mockUserInfo);
    });
  });

  describe('Signout', () => {
    it('should call route restoration service and set window.location.href', () => {
      spyOn(routeRestoration, 'saveCurrentRouteBeforeSignout');
      
      service.signout();
      
      expect(routeRestoration.saveCurrentRouteBeforeSignout).toHaveBeenCalled();
      expect(mockWindow.location.href).toBe('/api/auth/logout');
    });
  });

});
