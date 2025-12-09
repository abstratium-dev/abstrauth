import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SignupComponent } from './signup.component';

describe('SignupComponent', () => {
  let component: SignupComponent;
  let fixture: ComponentFixture<SignupComponent>;
  let mockRouter: jasmine.SpyObj<Router>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [SignupComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SignupComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Form Initialization', () => {
    it('should initialize form with empty values', () => {
      expect(component.signupForm.value).toEqual({
        email: '',
        name: '',
        username: '',
        password: ''
      });
    });

    it('should have required validators on email field', () => {
      const emailControl = component.signupForm.get('email');
      emailControl?.setValue('');
      expect(emailControl?.hasError('required')).toBe(true);
    });

    it('should have email validator on email field', () => {
      const emailControl = component.signupForm.get('email');
      emailControl?.setValue('invalid-email');
      expect(emailControl?.hasError('email')).toBe(true);
    });

    it('should have required validator on username field', () => {
      const usernameControl = component.signupForm.get('username');
      usernameControl?.setValue('');
      expect(usernameControl?.hasError('required')).toBe(true);
    });

    it('should have required and minLength validators on password field', () => {
      const passwordControl = component.signupForm.get('password');
      passwordControl?.setValue('');
      expect(passwordControl?.hasError('required')).toBe(true);
      
      passwordControl?.setValue('short');
      expect(passwordControl?.hasError('minlength')).toBe(true);
    });

    it('should accept valid password with 8 characters', () => {
      const passwordControl = component.signupForm.get('password');
      passwordControl?.setValue('12345678');
      expect(passwordControl?.valid).toBe(true);
    });

    it('should not require name field', () => {
      const nameControl = component.signupForm.get('name');
      nameControl?.setValue('');
      expect(nameControl?.valid).toBe(true);
    });
  });

  describe('Form Validation', () => {
    it('should mark form as invalid when required fields are empty', () => {
      expect(component.signupForm.invalid).toBe(true);
    });

    it('should mark form as valid when all required fields are filled correctly', () => {
      component.signupForm.patchValue({
        email: 'test@example.com',
        username: 'testuser',
        password: 'password123'
      });
      expect(component.signupForm.valid).toBe(true);
    });

    it('should not submit when form is invalid', () => {
      component.signup();
      
      expect(component.isSubmitting).toBe(false);
      httpMock.expectNone('/api/signup');
    });

    it('should mark all fields as touched when submitting invalid form', () => {
      component.signup();
      
      expect(component.signupForm.get('email')?.touched).toBe(true);
      expect(component.signupForm.get('username')?.touched).toBe(true);
      expect(component.signupForm.get('password')?.touched).toBe(true);
    });
  });

  describe('Signup Success', () => {
    beforeEach(() => {
      component.signupForm.patchValue({
        email: 'test@example.com',
        name: 'Test User',
        username: 'testuser',
        password: 'password123'
      });
    });

    it('should submit form data successfully', () => {
      component.signup();

      expect(component.isSubmitting).toBe(true);

      const req = httpMock.expectOne('/api/signup');
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
      
      const body = req.request.body as string;
      expect(body).toContain('email=test%40example.com');
      expect(body).toContain('name=Test+User');
      expect(body).toContain('username=testuser');
      expect(body).toContain('password=password123');

      req.flush({ id: '123' });

      expect(component.messageType).toBe('success');
      expect(component.message).toContain('Account created successfully');
      expect(component.message).toContain('123');
      expect(component.isSubmitting).toBe(false);
    });

    it('should store username and password in model service', () => {
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ id: '123' });

      expect(component['modelService'].signUpUsername$()).toBe('testuser');
      expect(component['modelService'].signUpPassword$()).toBe('password123');
    });

    it('should navigate to signin page with requestId', () => {
      component.requestId = 'test-request-123';
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ id: '123' });

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/signin', 'test-request-123']);
    });

    it('should reset form after successful signup', () => {
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ id: '123' });

      expect(component.signupForm.value).toEqual({
        email: null,
        name: null,
        username: null,
        password: null
      });
    });

    it('should clear message before submitting', () => {
      component.message = 'Previous message';
      component.messageType = 'error';
      
      component.signup();

      expect(component.message).toBe('');
      expect(component.messageType).toBe('');
      
      // Clean up the pending request
      const req = httpMock.expectOne('/api/signup');
      req.flush({ id: '123' });
    });
  });

  describe('Signup Error', () => {
    beforeEach(() => {
      component.signupForm.patchValue({
        email: 'test@example.com',
        username: 'testuser',
        password: 'password123'
      });
    });

    it('should handle error with error_description', () => {
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ error_description: 'Username already exists' }, { status: 400, statusText: 'Bad Request' });

      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Username already exists');
      expect(component.isSubmitting).toBe(false);
    });

    it('should handle error with error field', () => {
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ error: 'Invalid email' }, { status: 400, statusText: 'Bad Request' });

      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Invalid email');
      expect(component.isSubmitting).toBe(false);
    });

    it('should handle error with default message', () => {
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Signing up failed');
      expect(component.isSubmitting).toBe(false);
    });

    it('should not navigate on error', () => {
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ error: 'Error' }, { status: 400, statusText: 'Bad Request' });

      expect(mockRouter.navigate).not.toHaveBeenCalled();
    });

    it('should not reset form on error', () => {
      const originalValues = { ...component.signupForm.value };
      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ error: 'Error' }, { status: 400, statusText: 'Bad Request' });

      expect(component.signupForm.value).toEqual(originalValues);
    });
  });

  describe('Request ID Effect', () => {
    it('should update requestId from model service', () => {
      component['modelService'].setSignInRequestId('new-request-id');
      fixture.detectChanges();
      
      expect(component.requestId).toBe('new-request-id');
    });

    it('should handle empty requestId', () => {
      component['modelService'].setSignInRequestId('');
      fixture.detectChanges();
      
      expect(component.requestId).toBe('');
    });
  });

  describe('Edge Cases', () => {
    it('should handle signup with only required fields', () => {
      component.signupForm.patchValue({
        email: 'minimal@example.com',
        name: '',
        username: 'minimaluser',
        password: 'password123'
      });

      component.signup();

      const req = httpMock.expectOne('/api/signup');
      const body = req.request.body as string;
      expect(body).toContain('email=minimal%40example.com');
      expect(body).toContain('name=');
      expect(body).toContain('username=minimaluser');
      
      req.flush({ id: '456' });
      expect(component.messageType).toBe('success');
    });

    it('should handle special characters in form fields', () => {
      component.signupForm.patchValue({
        email: 'test+tag@example.com',
        name: 'Test O\'Brien',
        username: 'test_user-123',
        password: 'P@ssw0rd!'
      });

      component.signup();

      const req = httpMock.expectOne('/api/signup');
      req.flush({ id: '789' });
      
      expect(component.messageType).toBe('success');
    });
  });
});
