import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi, type MockedObject } from 'vitest';
import { createMock } from '../../testing/vitest-mocks';
import { SigninComponent } from './signin.component';
import { ModelService } from '../model.service';
import { AuthService } from '../auth.service';

describe('SigninComponent', () => {
    let component: SigninComponent;
    let fixture: ComponentFixture<SigninComponent>;
    let httpMock: HttpTestingController;
    let authServiceMock: MockedObject<AuthService>;

    beforeEach(async () => {
        authServiceMock = createMock<AuthService>({
            isAuthenticated: vi.fn().mockReturnValue(false)
        });

        await TestBed.configureTestingModule({
            imports: [SigninComponent],
            providers: [
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AuthService, useValue: authServiceMock },
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

        TestBed.inject(ModelService).reset();
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

    describe('Invite Data Handling', () => {
        it('should load valid invite data from sessionStorage', () => {
            sessionStorage.setItem('inviteData', JSON.stringify({
                authProvider: 'native',
                email: 'invited@example.com',
                password: 'temp-pass'
            }));

            const newFixture = TestBed.createComponent(SigninComponent);
            const newComponent = newFixture.componentInstance;

            expect(newComponent.inviteData).not.toBeNull();
            expect(newComponent.inviteData?.email).toBe('invited@example.com');
            expect(newComponent.inviteData?.authProvider).toBe('native');
            expect(newComponent.signinForm.value.username).toBe('invited@example.com');
            expect(newComponent.signinForm.value.password).toBe('temp-pass');
        });

        it('should handle invalid invite data JSON in sessionStorage', () => {
            sessionStorage.setItem('inviteData', 'not-valid-json');

            const newFixture = TestBed.createComponent(SigninComponent);
            const newComponent = newFixture.componentInstance;

            expect(newComponent.inviteData).toBeNull();
        });

        it('should reject invalid auth provider in invite data', () => {
            sessionStorage.setItem('inviteData', JSON.stringify({
                authProvider: 'invalid-provider',
                email: 'invited@example.com'
            }));

            const newFixture = TestBed.createComponent(SigninComponent);
            const newComponent = newFixture.componentInstance;

            expect(newComponent.inviteData).toBeNull();
        });
    });

    describe('Getters and Setters', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
        });

        it('should get and set clientId', () => {
            component.clientId = 'client-123';
            expect(component.clientId).toBe('client-123');
        });

        it('should get and set clientName', () => {
            component.clientName = 'New Client';
            expect(component.clientName).toBe('New Client');
        });

        it('should get and set scopes', () => {
            component.scopes = ['openid', 'email'];
            expect(component.scopes).toEqual(['openid', 'email']);
        });

        it('should get and set errorMessage', () => {
            component.errorMessage = 'Error';
            expect(component.errorMessage).toBe('Error');
        });

        it('should get and set getApproval', () => {
            component.getApproval = true;
            expect(component.getApproval).toBe(true);
        });

        it('should get and set isSubmitting', () => {
            component.isSubmitting = true;
            expect(component.isSubmitting).toBe(true);
        });

        it('should get and set name', () => {
            component.name = 'Test';
            expect(component.name).toBe('Test');
        });

        it('should get and set signinIsExpired', () => {
            component.signinIsExpired = true;
            expect(component.signinIsExpired).toBe(true);
        });

        it('should get and set inviteData', () => {
            const inviteData = { authProvider: 'native', email: 'test@example.com' };
            component.inviteData = inviteData;
            expect(component.inviteData).toEqual(inviteData);
        });

        it('should get and set rememberApproval', () => {
            component.rememberApproval = true;
            expect(component.rememberApproval).toBe(true);
        });

        it('should get and set shouldShowApproval', () => {
            component.shouldShowApproval = true;
            expect(component.shouldShowApproval).toBe(true);
        });

        it('should show signup based on modelService', () => {
            component.modelService.setSignupAllowed(true);
            expect(component.showSignup).toBe(true);
        });

        it('should show native signin based on invite data', () => {
            component.inviteData = { authProvider: 'native', email: 'test@example.com' };
            expect(component.showNativeSignin).toBe(true);
        });

        it('should show native signin based on modelService', () => {
            component.modelService.setAllowNativeSignin(true);
            expect(component.showNativeSignin).toBe(true);
        });

        it('should show google signin based on invite data', () => {
            component.inviteData = { authProvider: 'google', email: 'test@example.com' };
            expect(component.showGoogleSignin).toBe(true);
        });

        it('should show google signin based on modelService', () => {
            component.modelService.setAllowGoogleSignin(true);
            expect(component.showGoogleSignin).toBe(true);
        });

        it('should show microsoft signin based on invite data', () => {
            component.inviteData = { authProvider: 'microsoft', email: 'test@example.com' };
            expect(component.showMicrosoftSignin).toBe(true);
        });

        it('should show microsoft signin based on modelService', () => {
            component.modelService.setAllowMicrosoftSignin(true);
            expect(component.showMicrosoftSignin).toBe(true);
        });
    });

    describe('Authenticated Approval Flow', () => {
        it('should approve request for authenticated user', async () => {
            authServiceMock.isAuthenticated.mockReturnValue(true);

            vi.useFakeTimers();
            fixture.detectChanges();

            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });

            const req = httpMock.expectOne('/api/oauth/approve-authenticated?request_id=test-request-id');
            expect(req.request.method).toBe('POST');
            req.flush({ name: 'Authenticated User' });

            vi.advanceTimersByTime(100);
            await Promise.resolve();
            TestBed.flushEffects();

            expect(component.name).toBe('Authenticated User');
            expect(component.getApproval).toBe(true);

            vi.useRealTimers();
        });

        it('should handle 403 error for authenticated user', () => {
            authServiceMock.isAuthenticated.mockReturnValue(true);

            fixture.detectChanges();

            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid', clientId: 'client-123' });

            const req = httpMock.expectOne('/api/oauth/approve-authenticated?request_id=test-request-id');
            req.flush('No roles', { status: 403, statusText: 'Forbidden' });

            expect(component.errorMessage).toContain('No roles');
            expect(component.errorMessage).toContain('client-123');
            expect(component.getApproval).toBe(false);
        });

        it('should handle other errors for authenticated user', () => {
            authServiceMock.isAuthenticated.mockReturnValue(true);

            fixture.detectChanges();

            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });

            const req = httpMock.expectOne('/api/oauth/approve-authenticated?request_id=test-request-id');
            req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

            expect(component.errorMessage).toBe('Failed to process authorization request. Please try again.');
            expect(component.getApproval).toBe(false);
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

        it('should submit credentials successfully', async () => {
            vi.useFakeTimers();
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

            // Allow setTimeout in checkStoredApproval to execute
            vi.advanceTimersByTime(100);
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.getApproval).toBe(true);
            expect(component.name).toBe('Test User');
            expect(component.isSubmitting).toBe(false);
            expect(component.errorMessage).toBe('');

            vi.useRealTimers();
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

        it('should redirect to org-selection when redirectTo is present', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigateByUrl');

            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Test', redirectTo: '/org-selection?token=abc' });

            expect(navigateSpy).toHaveBeenCalledWith('/org-selection?token=abc');
            navigateSpy.mockRestore();
        });

        it('should mark signin as expired on 410 response', () => {
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush('Expired', { status: 410, statusText: 'Gone' });

            expect(component.signinIsExpired).toBe(true);
            expect(component.isSubmitting).toBe(false);
        });

        it('should show 403 error message with clientId', () => {
            component.clientId = 'client-123';
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush('No roles', { status: 403, statusText: 'Forbidden' });

            expect(component.errorMessage).toContain('No roles');
            expect(component.errorMessage).toContain('client-123');
        });

        it('should require password change for native invite with password', () => {
            sessionStorage.setItem('inviteData', JSON.stringify({
                authProvider: 'native',
                email: 'invited@example.com',
                password: 'temp-pass'
            }));

            const newFixture = TestBed.createComponent(SigninComponent);
            const newComponent = newFixture.componentInstance;
            const newHttpMock = TestBed.inject(HttpTestingController);

            newFixture.detectChanges();
            newHttpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });

            newComponent.signinForm.patchValue({ username: 'invited@example.com', password: 'temp-pass' });
            newComponent.signin();

            const req = newHttpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Invited User' });

            expect(sessionStorage.setItem).toHaveBeenCalledWith('requirePasswordChange', 'true');
        });
    });

    describe('Federated Sign In', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
        });

        it('should redirect to Google signin', () => {
            const hrefSpy = vi.spyOn(window.location, 'href', 'set');
            component.signinWithGoogle();
            expect(hrefSpy).toHaveBeenCalledWith('/oauth2/federated/google?request_id=test-request-id');
            hrefSpy.mockRestore();
        });

        it('should redirect to Microsoft signin', () => {
            const hrefSpy = vi.spyOn(window.location, 'href', 'set');
            component.signinWithMicrosoft();
            expect(hrefSpy).toHaveBeenCalledWith('/oauth2/federated/microsoft?request_id=test-request-id');
            hrefSpy.mockRestore();
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

    describe('Stored Approval', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test Client', scope: 'openid profile', clientId: 'client-123' });
        });

        it('should show approval when no stored approval', async () => {
            vi.useFakeTimers();
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Test User' });

            vi.advanceTimersByTime(100);
            await Promise.resolve();
            TestBed.flushEffects();

            expect(component.getApproval).toBe(true);
            expect(component.shouldShowApproval).toBe(true);

            vi.useRealTimers();
        });

        it('should show approval when stored approval is older than 30 days', async () => {
            const key = 'approval_Test Client';
            const oldDate = new Date();
            oldDate.setDate(oldDate.getDate() - 31);
            localStorage.setItem(key, JSON.stringify({ date: oldDate.toISOString(), scopes: ['openid', 'profile'] }));

            vi.useFakeTimers();
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Test User' });

            vi.advanceTimersByTime(100);
            await Promise.resolve();
            TestBed.flushEffects();

            expect(component.shouldShowApproval).toBe(true);
            expect(localStorage.removeItem).toHaveBeenCalledWith(key);

            vi.useRealTimers();
        });

        it('should show approval when stored scopes mismatch', async () => {
            const key = 'approval_Test Client';
            localStorage.setItem(key, JSON.stringify({ date: new Date().toISOString(), scopes: ['openid'] }));

            vi.useFakeTimers();
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Test User' });

            vi.advanceTimersByTime(100);
            await Promise.resolve();
            TestBed.flushEffects();

            expect(component.shouldShowApproval).toBe(true);
            expect(localStorage.removeItem).toHaveBeenCalledWith(key);

            vi.useRealTimers();
        });

        it('should auto-approve when stored approval is valid', async () => {
            const key = 'approval_Test Client';
            localStorage.setItem(key, JSON.stringify({ date: new Date().toISOString(), scopes: ['openid', 'profile'] }));

            const submitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit');

            vi.useFakeTimers();
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Test User' });

            vi.advanceTimersByTime(100);
            await Promise.resolve();
            TestBed.flushEffects();

            expect(component.shouldShowApproval).toBe(false);
            expect(submitSpy).toHaveBeenCalled();

            const form = document.body.querySelector('form[action="/oauth2/authorize"]');
            if (form) {
                document.body.removeChild(form);
            }

            vi.useRealTimers();
            submitSpy.mockRestore();
        });

        it('should show approval when stored approval JSON is invalid', async () => {
            const key = 'approval_Test Client';
            localStorage.setItem(key, 'invalid-json');

            vi.useFakeTimers();
            component.signinForm.patchValue({ username: 'user', password: 'pass' });
            component.signin();

            const req = httpMock.expectOne('/oauth2/authorize/authenticate');
            req.flush({ name: 'Test User' });

            vi.advanceTimersByTime(100);
            await Promise.resolve();
            TestBed.flushEffects();

            expect(component.getApproval).toBe(true);
            expect(component.shouldShowApproval).toBe(true);
            expect(localStorage.removeItem).toHaveBeenCalledWith(key);

            vi.useRealTimers();
        });
    });

    describe('Auto Approval', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
        });

        it('should auto-submit existing approval form', () => {
            const form = document.createElement('form');
            form.setAttribute('action', '/oauth2/authorize');
            document.body.appendChild(form);

            const submitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit');

            vi.useFakeTimers();
            component.autoApprove();
            vi.advanceTimersByTime(50);
            vi.useRealTimers();

            expect(submitSpy).toHaveBeenCalled();

            submitSpy.mockRestore();
            document.body.removeChild(form);
        });

        it('should log error when approval form is not found', () => {
            const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

            vi.useFakeTimers();
            component.autoApprove();
            vi.advanceTimersByTime(50);
            vi.useRealTimers();

            expect(consoleErrorSpy).toHaveBeenCalledWith('[SIGNIN] Approval form not found in DOM');

            consoleErrorSpy.mockRestore();
        });

        it('should auto-approve directly via hidden form', () => {
            const submitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit');

            component.autoApproveDirectly();

            expect(submitSpy).toHaveBeenCalled();

            const form = document.body.querySelector('form[action="/oauth2/authorize"]');
            if (form) {
                document.body.removeChild(form);
            }

            submitSpy.mockRestore();
        });

        it('should save approval and submit on approve click', () => {
            component.clientName = 'Test Client';
            component.scopes = ['openid', 'profile'];
            component.rememberApproval = true;

            const form = document.createElement('form');
            const consent = document.createElement('input');
            const submitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit');

            component.onApproveClick(form, consent);

            expect(localStorage.setItem).toHaveBeenCalledWith('approval_Test Client', expect.stringContaining('"scopes":["openid","profile"]'));
            expect(consent.value).toBe('approve');
            expect(submitSpy).toHaveBeenCalled();

            submitSpy.mockRestore();
        });

        it('should set consent to deny and submit on deny click', () => {
            const form = document.createElement('form');
            const consent = document.createElement('input');
            const submitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit');

            component.onDenyClick(form, consent);

            expect(consent.value).toBe('deny');
            expect(submitSpy).toHaveBeenCalled();

            submitSpy.mockRestore();
        });

        it('should not save approval when rememberApproval is false', () => {
            component.clientName = 'Test Client';
            component.scopes = ['openid', 'profile'];
            component.rememberApproval = false;

            const form = document.createElement('form');
            const consent = document.createElement('input');
            const submitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit');

            component.onApproveClick(form, consent);

            expect(localStorage.setItem).not.toHaveBeenCalled();
            expect(consent.value).toBe('approve');
            expect(submitSpy).toHaveBeenCalled();

            submitSpy.mockRestore();
        });
    });

    describe('Template Rendering', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });
        });

        it('should render signin expired card', () => {
            component.signinIsExpired = true;
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('Request expired');
        });

        it('should render invite info', () => {
            component.inviteData = { authProvider: 'native', email: 'invited@example.com' };
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('invited@example.com');
        });

        it('should render error message', () => {
            component.errorMessage = 'An error';
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('An error');
        });

        it('should render native signin form', () => {
            component.modelService.setAllowNativeSignin(true);
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.querySelector('form.centered-form')).toBeTruthy();
            expect(compiled.querySelector('#signin-button')).toBeTruthy();
        });

        it('should render validation errors when form is touched and invalid', () => {
            component.modelService.setAllowNativeSignin(true);
            component.signinForm.patchValue({ username: '', password: '' });
            component.signin();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('Email is required');
            expect(compiled.textContent).toContain('Password is required');
        });

        it('should render signup link', () => {
            component.modelService.setAllowNativeSignin(true);
            component.modelService.setSignupAllowed(true);
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.querySelector('#signup-link')).toBeTruthy();
        });

        it('should render google signin button', () => {
            component.modelService.setAllowGoogleSignin(true);
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.querySelector('#google-signin-button')).toBeTruthy();
        });

        it('should render google/microsoft divider', () => {
            component.modelService.setAllowNativeSignin(true);
            component.modelService.setAllowGoogleSignin(true);
            component.modelService.setAllowMicrosoftSignin(true);
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.querySelector('#google-signin-button')).toBeTruthy();
            expect(compiled.querySelector('#microsoft-signin-button')).toBeTruthy();
            expect(compiled.querySelector('.divider')).toBeTruthy();
        });

        it('should render microsoft signin button', () => {
            component.modelService.setAllowMicrosoftSignin(true);
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.querySelector('#microsoft-signin-button')).toBeTruthy();
        });

        it('should render approval section', () => {
            component.getApproval = true;
            component.name = 'Test User';
            component.clientName = 'Test Client';
            component.scopes = ['openid', 'profile'];
            component.shouldShowApproval = true;
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('Approve Application');
            expect(compiled.textContent).toContain('Test Client');
            expect(compiled.querySelector('#approve-button')).toBeTruthy();
            expect(compiled.querySelector('#deny-button')).toBeTruthy();
        });

        it('should render approval error message', () => {
            component.getApproval = true;
            component.errorMessage = 'Approval error';
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('Approval error');
        });

        it('should render automatic approval message', () => {
            component.getApproval = true;
            component.shouldShowApproval = false;
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('Approving automatically');
        });
    });

    describe('Signin Timeout', () => {
        it('should mark signin as expired after timeout', () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            httpMock.expectOne('/oauth2/authorize/details/test-request-id').flush({ clientName: 'Test', scope: 'openid' });

            vi.advanceTimersByTime((10 * 60 * 1000) - (30 * 1000));
            TestBed.flushEffects();

            expect(component.signinIsExpired).toBe(true);
            vi.useRealTimers();
        });
    });
});