import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
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
