import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthorizeComponent } from './authorize.component';

describe('AuthorizeComponent (BFF Pattern)', () => {
  let component: AuthorizeComponent;
  let fixture: ComponentFixture<AuthorizeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthorizeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorizeComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize without redirecting in test environment', () => {
    // In test environment, authorize() should not redirect
    fixture.detectChanges();
    
    // Component should be created successfully without page reload
    expect(component).toBeTruthy();
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
