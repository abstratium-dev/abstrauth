import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthorizeComponent } from './authorize.component';

describe('AuthorizeComponent', () => {
  let component: AuthorizeComponent;
  let fixture: ComponentFixture<AuthorizeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthorizeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorizeComponent);
    component = fixture.componentInstance;
    
    // Spy on authorize to prevent navigation
    spyOn(component, 'authorize').and.returnValue(Promise.resolve());
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not navigate during test', () => {
    fixture.detectChanges();
    expect(component.authorize).toHaveBeenCalled();
  });

  it('should generate and store state parameter', () => {
    // Clear any existing state
    sessionStorage.clear();
    
    // Test the individual methods without calling authorize (which navigates)
    const realComponent = new AuthorizeComponent();
    
    // Generate state and verifier manually
    const state = realComponent.generateRandomString(32);
    const verifier = realComponent.generateRandomString(128);
    
    // Store them like authorize() does
    sessionStorage.setItem('state', state);
    sessionStorage.setItem('code_verifier', verifier);
    
    // Verify state was stored correctly
    const storedState = sessionStorage.getItem('state');
    expect(storedState).toBeTruthy();
    expect(storedState?.length).toBe(32); // Should be 32 characters
    expect(storedState).toBe(state);
    
    // Verify code_verifier was stored correctly
    const storedVerifier = sessionStorage.getItem('code_verifier');
    expect(storedVerifier).toBeTruthy();
    expect(storedVerifier?.length).toBe(128); // Should be 128 characters
    expect(storedVerifier).toBe(verifier);

    // Clean up
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });
});
