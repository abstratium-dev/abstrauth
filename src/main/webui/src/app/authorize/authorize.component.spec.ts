import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthorizeComponent } from './authorize.component';

describe('AuthorizeComponent (BFF Pattern)', () => {
  let component: AuthorizeComponent;
  let fixture: ComponentFixture<AuthorizeComponent>;
  let mockLocation: any;
  let originalLocation: any;

  beforeAll(() => {
    // Save original location once
    originalLocation = window.location;
  });

  beforeEach(async () => {
    // Mock window.location.href - reset for each test
    mockLocation = { href: '' };
    Object.defineProperty(window, 'location', {
      writable: true,
      configurable: true,
      value: mockLocation
    });

    await TestBed.configureTestingModule({
      imports: [AuthorizeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorizeComponent);
    component = fixture.componentInstance;
  });

  afterAll(() => {
    // Restore original location after all tests
    Object.defineProperty(window, 'location', {
      writable: true,
      configurable: true,
      value: originalLocation
    });
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should redirect to /oauth2/authorize on init', () => {
    fixture.detectChanges();
    
    // In BFF pattern, component just redirects to authorization endpoint
    // PKCE and state are handled by Quarkus OIDC
    expect(window.location.href).toContain('/oauth2/authorize');
  });

  it('should not use sessionStorage in BFF pattern', () => {
    // Clear any existing state
    sessionStorage.clear();
    
    fixture.detectChanges();
    
    // Verify no PKCE parameters stored in sessionStorage
    // (they're handled server-side in BFF pattern)
    expect(sessionStorage.getItem('state')).toBeNull();
    expect(sessionStorage.getItem('code_verifier')).toBeNull();
    expect(sessionStorage.getItem('code_challenge')).toBeNull();
  });
});
