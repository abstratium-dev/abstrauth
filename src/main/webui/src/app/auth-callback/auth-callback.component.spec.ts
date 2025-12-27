import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthCallbackComponent } from './auth-callback.component';
import { AuthService } from '../auth.service';

describe('AuthCallbackComponent', () => {
  let component: AuthCallbackComponent;
  let fixture: ComponentFixture<AuthCallbackComponent>;
  let mockRouter: jasmine.SpyObj<Router>;
  let mockAuthService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);
    mockAuthService = jasmine.createSpyObj('AuthService', ['setAccessToken', 'getRouteBeforeSignIn']);
    mockAuthService.getRouteBeforeSignIn.and.returnValue('/');

    await TestBed.configureTestingModule({
      imports: [AuthCallbackComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: mockAuthService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => {
                  if (key === 'code') return 'test-code';
                  if (key === 'state') return 'test-state'; // Add state to prevent HTTP call
                  if (key === 'error') return null;
                  if (key === 'error_description') return null;
                  return null;
                }
              }
            }
          }
        }
      ]
    })
    .compileComponents();

    // Setup sessionStorage with matching state
    sessionStorage.setItem('state', 'test-state');
    sessionStorage.setItem('code_verifier', 'test-verifier');

    fixture = TestBed.createComponent(AuthCallbackComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });


  afterEach(() => {
    // Clean up sessionStorage after each test
    sessionStorage.clear();
  });
});

describe('AuthCallbackComponent - State Validation', () => {
  it('should reject callback with missing state parameter', async () => {
    // Setup: store state in sessionStorage
    sessionStorage.setItem('state', 'expected-state');
    
    await TestBed.configureTestingModule({
      imports: [AuthCallbackComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) },
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['setAccessToken', 'getRouteBeforeSignIn']) },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => {
                  if (key === 'code') return 'test-code';
                  if (key === 'state') return null; // Missing state
                  if (key === 'error') return null;
                  if (key === 'error_description') return null;
                  return null;
                }
              }
            }
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Trigger ngOnInit
    component.ngOnInit();

    // Should set error message
    expect(component.error).toContain('Invalid state parameter');
    expect(component.error).toContain('CSRF attack');
    
    // Should clear sessionStorage
    expect(sessionStorage.getItem('state')).toBeNull();
    expect(sessionStorage.getItem('code_verifier')).toBeNull();
    
    sessionStorage.clear();
  });

  it('should reject callback with mismatched state parameter', async () => {
    // Setup: store expected state
    sessionStorage.setItem('state', 'expected-state');
    sessionStorage.setItem('code_verifier', 'test-verifier');
    
    await TestBed.configureTestingModule({
      imports: [AuthCallbackComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) },
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['setAccessToken', 'getRouteBeforeSignIn']) },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => {
                  if (key === 'code') return 'test-code';
                  if (key === 'state') return 'wrong-state'; // Mismatched state
                  if (key === 'error') return null;
                  if (key === 'error_description') return null;
                  return null;
                }
              }
            }
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Trigger ngOnInit
    component.ngOnInit();

    // Should set error message
    expect(component.error).toContain('Invalid state parameter');
    expect(component.error).toContain('CSRF attack');
    
    // Should clear sessionStorage
    expect(sessionStorage.getItem('state')).toBeNull();
    expect(sessionStorage.getItem('code_verifier')).toBeNull();
    
    sessionStorage.clear();
  });

  it('should accept callback with valid state parameter', async () => {
    // Setup: store matching state
    const validState = 'valid-state-123';
    sessionStorage.setItem('state', validState);
    sessionStorage.setItem('code_verifier', 'test-verifier');
    
    await TestBed.configureTestingModule({
      imports: [AuthCallbackComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) },
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['setAccessToken', 'getRouteBeforeSignIn']) },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => {
                  if (key === 'code') return 'test-code';
                  if (key === 'state') return validState; // Matching state
                  if (key === 'error') return null;
                  if (key === 'error_description') return null;
                  return null;
                }
              }
            }
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Trigger ngOnInit
    component.ngOnInit();

    // Should NOT set error message
    expect(component.error).toBeFalsy();
    
    // Should clear state from sessionStorage
    expect(sessionStorage.getItem('state')).toBeNull();
    
    sessionStorage.clear();
  });
});

describe('AuthCallbackComponent - Email Mismatch Warning', () => {
  let httpTestingController: HttpTestingController;
  let mockRouter: jasmine.SpyObj<Router>;
  let mockAuthService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    mockRouter = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl']);
    mockAuthService = jasmine.createSpyObj('AuthService', ['setAccessToken', 'getRouteBeforeSignIn', 'getEmail']);
    mockAuthService.getRouteBeforeSignIn.and.returnValue('');

    await TestBed.configureTestingModule({
      imports: [AuthCallbackComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: mockAuthService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => {
                  if (key === 'code') return 'test-code';
                  if (key === 'state') return 'test-state';
                  if (key === 'error') return null;
                  if (key === 'error_description') return null;
                  return null;
                }
              }
            }
          }
        }
      ]
    }).compileComponents();

    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    sessionStorage.clear();
  });

  it('should show warning for 10 seconds when email mismatch occurs', fakeAsync(() => {
    // Setup: store matching state and invite data with different email
    sessionStorage.setItem('state', 'test-state');
    sessionStorage.setItem('code_verifier', 'test-verifier');
    sessionStorage.setItem('inviteData', JSON.stringify({ email: 'invited@example.com' }));

    // Mock the token email to be different
    mockAuthService.getEmail.and.returnValue('different@example.com');

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Trigger ngOnInit
    component.ngOnInit();

    // Respond to the token exchange request
    const req = httpTestingController.expectOne('/oauth2/token');
    req.flush({
      access_token: 'test-token',
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: null,
      scope: 'openid'
    });

    // Should set warning message
    expect(component.emailMismatchWarning).toContain('different@example.com');
    expect(component.emailMismatchWarning).toContain('invited@example.com');
    expect(component.redirecting).toBe(true);

    // Should NOT redirect immediately
    expect(mockRouter.navigate).not.toHaveBeenCalled();
    expect(mockRouter.navigateByUrl).not.toHaveBeenCalled();

    // Advance time by 10 seconds
    tick(10000);

    // Should redirect after 10 seconds
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/accounts']);

    // Invite data should be cleared
    expect(sessionStorage.getItem('inviteData')).toBeNull();
  }));

  it('should redirect immediately when emails match', fakeAsync(() => {
    // Setup: store matching state and invite data with same email
    sessionStorage.setItem('state', 'test-state');
    sessionStorage.setItem('code_verifier', 'test-verifier');
    sessionStorage.setItem('inviteData', JSON.stringify({ email: 'user@example.com' }));

    // Mock the token email to be the same
    mockAuthService.getEmail.and.returnValue('user@example.com');

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Trigger ngOnInit
    component.ngOnInit();

    // Respond to the token exchange request
    const req = httpTestingController.expectOne('/oauth2/token');
    req.flush({
      access_token: 'test-token',
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: null,
      scope: 'openid'
    });

    // Advance any pending timers
    tick();

    // Should NOT set warning message
    expect(component.emailMismatchWarning).toBeNull();
    expect(component.redirecting).toBe(false);

    // Should redirect immediately
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/accounts']);

    // Invite data should be cleared
    expect(sessionStorage.getItem('inviteData')).toBeNull();
  }));

  it('should redirect immediately when no invite data exists', fakeAsync(() => {
    // Setup: store matching state but NO invite data
    sessionStorage.setItem('state', 'test-state');
    sessionStorage.setItem('code_verifier', 'test-verifier');

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Trigger ngOnInit
    component.ngOnInit();

    // Respond to the token exchange request
    const req = httpTestingController.expectOne('/oauth2/token');
    req.flush({
      access_token: 'test-token',
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: null,
      scope: 'openid'
    });

    // Advance any pending timers
    tick();

    // Should NOT set warning message
    expect(component.emailMismatchWarning).toBeNull();
    expect(component.redirecting).toBe(false);

    // Should redirect immediately
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/accounts']);
  }));

  it('should redirect immediately when invite data parsing fails', fakeAsync(() => {
    // Setup: store matching state and invalid invite data
    sessionStorage.setItem('state', 'test-state');
    sessionStorage.setItem('code_verifier', 'test-verifier');
    sessionStorage.setItem('inviteData', 'invalid-json');

    const fixture = TestBed.createComponent(AuthCallbackComponent);
    const component = fixture.componentInstance;

    // Spy on console.error to verify error logging
    spyOn(console, 'error');

    // Trigger ngOnInit
    component.ngOnInit();

    // Respond to the token exchange request
    const req = httpTestingController.expectOne('/oauth2/token');
    req.flush({
      access_token: 'test-token',
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: null,
      scope: 'openid'
    });

    // Advance any pending timers
    tick();

    // Should log error
    expect(console.error).toHaveBeenCalledWith('Error parsing invite data:', jasmine.any(Error));

    // Should NOT set warning message
    expect(component.emailMismatchWarning).toBeNull();

    // Should redirect despite error
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/accounts']);

    // Invite data should be cleared
    expect(sessionStorage.getItem('inviteData')).toBeNull();
  }));
});
