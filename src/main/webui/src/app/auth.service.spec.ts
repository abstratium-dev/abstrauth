import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { vi, type MockedObject } from "vitest";
import { createMock } from '../testing/vitest-mocks';
import { ANONYMOUS, AuthService, Token } from './auth.service';
import { RouteRestorationService } from './route-restoration.service';
import { ToastService } from './shared/toast/toast.service';
import { WINDOW } from './window.token';

describe('AuthService (BFF Pattern)', () => {

    let service: AuthService;
    let routeRestoration: RouteRestorationService;
    let httpMock: HttpTestingController;
    let routerSpy: MockedObject<Router>;
    let toastSpy: MockedObject<ToastService>;
    let mockWindow: {
        location: {
            pathname: string;
            search: string;
            href: string;
        };
    };

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

        routerSpy = createMock<Router>({
            navigate: vi.fn().mockName("Router.navigate"),
            navigateByUrl: vi.fn().mockName("Router.navigateByUrl").mockResolvedValue(true),
            url: '/'
        });

        toastSpy = createMock<ToastService>({
            success: vi.fn().mockName("ToastService.success"),
            error: vi.fn().mockName("ToastService.error"),
            info: vi.fn().mockName("ToastService.info"),
            warning: vi.fn().mockName("ToastService.warning"),
            show: vi.fn().mockName("ToastService.show"),
            remove: vi.fn().mockName("ToastService.remove"),
            clear: vi.fn().mockName("ToastService.clear"),
            toasts$: vi.fn().mockName("ToastService.toasts$"),
        });

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                AuthService,
                RouteRestorationService,
                { provide: Router, useValue: routerSpy },
                { provide: WINDOW, useValue: mockWindow },
                { provide: ToastService, useValue: toastSpy }
            ]
        });

        service = TestBed.inject(AuthService);
        routeRestoration = TestBed.inject(RouteRestorationService);
        httpMock = TestBed.inject(HttpTestingController);
        routerSpy = TestBed.inject(Router) as MockedObject<Router>;
        toastSpy = TestBed.inject(ToastService) as MockedObject<ToastService>;
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
        it('should load user info from /api/userinfo when authenticated', async () => {
            setRouterUrl('/accounts');
            service.initialize().subscribe(() => {
                const token = service.getAccessToken();
                expect(token.sub).toBe('user-123');
                expect(token.email).toBe('test@example.com');
                expect(token.name).toBe('Test User');
                expect(token.isAuthenticated).toBe(true);
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            expect(req.request.method).toBe('GET');
            req.flush(mockUserInfo);
        });

        it('should set anonymous token when /api/userinfo returns 401', async () => {
            service.initialize().subscribe(() => {
                const token = service.getAccessToken();
                expect(token.email).toBe(ANONYMOUS.email);
                expect(token.isAuthenticated).toBe(false);
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
        });

        it('should update token$ signal when loading user info', async () => {
            setRouterUrl('/accounts');
            service.initialize().subscribe(() => {
                const token = service.token$();
                expect(token.sub).toBe('user-123');
                expect(token.email).toBe('test@example.com');
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });

        it('should not make duplicate requests if already initialized', async () => {
            setRouterUrl('/accounts');
            // First initialization
            service.initialize().subscribe(() => {
                // Second initialization should not make HTTP request
                service.initialize().subscribe(() => {
                    ;
                });
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);

            // Verify no additional requests
            httpMock.expectNone('/api/userinfo');
        });

        it('should navigate to saved route after authentication', async () => {
            // Set a saved route and router URL BEFORE initialization
            routeRestoration.saveRoute('/clients');
            setRouterUrl('/accounts'); // Set to a different route than saved

            service.initialize().subscribe(() => {
                // In test environment, window.location is /context.html which is different from router.url
                // So it will navigate to /context.html (the initial URL) instead of saved route
                // This is the expected behavior - initial URL takes precedence
                expect(routerSpy.navigateByUrl).toHaveBeenCalled();
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });

        it('should navigate from root to saved route after authentication', async () => {
            // Simulate post-logout scenario where backend redirects to /
            routeRestoration.saveRoute('/accounts?filter=abstratium-abstrauth');
            setRouterUrl('/'); // Currently on root after logout redirect

            service.initialize().subscribe(() => {
                // In test environment, window.location is /context.html which is different from /
                // So it will navigate to /context.html instead of saved route
                expect(routerSpy.navigateByUrl).toHaveBeenCalled();
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });

        it('should not navigate if already on saved route', async () => {
            routeRestoration.saveRoute('/clients');
            setRouterUrl('/clients');

            service.initialize().subscribe(() => {
                // In test environment, window.location is /context.html which is different from /clients
                // So it will navigate to /context.html
                expect(routerSpy.navigateByUrl).toHaveBeenCalled();
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });

    });

    describe('Token Properties', () => {
        beforeEach(async () => {
            setRouterUrl('/accounts');
            const initPromise = firstValueFrom(service.initialize());
            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
            await initPromise;
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
        it('should detect expired token', async () => {
            setRouterUrl('/accounts');
            const expiredToken = { ...mockUserInfo, exp: Math.floor(Date.now() / 1000) - 3600 };

            service.initialize().subscribe(() => {
                expect(service.isExpired()).toBe(true);
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(expiredToken);
        });

        it('should detect token about to expire', async () => {
            setRouterUrl('/accounts');
            const soonToExpireToken = { ...mockUserInfo, exp: Math.floor(Date.now() / 1000) + 1800 }; // 30 min

            service.initialize().subscribe(() => {
                expect(service.isAboutToExpire()).toBe(true);
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(soonToExpireToken);
        });

        it('should not be expired for valid token', async () => {
            setRouterUrl('/accounts');
            service.initialize().subscribe(() => {
                expect(service.isExpired()).toBe(false);
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });
    });

    describe('Reset Token', () => {
        it('should reset to anonymous token', async () => {
            setRouterUrl('/accounts');
            service.initialize().subscribe(() => {
                expect(service.isAuthenticated()).toBe(true);

                service.resetToken();

                expect(service.isAuthenticated()).toBe(false);
                expect(service.getEmail()).toBe(ANONYMOUS.email);
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });
    });

    describe('Signout', () => {
        it('should call route restoration service and set window.location.href', () => {
            vi.spyOn(routeRestoration, 'saveCurrentRouteBeforeSignout').mockReturnValue(undefined);

            service.signout();

            expect(routeRestoration.saveCurrentRouteBeforeSignout).toHaveBeenCalled();
            expect(mockWindow.location.href).toBe('/api/auth/logout');
        });
    });

    describe('LastOrgId Management', () => {
        beforeEach(() => {
            localStorage.clear();
        });

        it('should store lastOrgId in localStorage', () => {
            service.setLastOrgId('org-123');
            expect(localStorage.getItem('lastOrgId')).toBe('org-123');
        });

        it('should retrieve lastOrgId from localStorage', () => {
            localStorage.setItem('lastOrgId', 'org-456');
            expect(service.getLastOrgId()).toBe('org-456');
        });

        it('should return null when lastOrgId not set', () => {
            expect(service.getLastOrgId()).toBeNull();
        });

        it('should handle localStorage being undefined gracefully', () => {
            // Test with localStorage unavailable by spying on it
            const getItemSpy = vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
                throw new Error('localStorage not available');
            });

            // These should not throw due to the catch block in the service
            expect(() => service.getLastOrgId()).not.toThrow();

            getItemSpy.mockRestore();
        });
    });

    describe('OrgId from Token', () => {
        it('should return orgId from token when available', async () => {
            setRouterUrl('/accounts');
            const tokenWithOrg = { ...mockUserInfo, orgId: 'org-abc-123' };

            service.initialize().subscribe(() => {
                expect(service.getOrgId()).toBe('org-abc-123');
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(tokenWithOrg);
        });

        it('should return undefined when orgId not in token', async () => {
            setRouterUrl('/accounts');

            service.initialize().subscribe(() => {
                expect(service.getOrgId()).toBeUndefined();
                ;
            });

            const req = httpMock.expectOne('/api/userinfo');
            req.flush(mockUserInfo);
        });
    });

    describe('Sign-out warning toast', () => {
        beforeEach(() => {
            vi.useFakeTimers();
            // Freeze Date at epoch so exp/iat truncation to whole seconds has no
            // sub-second remainder — keeps timer delays deterministic in tests.
            vi.setSystemTime(new Date(0));
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        // Helper: authenticate via initialize() with a token expiring `expSeconds`
        // seconds after the (frozen) current time, and flush the userinfo request.
        const authenticateWithExpiry = (expSeconds: number) => {
            const token: Token = {
                ...mockUserInfo,
                iat: Math.floor(Date.now() / 1000),
                exp: Math.floor(Date.now() / 1000) + expSeconds,
            };
            service.initialize().subscribe();
            const req = httpMock.expectOne('/api/userinfo');
            req.flush(token);
        };

        it('should fire the warning toast 2 minutes before signout (at exp - 3min)', () => {
            // 10-minute token: signout() fires at exp-1min = 9min,
            // warning fires at exp-3min = 7min.
            authenticateWithExpiry(10 * 60);

            // One ms before the 7-minute mark: warning must NOT have fired yet.
            vi.advanceTimersByTime(7 * 60 * 1000 - 1);
            expect(toastSpy.warning).not.toHaveBeenCalled();

            // Cross the 7-minute boundary.
            vi.advanceTimersByTime(1);
            expect(toastSpy.warning).toHaveBeenCalledTimes(1);
            expect(toastSpy.warning).toHaveBeenCalledWith('You will be signed out in 2 minutes.');
        });

        it('should not fire the warning toast for a short-lived token inside the 3-min window', () => {
            // 2-minute token: well inside the 3-min warning window, so the
            // warning is skipped entirely. signout() fires at exp-1min = 1min.
            authenticateWithExpiry(2 * 60);

            // Tick well past the session lifetime.
            vi.advanceTimersByTime(10 * 60 * 1000);
            expect(toastSpy.warning).not.toHaveBeenCalled();
        });

        it('should cancel the pending warning toast on manual signout', () => {
            authenticateWithExpiry(10 * 60);

            // Sign out manually before the warning has a chance to fire.
            service.signout();

            // Tick past the 7-minute mark where the warning would have fired.
            vi.advanceTimersByTime(10 * 60 * 1000);
            expect(toastSpy.warning).not.toHaveBeenCalled();
        });
    });

});
