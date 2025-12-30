import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthorizeComponent } from './authorize.component';
import { WINDOW } from '../window.token';

describe('AuthorizeComponent (BFF Pattern)', () => {
  let component: AuthorizeComponent;
  let fixture: ComponentFixture<AuthorizeComponent>;
  let mockWindow: { location: { pathname: string; search: string; href: string } };

  beforeEach(async () => {
    // Create mock window
    mockWindow = {
      location: {
        pathname: '/authorize',
        search: '',
        href: ''
      }
    };

    await TestBed.configureTestingModule({
      imports: [AuthorizeComponent],
      providers: [
        { provide: WINDOW, useValue: mockWindow }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorizeComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should set window.location.href to login endpoint on init', () => {
    // Trigger ngOnInit
    fixture.detectChanges();
    
    // Verify redirect was attempted (href was set)
    expect(mockWindow.location.href).toBe('/api/auth/login');
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
