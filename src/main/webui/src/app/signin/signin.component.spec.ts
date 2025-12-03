import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SigninComponent } from './signin.component';

describe('SigninComponent', () => {
  let component: SigninComponent;
  let fixture: ComponentFixture<SigninComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SigninComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => 'test-request-id'
              }
            }
          }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SigninComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Initialization', () => {
    it('should load auth request details on init', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/oauth2/authorize/details/test-request-id');
      expect(req.request.method).toBe('GET');
      req.flush({ clientName: 'Test Client', scope: 'openid profile email' });

      expect(component.clientName).toBe('Test Client');
      expect(component.scopes).toEqual(['openid', 'profile', 'email']);
    });

    it('should handle error loading auth request details', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/oauth2/authorize/details/test-request-id');
      req.flush('Not found', { status: 404, statusText: 'Not Found' });

      expect(component.errorMessage).toBeTruthy();
    });

    it('should set requestId from route parameter', () => {
      fixture.detectChanges();
      httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });

      expect(component.requestId).toBe('test-request-id');
    });

    it('should initialize form with model service values', () => {
      component.modelService.setSignUpUsername('testuser');
      component.modelService.setSignUpPassword('testpass');

      const newFixture = TestBed.createComponent(SigninComponent);
      const newComponent = newFixture.componentInstance;

      expect(newComponent.signinForm.value.username).toBe('testuser');
      expect(newComponent.signinForm.value.password).toBe('testpass');
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      fixture.detectChanges();
      httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
    });

    it('should mark form as invalid when empty', () => {
      component.signinForm.patchValue({ username: '', password: '' });
      expect(component.signinForm.invalid).toBe(true);
    });

    it('should mark form as valid when filled', () => {
      component.signinForm.patchValue({ username: 'user', password: 'pass' });
      expect(component.signinForm.valid).toBe(true);
    });

    it('should not submit when form is invalid', () => {
      component.signinForm.patchValue({ username: '', password: '' });
      component.signin();

      expect(component.isSubmitting).toBe(false);
      httpMock.expectNone('/oauth2/authorize/authenticate');
    });

    it('should mark all fields as touched when submitting invalid form', () => {
      component.signinForm.patchValue({ username: '', password: '' });
      component.signin();

      expect(component.signinForm.get('username')?.touched).toBe(true);
      expect(component.signinForm.get('password')?.touched).toBe(true);
    });
  });

  describe('Sign In', () => {
    beforeEach(() => {
      fixture.detectChanges();
      httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
    });

    it('should submit credentials successfully', () => {
      component.signinForm.patchValue({ username: 'testuser', password: 'testpass' });
      component.signin();

      expect(component.isSubmitting).toBe(true);

      const req = httpMock.expectOne('/oauth2/authorize/authenticate');
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
      
      const body = req.request.body as string;
      expect(body).toContain('username=testuser');
      expect(body).toContain('password=testpass');
      expect(body).toContain('request_id=test-request-id');

      req.flush({ name: 'Test User' });

      expect(component.getApproval).toBe(true);
      expect(component.name).toBe('Test User');
      expect(component.isSubmitting).toBe(false);
      expect(component.errorMessage).toBe('');
    });

    it('should handle authentication failure with error details', () => {
      component.signinForm.patchValue({ username: 'wrong', password: 'wrong' });
      component.signin();

      const req = httpMock.expectOne('/oauth2/authorize/authenticate');
      req.flush({ details: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });

      expect(component.getApproval).toBe(false);
      expect(component.errorMessage).toBe('Invalid credentials');
      expect(component.isSubmitting).toBe(false);
    });

    it('should handle authentication failure with error object', () => {
      component.signinForm.patchValue({ username: 'wrong', password: 'wrong' });
      component.signin();

      const req = httpMock.expectOne('/oauth2/authorize/authenticate');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      expect(component.errorMessage).toBeTruthy();
      expect(component.isSubmitting).toBe(false);
    });

    it('should clear error message before submitting', () => {
      component.errorMessage = 'Previous error';
      component.signinForm.patchValue({ username: 'user', password: 'pass' });
      component.signin();

      expect(component.errorMessage).toBe('');
      
      // Clean up the pending request
      const req = httpMock.expectOne('/oauth2/authorize/authenticate');
      req.flush({ name: 'Test' });
    });
  });

  describe('Federated Sign In', () => {
    beforeEach(() => {
      fixture.detectChanges();
      httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
    });

    it('should have signinWithGoogle method', () => {
      // Test that the method exists and can be called
      expect(component.signinWithGoogle).toBeDefined();
      expect(typeof component.signinWithGoogle).toBe('function');
    });
  });

  describe('Scope Parsing', () => {
    it('should parse single scope', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/oauth2/authorize/details/test-request-id');
      req.flush({ clientName: 'Test', scope: 'openid' });

      expect(component.scopes).toEqual(['openid']);
    });

    it('should parse multiple scopes', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/oauth2/authorize/details/test-request-id');
      req.flush({ clientName: 'Test', scope: 'openid profile email admin' });

      expect(component.scopes).toEqual(['openid', 'profile', 'email', 'admin']);
    });

    it('should handle empty scope', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/oauth2/authorize/details/test-request-id');
      req.flush({ clientName: 'Test', scope: '' });

      expect(component.scopes).toEqual(['']);
    });
  });
});
