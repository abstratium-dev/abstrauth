import type { MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SignupComponent } from './signup.component';
import { ModelService } from '../model.service';

describe('SignupComponent', () => {
    let component: SignupComponent;
    let fixture: ComponentFixture<SignupComponent>;
    let mockRouter: MockedObject<Router>;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        mockRouter = createMock<Router>({
            navigate: vi.fn().mockName("Router.navigate")
        });

        await TestBed.configureTestingModule({
            imports: [SignupComponent],
            providers: [
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                { provide: Router, useValue: mockRouter }
            ]
        })
            .compileComponents();

        fixture = TestBed.createComponent(SignupComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        TestBed.inject(ModelService).reset();
        fixture.detectChanges();
    });

    afterEach(() => {
        httpMock.verify();
        TestBed.resetTestingModule();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Form Initialization', () => {
        it('should initialize form with empty values', () => {
            expect(component.signupForm.value).toEqual({
                email: '',
                name: '',
                organisationName: '',
                password: '',
                password2: ''
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

        it('should have required validator on password field', () => {
            const passwordControl = component.signupForm.get('password');
            passwordControl?.setValue('');
            expect(passwordControl?.hasError('required')).toBe(true);
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

        it('should have required validator on organisationName field', () => {
            const orgControl = component.signupForm.get('organisationName');
            orgControl?.setValue('');
            expect(orgControl?.hasError('required')).toBe(true);
        });
    });

    describe('Form Validation', () => {
        it('should mark form as invalid when required fields are empty', () => {
            expect(component.signupForm.invalid).toBe(true);
        });

        it('should mark form as valid when all required fields are filled correctly', () => {
            component.signupForm.patchValue({
                email: 'test@example.com',
                organisationName: 'Test Organisation',
                password: 'password123',
                password2: 'password123'
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
            expect(component.signupForm.get('organisationName')?.touched).toBe(true);
            expect(component.signupForm.get('password')?.touched).toBe(true);
            expect(component.signupForm.get('password2')?.touched).toBe(true);
        });
    });

    describe('Signup Success', () => {
        beforeEach(() => {
            component.signupForm.patchValue({
                email: 'test@example.com',
                name: 'Test User',
                organisationName: 'Test Organisation',
                password: 'password123',
                password2: 'password123'
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
            expect(body).toContain('username=test%40example.com'); // username is the email
            expect(body).toContain('password=password123');
            expect(body).toContain('organisationName=Test+Organisation');

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

            expect(component['modelService'].signUpUsername$()).toBe('test@example.com'); // username is the email
            expect(component['modelService'].signUpPassword$()).toBe('password123');
        });

        it('should navigate to signin page with requestId', () => {
            component['modelService'].setSignInRequestId('test-request-123');
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
                organisationName: null,
                password: null,
                password2: null
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
                name: 'Test User',
                organisationName: 'Test Organisation',
                password: 'password123',
                password2: 'password123'
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
                organisationName: 'Minimal Organisation',
                password: 'password123',
                password2: 'password123'
            });

            component.signup();

            const req = httpMock.expectOne('/api/signup');
            const body = req.request.body as string;
            expect(body).toContain('email=minimal%40example.com');
            expect(body).toContain('name=');
            expect(body).toContain('username=minimal%40example.com'); // username is the email
            expect(body).toContain('organisationName=Minimal+Organisation');

            req.flush({ id: '456' });
            expect(component.messageType).toBe('success');
        });

        it('should handle special characters in form fields', () => {
            component.signupForm.patchValue({
                email: 'test+tag@example.com',
                name: 'Test O\'Brien',
                organisationName: 'O\'Brien Corp',
                password: 'P@ssw0rd!',
                password2: 'P@ssw0rd!'
            });

            component.signup();

            const req = httpMock.expectOne('/api/signup');
            req.flush({ id: '789' });

            expect(component.messageType).toBe('success');
        });
    });

    describe('Organisation Name Auto-population', () => {
        it('should auto-populate organisationName when name changes', () => {
            component.signupForm.get('name')?.setValue('John Doe');
            fixture.detectChanges();

            expect(component.signupForm.get('organisationName')?.value).toBe("John Doe's Organisation");
        });

        it('should not auto-populate organisationName when name is empty', () => {
            component.signupForm.get('name')?.setValue('');
            fixture.detectChanges();

            expect(component.signupForm.get('organisationName')?.value).toBe('');
        });

        it('should not auto-populate after user manually edits organisationName', () => {
            component.signupForm.get('name')?.setValue('John Doe');
            fixture.detectChanges();
            expect(component.signupForm.get('organisationName')?.value).toBe("John Doe's Organisation");

            // Simulate user editing the field
            component.onOrganisationNameChange();
            component.signupForm.get('organisationName')?.setValue('Custom Org Name');

            // Now change the name again - org name should not auto-update
            component.signupForm.get('name')?.setValue('Jane Smith');
            fixture.detectChanges();

            expect(component.signupForm.get('organisationName')?.value).toBe('Custom Org Name');
        });

        it('should mark organisationNameManuallyEdited when onOrganisationNameChange is called', () => {
            expect(component.organisationNameManuallyEdited).toBe(false);

            component.onOrganisationNameChange();

            expect(component.organisationNameManuallyEdited).toBe(true);
        });

        it('should clean up effect when component is destroyed', () => {
            fixture.destroy();
            // After destroy, setting name should not throw or cause side effects
            expect(() => component.signupForm.get('name')?.setValue('Post-destroy')).not.toThrow();
        });
    });

    describe('Template Rendering', () => {
        it('should render success message', () => {
            component.message = 'Account created successfully!';
            component.messageType = 'success';
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const message = compiled.querySelector('#message');
            expect(message).toBeTruthy();
            expect(message?.textContent).toContain('Account created successfully!');
            expect(message?.classList.contains('success-box')).toBe(true);
        });

        it('should render error message', () => {
            component.message = 'Signup failed';
            component.messageType = 'error';
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const message = compiled.querySelector('#message');
            expect(message).toBeTruthy();
            expect(message?.textContent).toContain('Signup failed');
            expect(message?.classList.contains('error-box')).toBe(true);
        });

        it('should display email required error', () => {
            const emailControl = component.signupForm.get('email')!;
            emailControl.setValue('');
            emailControl.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#email + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('Email is required');
        });

        it('should display invalid email error', () => {
            const emailControl = component.signupForm.get('email')!;
            emailControl.setValue('invalid-email');
            emailControl.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#email + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('Please enter a valid email');
        });

        it('should display organisation name required error', () => {
            const orgControl = component.signupForm.get('organisationName')!;
            orgControl.setValue('');
            orgControl.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#organisationName + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('Organisation name is required');
        });

        it('should display password required error', () => {
            const passwordControl = component.signupForm.get('password')!;
            passwordControl.setValue('');
            passwordControl.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#password + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('Password is required');
        });

        it('should display password minlength error', () => {
            const passwordControl = component.signupForm.get('password')!;
            passwordControl.setValue('short');
            passwordControl.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#password + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('at least 8 characters');
        });

        it('should display confirm password required error', () => {
            const password2Control = component.signupForm.get('password2')!;
            password2Control.setValue('');
            password2Control.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#password2 + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('Password is required');
        });

        it('should display confirm password minlength error', () => {
            const password2Control = component.signupForm.get('password2')!;
            password2Control.setValue('short');
            password2Control.markAsTouched();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const error = compiled.querySelector('#password2 + .error');
            expect(error).toBeTruthy();
            expect(error?.textContent).toContain('at least 8 characters');
        });

        it('should mark organisationName as manually edited on input', () => {
            const compiled = fixture.nativeElement as HTMLElement;
            const orgInput = compiled.querySelector('#organisationName') as HTMLInputElement;
            orgInput.value = 'Custom Org';
            orgInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(component.organisationNameManuallyEdited).toBe(true);
        });

        it('should render Creating button text while submitting', () => {
            component.isSubmitting = true;
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const button = compiled.querySelector('#create-account-button') as HTMLButtonElement;
            expect(button?.textContent).toContain('Creating...');
        });
    });
});