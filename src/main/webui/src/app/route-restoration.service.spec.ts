import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouteRestorationService } from './route-restoration.service';
import { WINDOW } from './window.token';

describe('RouteRestorationService', () => {
  let service: RouteRestorationService;
  let routerSpy: jasmine.SpyObj<Router>;
  let mockWindow: { location: { pathname: string; search: string; href: string } };

  beforeEach(() => {
    // Create router spy
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl'], { url: '/' });
    routerSpy.navigateByUrl.and.returnValue(Promise.resolve(true));

    // Create mock window
    mockWindow = {
      location: {
        pathname: '/accounts',
        search: '',
        href: ''
      }
    };

    TestBed.configureTestingModule({
      providers: [
        RouteRestorationService,
        { provide: Router, useValue: routerSpy },
        { provide: WINDOW, useValue: mockWindow }
      ]
    });

    service = TestBed.inject(RouteRestorationService);
    
    // Clear localStorage before each test
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  // Helper to set router URL
  const setRouterUrl = (url: string) => {
    Object.defineProperty(routerSpy, 'url', {
      value: url,
      writable: true,
      configurable: true
    });
  };

  describe('Route Saving', () => {
    it('should save a valid route', () => {
      service.saveRoute('/clients');
      expect(service.getSavedRoute()).toBe('/clients');
    });

    it('should save route with query parameters', () => {
      service.saveRoute('/accounts?filter=abstratium-abstrauth');
      expect(service.getSavedRoute()).toBe('/accounts?filter=abstratium-abstrauth');
    });

    it('should not save ignored route - signin', () => {
      service.saveRoute('/signin/abc123');
      expect(service.getSavedRoute()).toBe('/accounts'); // default route
    });

    it('should not save ignored route - signup', () => {
      service.saveRoute('/signup');
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should not save ignored route - authorize', () => {
      service.saveRoute('/authorize');
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should not save ignored route - root path', () => {
      service.saveRoute('/');
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should not save routes with OAuth state parameter', () => {
      service.saveRoute('/?state=a99573bc-75e3-436d-8f60-3370e1ec65e6');
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should not save routes with state in query string', () => {
      service.saveRoute('/authorize?client_id=test&state=abc123');
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should overwrite previously saved route', () => {
      service.saveRoute('/clients');
      expect(service.getSavedRoute()).toBe('/clients');
      service.saveRoute('/accounts');
      expect(service.getSavedRoute()).toBe('/accounts');
    });
  });

  describe('Route Retrieval', () => {
    it('should return saved route', () => {
      service.saveRoute('/clients');
      expect(service.getSavedRoute()).toBe('/clients');
    });

    it('should return default route when nothing saved', () => {
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should return default route after clearing', () => {
      service.saveRoute('/clients');
      service.clearSavedRoute();
      expect(service.getSavedRoute()).toBe('/accounts');
    });
  });

  describe('Target Route Determination', () => {
    it('should return saved route when on root', () => {
      service.saveRoute('/clients');
      setRouterUrl('/');
      
      const target = service.determineTargetRoute('/');
      expect(target).toBe('/clients');
    });

    it('should return initial URL when manually entered with query params', () => {
      service.saveRoute('/clients'); // Previously saved
      setRouterUrl('/');
      
      const target = service.determineTargetRoute('/accounts?filter=test');
      expect(target).toBe('/accounts?filter=test');
    });

    it('should return null when already on target route', () => {
      service.saveRoute('/clients');
      setRouterUrl('/clients');
      
      const target = service.determineTargetRoute('/clients');
      expect(target).toBeNull();
    });

    it('should return null when on ignored route', () => {
      service.saveRoute('/clients');
      setRouterUrl('/signin/abc123');
      
      const target = service.determineTargetRoute('/signin/abc123');
      expect(target).toBeNull();
    });

    it('should prefer initial URL over saved route', () => {
      service.saveRoute('/clients');
      setRouterUrl('/');
      
      const target = service.determineTargetRoute('/accounts?filter=abstratium-abstrauth');
      expect(target).toBe('/accounts?filter=abstratium-abstrauth');
    });

    it('should use saved route when initial URL is ignored', () => {
      service.saveRoute('/clients');
      setRouterUrl('/');
      
      const target = service.determineTargetRoute('/?state=abc123');
      expect(target).toBe('/clients');
    });

    it('should navigate from root to saved route after logout', () => {
      service.saveRoute('/accounts?filter=abstratium-abstrauth');
      setRouterUrl('/');
      
      const target = service.determineTargetRoute('/');
      expect(target).toBe('/accounts?filter=abstratium-abstrauth');
    });
  });

  describe('Navigation', () => {
    it('should navigate to target route', async () => {
      const success = await service.navigateToTarget('/clients');
      
      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/clients');
      expect(success).toBe(true);
    });

    it('should navigate and clear saved route when requested', async () => {
      service.saveRoute('/clients');
      
      await service.navigateToTarget('/clients', true);
      
      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/clients');
      expect(service.getSavedRoute()).toBe('/accounts'); // cleared, returns default
    });

    it('should not clear saved route by default', async () => {
      service.saveRoute('/clients');
      
      await service.navigateToTarget('/clients');
      
      expect(service.getSavedRoute()).toBe('/clients'); // not cleared
    });

    it('should handle navigation failure', async () => {
      routerSpy.navigateByUrl.and.returnValue(Promise.resolve(false));
      
      const success = await service.navigateToTarget('/clients');
      
      expect(success).toBe(false);
    });

    it('should handle navigation error', async () => {
      routerSpy.navigateByUrl.and.returnValue(Promise.reject(new Error('Navigation failed')));
      
      const success = await service.navigateToTarget('/clients');
      
      expect(success).toBe(false);
    });
  });

  describe('Clear Saved Route', () => {
    it('should clear saved route', () => {
      service.saveRoute('/clients');
      expect(service.getSavedRoute()).toBe('/clients');
      
      service.clearSavedRoute();
      expect(service.getSavedRoute()).toBe('/accounts');
    });

    it('should handle clearing when nothing saved', () => {
      expect(() => service.clearSavedRoute()).not.toThrow();
      expect(service.getSavedRoute()).toBe('/accounts');
    });
  });
});
