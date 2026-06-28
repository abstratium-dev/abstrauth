import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
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
      'signout', 'getLastOrgId', 'setLastOrgId'
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
        provideHttpClient(withXhr()),
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

});
