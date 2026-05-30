import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';

import { HeaderComponent } from './header.component';
import { AuthService, Token, ANONYMOUS } from '../auth.service';
import { Controller } from '../controller';
import { ThemeService } from '../theme.service';
import { signal, WritableSignal } from '@angular/core';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let themeServiceMock: { theme$: jasmine.Spy };
  let tokenSignal: WritableSignal<Token>;

  const mockOrg = {
    id: 'org-123',
    name: 'Test Organisation',
    createdByAccountId: 'account-456',
    createdAt: '2024-01-15T10:00:00Z'
  };

  const mockTokenWithOrg: Token = {
    ...ANONYMOUS,
    sub: 'user-123',
    email: 'test@example.com',
    name: 'Test User',
    isAuthenticated: true,
    orgId: 'org-123'
  };

  beforeEach(async () => {
    // Create a writable signal that can be updated
    tokenSignal = signal<Token>(ANONYMOUS);

    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'signout', 'clearLastOrgId', 'getLastOrgId', 'setLastOrgId'
    ], {
      token$: tokenSignal
    });

    // Create a Subject to simulate router events
    const routerEventsSubject = new Subject();
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree', 'serializeUrl'], {
      events: routerEventsSubject.asObservable(),
      url: '/'
    });
    // Mock createUrlTree to return a minimal UrlTree-like object
    routerSpy.createUrlTree.and.returnValue({
      root: { segments: [], children: {}, hasChildren: false, numberOfChildren: 0 },
      queryParams: {},
      fragment: null,
      queryParamMap: { get: () => null, getAll: () => [], has: () => false }
    } as any);
    routerSpy.serializeUrl.and.returnValue('/');

    themeServiceMock = {
      theme$: jasmine.createSpy('theme$').and.returnValue('light')
    };

    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ThemeService, useValue: themeServiceMock },
        { provide: ActivatedRoute, useValue: {} },
        Controller
      ]
    })
    .compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Mock the /public/config request that's called in ngOnInit
    const configReq = httpMock.expectOne('/public/config');
    configReq.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 900 });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Organisation Display', () => {
    it('should load current organisation when user is signed in with orgId', fakeAsync(() => {
      // Update the token signal to simulate signed in user with org
      tokenSignal.set(mockTokenWithOrg);

      // Trigger effect by running change detection
      fixture.detectChanges();
      tick();

      // Expect the HTTP request for current organisation
      const orgReq = httpMock.expectOne('/api/organisations/current');
      expect(orgReq.request.method).toBe('GET');
      orgReq.flush(mockOrg);

      expect(component.currentOrg).toEqual(mockOrg);
      expect(component.isLoadingOrg).toBeFalse();
    }));

    it('should not load organisation when user is not signed in', () => {
      // Token is already anonymous (not signed in)
      fixture.detectChanges();

      // Should not make any organisation request
      httpMock.expectNone('/api/organisations/current');
      expect(component.currentOrg).toBeNull();
    });

    it('should not load organisation when token has no orgId', fakeAsync(() => {
      const tokenWithoutOrg: Token = {
        ...ANONYMOUS,
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        isAuthenticated: true
        // no orgId
      };
      tokenSignal.set(tokenWithoutOrg);

      fixture.detectChanges();
      tick();

      httpMock.expectNone('/api/organisations/current');
      expect(component.currentOrg).toBeNull();
    }));

    it('should handle error when loading organisation fails', fakeAsync(() => {
      tokenSignal.set(mockTokenWithOrg);

      fixture.detectChanges();
      tick();

      const orgReq = httpMock.expectOne('/api/organisations/current');
      orgReq.flush({ error: 'Organisation not found' }, { status: 404, statusText: 'Not Found' });

      expect(component.currentOrg).toBeNull();
      expect(component.isLoadingOrg).toBeFalse();
      expect(component.orgError).toBe('Organisation not found');
    }));

    it('should display organisation name when loaded', fakeAsync(() => {
      tokenSignal.set(mockTokenWithOrg);

      fixture.detectChanges();
      tick();

      const orgReq = httpMock.expectOne('/api/organisations/current');
      orgReq.flush(mockOrg);

      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Test Organisation');
      expect(compiled.textContent).toContain('Organisation:');
    }));
  });

  describe('Switch Organisation', () => {
    it('should clear lastOrgId and sign out when switching organisation', fakeAsync(() => {
      tokenSignal.set(mockTokenWithOrg);

      fixture.detectChanges();
      tick();

      const orgReq = httpMock.expectOne('/api/organisations/current');
      orgReq.flush(mockOrg);

      component.switchOrganisation();

      expect(authServiceSpy.clearLastOrgId).toHaveBeenCalled();
      expect(authServiceSpy.signout).toHaveBeenCalled();
    }));
  });

  describe('Create New Organisation', () => {
    it('should navigate to user page when creating new organisation', fakeAsync(() => {
      tokenSignal.set(mockTokenWithOrg);

      fixture.detectChanges();
      tick();

      const orgReq = httpMock.expectOne('/api/organisations/current');
      orgReq.flush(mockOrg);

      component.createNewOrganisation();

      expect(routerSpy.navigate).toHaveBeenCalledWith(['/user']);
    }));
  });

  describe('Loading State', () => {
    it('should show loading state while fetching organisation', fakeAsync(() => {
      tokenSignal.set(mockTokenWithOrg);

      fixture.detectChanges();
      tick();

      // Don't flush the request yet - should be in loading state
      const orgReq = httpMock.match('/api/organisations/current');
      expect(orgReq.length).toBe(1);

      expect(component.isLoadingOrg).toBeTrue();
    }));
  });
});
