import type { MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { AuthService, ROLE_MANAGE_CLIENTS } from '../auth.service';
import { ClientsComponent } from './clients.component';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';
import { AllowedRole, ClientSecret, ModelService } from '../model.service';

describe('ClientsComponent', () => {
    let component: ClientsComponent;
    let fixture: ComponentFixture<ClientsComponent>;
    let httpMock: HttpTestingController;
    let queryParamsSubject: BehaviorSubject<any>;
    let confirmService: MockedObject<ConfirmDialogService>;

    const mockClients = [
        {
            id: '1',
            orgId: 'test-org',
            clientId: 'test_client_1',
            clientName: 'Test Client 1',
            clientType: 'confidential',
            redirectUris: '["http://localhost:3000/callback"]',
            allowedScopes: '["openid", "profile", "email"]',
            requirePkce: true,
            autoSubscribe: true,
            publik: false,
            createdAt: '2024-01-01T00:00:00Z'
        },
        {
            id: '2',
            orgId: 'test-org',
            clientId: 'test_client_2',
            clientName: 'Test Client 2',
            clientType: 'confidential',
            redirectUris: '["http://localhost:4000/callback", "http://localhost:4000/auth"]',
            allowedScopes: '["openid", "admin"]',
            requirePkce: true,
            autoSubscribe: true,
            publik: false,
            createdAt: '2024-01-02T00:00:00Z'
        }
    ];

    beforeEach(async () => {
        vi.useFakeTimers();
        queryParamsSubject = new BehaviorSubject({});

        const routerSpy = createMock<Router>({
            navigate: vi.fn().mockName("Router.navigate"),
            createUrlTree: vi.fn().mockName("Router.createUrlTree"),
            serializeUrl: vi.fn().mockName("Router.serializeUrl"),
            events: EMPTY
        });
        routerSpy.createUrlTree.mockReturnValue({} as any);
        routerSpy.serializeUrl.mockReturnValue('');

        const confirmServiceSpy = createMock<ConfirmDialogService>({
            confirm: vi.fn().mockName("ConfirmDialogService.confirm")
        });
        confirmServiceSpy.confirm.mockResolvedValue(true); // Default to confirming

        await TestBed.configureTestingModule({
            imports: [ClientsComponent],
            providers: [
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                { provide: Router, useValue: routerSpy },
                { provide: ConfirmDialogService, useValue: confirmServiceSpy },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        queryParams: queryParamsSubject.asObservable()
                    }
                }
            ]
        })
            .compileComponents();

        fixture = TestBed.createComponent(ClientsComponent);
        component = fixture.componentInstance;
        TestBed.inject(ModelService).reset();
        httpMock = TestBed.inject(HttpTestingController);
        confirmService = TestBed.inject(ConfirmDialogService) as MockedObject<ConfirmDialogService>;
    });

    afterEach(() => {
        // Flush any pending /public/config requests from app initializer
        const configRequests = httpMock.match('/public/config');
        configRequests.forEach(req => {
            if (!req.cancelled) {
                req.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 900, insecureClientSecret: false, warningMessage: '' });
            }
        });

        vi.clearAllTimers();
        vi.useRealTimers();
        httpMock.verify();
        TestBed.resetTestingModule();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Component Initialization', () => {
        it('should start with loading state true', () => {
            component.ngOnInit();
            expect(component.loading).toBe(true);
            httpMock.expectOne('/api/clients').flush([]);
        });

        it('should start with empty clients array', () => {
            expect(component.clients).toEqual([]);
        });

        it('should start with no error', () => {
            expect(component.error).toBeNull();
        });

        it('should call loadClients on init', () => {
            vi.spyOn(component, 'loadClients').mockReturnValue(undefined);
            component.ngOnInit();
            expect(component.loadClients).toHaveBeenCalled();
        });
    });

    describe('Loading Clients - Success Cases', () => {
        it('should load clients successfully', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            expect(req.request.method).toBe('GET');
            req.flush(mockClients);
            fixture.detectChanges();

            expect(component.clients).toEqual(mockClients);
            expect(component.loading).toBe(false);
            expect(component.error).toBeNull();
        });

        it('should display loading message while fetching', () => {
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const loadingDiv = compiled.querySelector('.loading');

            expect(loadingDiv).toBeTruthy();
            expect(loadingDiv.textContent).toContain('Loading clients');

            // Flush the pending request to clean up
            const req = httpMock.expectOne('/api/clients');
            req.flush([]);
        });

        it('should display clients after successful load', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const cards = compiled.querySelectorAll('.card');

            expect(cards.length).toBe(2);
            expect(compiled.textContent).toContain('Test Client 1');
            expect(compiled.textContent).toContain('Test Client 2');
        });

        it('should display client details correctly', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([mockClients[0]]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;

            expect(compiled.textContent).toContain('test_client_1');
            expect(compiled.textContent).toContain('http://localhost:3000/callback');
            expect(compiled.textContent).toContain('openid');
            expect(compiled.textContent).toContain('profile');
            expect(compiled.textContent).toContain('email');
            expect(compiled.textContent).toContain('Yes'); // requirePkce
        });

        it('should display correct badge for confidential client', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([mockClients[0]]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const badge = compiled.querySelector('.badge-secondary');

            expect(badge).toBeTruthy();
            expect(badge.textContent).toContain('confidential');
        });

        it('should display correct badge for confidential client', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([mockClients[1]]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const badge = compiled.querySelector('.badge-secondary');

            expect(badge).toBeTruthy();
            expect(badge.textContent).toContain('confidential');
        });

        it('should display info message when no clients exist', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const infoMessage = compiled.querySelector('.info-message');

            expect(infoMessage).toBeTruthy();
            expect(infoMessage.textContent).toContain('No OAuth clients found');
        });

        it('should display multiple redirect URIs', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([mockClients[1]]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const uriList = compiled.querySelectorAll('.simple-list li');

            expect(uriList.length).toBe(2);
            expect(uriList[0].textContent).toContain('http://localhost:4000/callback');
            expect(uriList[1].textContent).toContain('http://localhost:4000/auth');
        });

        it('should display multiple scopes as badges', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([mockClients[0]]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const scopeBadges = compiled.querySelectorAll('.badge-success');

            expect(scopeBadges.length).toBe(3);
        });
    });

    describe('Loading Clients - Error Cases', () => {
        it('should handle HTTP error gracefully', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush('Error loading clients', { status: 500, statusText: 'Server Error' });
            fixture.detectChanges();

            expect(component.error).toBe('Failed to load clients');
            expect(component.loading).toBe(false);
            expect(component.clients).toEqual([]);
        });

        it('should display error message on failure', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush('Error', { status: 500, statusText: 'Server Error' });
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const errorBox = compiled.querySelector('.error-box');

            expect(errorBox).toBeTruthy();
            expect(errorBox.textContent).toContain('Failed to load clients');
        });

        it('should handle 404 error', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush('Not found', { status: 404, statusText: 'Not Found' });
            fixture.detectChanges();

            expect(component.error).toBe('Failed to load clients');
            expect(component.loading).toBe(false);
        });

        it('should handle network error', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.error(new ProgressEvent('error'));
            fixture.detectChanges();

            expect(component.error).toBe('Failed to load clients');
            expect(component.loading).toBe(false);
        });
    });

    describe('parseJsonArray method', () => {
        it('should parse valid JSON array', () => {
            const result = component.parseJsonArray('["scope1", "scope2", "scope3"]');
            expect(result).toEqual(['scope1', 'scope2', 'scope3']);
        });

        it('should return empty array for invalid JSON', () => {
            const result = component.parseJsonArray('invalid json');
            expect(result).toEqual([]);
        });

        it('should return empty array for empty string', () => {
            const result = component.parseJsonArray('');
            expect(result).toEqual([]);
        });

        it('should handle empty JSON array', () => {
            const result = component.parseJsonArray('[]');
            expect(result).toEqual([]);
        });

        it('should handle malformed JSON gracefully', () => {
            const result = component.parseJsonArray('[unclosed');
            expect(result).toEqual([]);
        });
    });

    describe('UI State Management', () => {
        it('should not show loading, error, or clients initially before HTTP response', () => {
            // Don't trigger change detection yet
            const compiled = fixture.nativeElement;

            component.ngOnInit();

            // Component has triggered the HTTP request but it hasn't been flushed yet
            expect(component.loading).toBe(true);
            expect(component.error).toBeNull();
            expect(component.clients).toEqual([]);
            httpMock.expectOne('/api/clients').flush([]);
        });

        it('should hide loading message after successful load', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const loadingDiv = compiled.querySelector('.loading');

            expect(loadingDiv).toBeFalsy();
        });

        it('should hide loading message after error', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush('Error', { status: 500, statusText: 'Server Error' });
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const loadingDiv = compiled.querySelector('.loading');

            expect(loadingDiv).toBeFalsy();
        });

        it('should display data-client-id attribute for e2e testing', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush([mockClients[0]]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const card = compiled.querySelector('[data-client-id="test_client_1"]');

            expect(card).toBeTruthy();
        });
    });

    describe('Form Management', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should start with form hidden', () => {
            expect(component.showForm).toBe(false);
        });

        it('should toggle form visibility', () => {
            expect(component.showForm).toBe(false);
            component.toggleForm();
            expect(component.showForm).toBe(true);
            component.toggleForm();
            expect(component.showForm).toBe(false);
        });

        it('should reset form when showing', () => {
            component.formData.clientId = 'test';
            component.formError = 'Some error';
            component.toggleForm();
            expect(component.formData.clientId).toBe('');
            expect(component.formError).toBeNull();
        });

        it('should initialize form with default values', () => {
            component.resetForm();
            expect(component.formData.clientId).toBe('');
            expect(component.formData.clientName).toBe('');
            expect(component.formData.clientType).toBe('confidential');
            expect(component.formData.redirectUris).toBe('');
            expect(component.formData.allowedScopes).toBe('');
            expect(component.formData.requirePkce).toBe(true);
            expect(component.formError).toBeNull();
        });
    });

    describe('Client Creation', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should create client successfully', async () => {
            component.formData = {
                clientId: 'new_client',
                clientName: 'New Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid profile',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            expect(createReq.request.method).toBe('POST');
            expect(createReq.request.body.clientId).toBe('new_client');
            expect(createReq.request.body.redirectUris).toBe('["http://localhost:3000/callback"]');
            expect(createReq.request.body.allowedScopes).toBe('["openid","profile"]');

            createReq.flush({
                id: '3',
                orgId: 'test-org',
                clientId: 'new_client',
                clientName: 'New Client',
                clientType: 'confidential',
                redirectUris: '["http://localhost:3000/callback"]',
                allowedScopes: '["openid", "profile"]',
                requirePkce: true,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-03T00:00:00Z'
            });

            // Wait a microtask for loadClients() to be called
            await Promise.resolve();

            // Expect reload of clients list
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([...mockClients]);

            await submitPromise;

            expect(component.showForm).toBe(false);
            expect(component.formError).toBeNull();
        });

        it('should handle multiple redirect URIs', async () => {
            component.formData = {
                clientId: 'multi_uri_client',
                clientName: 'Multi URI Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback\nhttp://localhost:4000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            expect(createReq.request.body.redirectUris).toBe('["http://localhost:3000/callback","http://localhost:4000/callback"]');

            createReq.flush({ id: '3', orgId: 'test-org', ...component.formData, autoSubscribe: true, publik: false });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([]);

            await submitPromise;
        });

        it('should handle comma-separated scopes', async () => {
            component.formData = {
                clientId: 'comma_scopes_client',
                clientName: 'Comma Scopes Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid, profile, email',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            expect(createReq.request.body.allowedScopes).toBe('["openid","profile","email"]');

            createReq.flush({ id: '3', orgId: 'test-org', ...component.formData, autoSubscribe: true, publik: false });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([]);

            await submitPromise;
        });

        it('should validate redirect URIs are required when scopes are set', async () => {
            component.formData = {
                clientId: 'test_client',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: '',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            await component.onSubmit();

            expect(component.formError).toBe('Redirect URIs are required when scopes are configured');
            expect(component.formSubmitting).toBe(false);
        });

        it('should validate scopes are required when redirect URIs are set', async () => {
            component.formData = {
                clientId: 'test_client',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: '',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            await component.onSubmit();

            expect(component.formError).toBe('Scopes are required when redirect URIs are configured');
            expect(component.formSubmitting).toBe(false);
        });

        it('should validate client ID contains only letters, numbers, and underscores', async () => {
            component.formData = {
                clientId: 'invalid-client-id',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            await component.onSubmit();

            expect(component.formError).toBe('Client ID must contain only letters, numbers, and underscores');
            expect(component.formSubmitting).toBe(false);
        });

        it('should reject client ID with special characters', async () => {
            component.formData = {
                clientId: 'my@client#id',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            await component.onSubmit();

            expect(component.formError).toBe('Client ID must contain only letters, numbers, and underscores');
            expect(component.formSubmitting).toBe(false);
        });

        it('should accept client ID with underscores', async () => {
            component.formData = {
                clientId: 'my_client_id',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            expect(createReq.request.method).toBe('POST');
            expect(createReq.request.body.clientId).toBe('my_client_id');

            createReq.flush({
                id: '3',
                orgId: 'test-org',
                clientId: 'my_client_id',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: '["http://localhost:3000/callback"]',
                allowedScopes: '["openid"]',
                requirePkce: true,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-03T00:00:00Z'
            });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([...mockClients]);

            await submitPromise;

            expect(component.showForm).toBe(false);
            expect(component.formError).toBeNull();
        });

        it('should accept client ID with numbers', async () => {
            component.formData = {
                clientId: 'client123_test456',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            expect(createReq.request.method).toBe('POST');
            expect(createReq.request.body.clientId).toBe('client123_test456');

            createReq.flush({
                id: '3',
                orgId: 'test-org',
                clientId: 'client123_test456',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: '["http://localhost:3000/callback"]',
                allowedScopes: '["openid"]',
                requirePkce: true,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-03T00:00:00Z'
            });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([...mockClients]);

            await submitPromise;

            expect(component.formError).toBeNull();
        });

        it('should allow M2M client with no scopes and no redirect URIs', async () => {
            component.formData = {
                clientId: 'm2m_client',
                clientName: 'M2M Client',
                clientType: 'confidential',
                redirectUris: '',
                allowedScopes: '',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            expect(createReq.request.method).toBe('POST');
            expect(createReq.request.body.clientId).toBe('m2m_client');
            expect(createReq.request.body.redirectUris).toBe('[]');
            expect(createReq.request.body.allowedScopes).toBe('[]');

            createReq.flush({
                id: '3',
                orgId: 'test-org',
                clientId: 'm2m-client',
                clientName: 'M2M Client',
                clientType: 'confidential',
                redirectUris: '[]',
                allowedScopes: '[]',
                requirePkce: true,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-03T00:00:00Z'
            });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([...mockClients]);

            await submitPromise;

            expect(component.showForm).toBe(false);
            expect(component.formError).toBeNull();
        });

        it('should handle duplicate client ID error', async () => {
            component.showForm = true;
            component.formData = {
                clientId: 'existing_client',
                clientName: 'Existing Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({ error: 'Client ID already exists' }, { status: 409, statusText: 'Conflict' });

            await submitPromise;

            expect(component.formError).toBe('Client ID already exists');
            expect(component.showForm).toBe(true);
        });

        it('should handle permission error', async () => {
            component.formData = {
                clientId: 'test_client',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({}, { status: 403, statusText: 'Forbidden' });

            await submitPromise;

            expect(component.formError).toBe('You do not have permission to create clients');
        });

        it('should handle generic error', async () => {
            component.formData = {
                clientId: 'test_client',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({}, { status: 500, statusText: 'Server Error' });

            await submitPromise;

            expect(component.formError).toBe('Failed to create client. Please try again.');
        });

        it('should set formSubmitting during submission', async () => {
            component.formData = {
                clientId: 'test_client',
                clientName: 'Test Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: false
            };

            expect(component.formSubmitting).toBe(false);

            const submitPromise = component.onSubmit();

            // Should be true during submission
            expect(component.formSubmitting).toBe(true);

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({ id: '3', orgId: 'test-org', ...component.formData, autoSubscribe: true, publik: false });
            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([]);

            await submitPromise;

            expect(component.formSubmitting).toBe(false);
        });
    });

    describe('Role-based Access', () => {
        it('should check for manage clients role', () => {
            // This test verifies the method exists and returns a boolean
            const result = component.hasManageClientsRole();
            expect(typeof result).toBe('boolean');
        });
    });

    describe('Client Editing', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should start edit mode for a client', () => {
            expect(component.editingClientId).toBeNull();

            component.startEdit(mockClients[0]);

            expect(component.editingClientId).toBe('1');
            expect(component.showForm).toBe(false);
            expect(component.formData.clientId).toBe('test_client_1');
            expect(component.formData.clientName).toBe('Test Client 1');
            expect(component.formData.clientType).toBe('confidential');
            expect(component.formData.redirectUris).toBe('http://localhost:3000/callback');
            expect(component.formData.allowedScopes).toBe('openid profile email');
            expect(component.formData.requirePkce).toBe(true);
        });

        it('should cancel edit mode', () => {
            component.startEdit(mockClients[0]);
            expect(component.editingClientId).toBe('1');

            component.cancelEdit();

            expect(component.editingClientId).toBeNull();
            expect(component.formData.clientId).toBe('');
        });

        it('should update client successfully', async () => {
            component.startEdit(mockClients[0]);
            component.formData.clientName = 'Updated Client Name';
            component.formData.clientType = 'confidential';

            const submitPromise = component.onSubmit();

            const updateReq = httpMock.expectOne('/api/clients/1');
            expect(updateReq.request.method).toBe('PUT');
            expect(updateReq.request.body.clientName).toBe('Updated Client Name');
            expect(updateReq.request.body.clientType).toBe('confidential');

            updateReq.flush({
                ...mockClients[0],
                clientName: 'Updated Client Name',
                clientType: 'confidential'
            });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([...mockClients]);

            await submitPromise;

            expect(component.editingClientId).toBeNull();
            expect(component.formError).toBeNull();
        });

        it('should handle update error', async () => {
            component.startEdit(mockClients[0]);

            const submitPromise = component.onSubmit();

            const updateReq = httpMock.expectOne('/api/clients/1');
            updateReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

            await submitPromise;

            expect(component.formError).toBeTruthy();
            expect(component.editingClientId).toBe('1');
        });

        it('should parse JSON arrays correctly when starting edit', () => {
            const client = {
                ...mockClients[1],
                redirectUris: '["http://localhost:4000/callback", "http://localhost:4000/auth"]',
                allowedScopes: '["openid", "admin"]'
            };

            component.startEdit(client);

            expect(component.formData.redirectUris).toBe('http://localhost:4000/callback\nhttp://localhost:4000/auth');
            expect(component.formData.allowedScopes).toBe('openid admin');
        });

        it('should set formSubmitting during update', async () => {
            component.startEdit(mockClients[0]);

            expect(component.formSubmitting).toBe(false);

            const submitPromise = component.onSubmit();

            expect(component.formSubmitting).toBe(true);

            const updateReq = httpMock.expectOne('/api/clients/1');
            updateReq.flush(mockClients[0]);

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([]);

            await submitPromise;

            expect(component.formSubmitting).toBe(false);
        });

        it('should not include clientId in update request', async () => {
            component.startEdit(mockClients[0]);

            const submitPromise = component.onSubmit();

            const updateReq = httpMock.expectOne('/api/clients/1');
            expect(updateReq.request.body.clientId).toBeUndefined();
            expect(updateReq.request.body.clientName).toBeDefined();

            updateReq.flush(mockClients[0]);

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([]);

            await submitPromise;
        });
    });

    describe('Client Deletion', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should delete client successfully after confirmation', async () => {
            const deletePromise = component.deleteClient(mockClients[0]);

            // Wait for confirmation Promise to resolve
            await Promise.resolve();

            const deleteReq = httpMock.expectOne('/api/clients/test_client_1');
            expect(deleteReq.request.method).toBe('DELETE');
            deleteReq.flush(null, { status: 204, statusText: 'No Content' });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([mockClients[1]]);

            await deletePromise;

            expect(confirmService.confirm).toHaveBeenCalled();
        });

        it('should not delete client if user cancels confirmation', async () => {
            confirmService.confirm.mockResolvedValue(false);

            await component.deleteClient(mockClients[0]);

            httpMock.expectNone('/api/clients/test_client_1');
            expect(confirmService.confirm).toHaveBeenCalled();
        });

        it('should cancel edit mode if deleting the client being edited', async () => {
            component.startEdit(mockClients[0]);
            expect(component.editingClientId).toBe('1');

            const deletePromise = component.deleteClient(mockClients[0]);

            // Wait for confirmation Promise to resolve
            await Promise.resolve();

            const deleteReq = httpMock.expectOne('/api/clients/test_client_1');
            deleteReq.flush(null, { status: 204, statusText: 'No Content' });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([]);

            await deletePromise;

            expect(component.editingClientId).toBeNull();
        });

        it('should handle 404 error when deleting', async () => {
            const deletePromise = component.deleteClient(mockClients[0]);

            // Wait for confirmation Promise to resolve
            await Promise.resolve();

            const deleteReq = httpMock.expectOne('/api/clients/test_client_1');
            deleteReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

            await deletePromise;
        });

        it('should handle 403 permission error when deleting', async () => {
            const deletePromise = component.deleteClient(mockClients[0]);

            // Wait for confirmation Promise to resolve
            await Promise.resolve();

            const deleteReq = httpMock.expectOne('/api/clients/test_client_1');
            deleteReq.flush({}, { status: 403, statusText: 'Forbidden' });

            await deletePromise;
        });

        it('should handle generic error when deleting', async () => {
            const deletePromise = component.deleteClient(mockClients[0]);

            // Wait for confirmation Promise to resolve
            await Promise.resolve();

            const deleteReq = httpMock.expectOne('/api/clients/test_client_1');
            deleteReq.flush({}, { status: 500, statusText: 'Internal Server Error' });

            await deletePromise;
        });
    });

    describe('Secret Expiration Detection', () => {
        it('should detect secret expiring within 30 days', () => {
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 1);

            const secret = {
                id: 105,
                description: 'Soon to Expire',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: tomorrow.toISOString()
            };

            expect(component.isSecretExpiringSoon(secret)).toBe(true);
        });

        it('should detect secret expiring in exactly 30 days', () => {
            const thirtyDaysFromNow = new Date();
            thirtyDaysFromNow.setDate(thirtyDaysFromNow.getDate() + 30);

            const secret = {
                id: 106,
                description: 'Expires in 30 days',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: thirtyDaysFromNow.toISOString()
            };

            expect(component.isSecretExpiringSoon(secret)).toBe(true);
        });

        it('should not detect secret expiring in 32 days as expiring soon', () => {
            const thirtyTwoDaysFromNow = new Date();
            thirtyTwoDaysFromNow.setDate(thirtyTwoDaysFromNow.getDate() + 32);

            const secret = {
                id: 107,
                description: 'Expires in 32 days',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: thirtyTwoDaysFromNow.toISOString()
            };

            expect(component.isSecretExpiringSoon(secret)).toBe(false);
        });

        it('should not detect already expired secret as expiring soon', () => {
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);

            const secret = {
                id: 108,
                description: 'Already expired',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: yesterday.toISOString()
            };

            expect(component.isSecretExpiringSoon(secret)).toBe(false);
        });

        it('should not detect secret without expiration as expiring soon', () => {
            const secret = {
                id: 109,
                description: 'No expiration',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: null
            };

            expect(component.isSecretExpiringSoon(secret)).toBe(false);
        });

        it('should detect expired secret', () => {
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);

            const secret = {
                id: 110,
                description: 'Expired',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: yesterday.toISOString()
            };

            expect(component.isSecretExpired(secret)).toBe(true);
        });

        it('should not detect future secret as expired', () => {
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 1);

            const secret = {
                id: 111,
                description: 'Not expired',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: tomorrow.toISOString()
            };

            expect(component.isSecretExpired(secret)).toBe(false);
        });

        it('should not detect secret without expiration as expired', () => {
            const secret = {
                id: 112,
                description: 'No expiration',
                active: true,
                createdAt: '2026-02-05T20:30:58Z',
                expiresAt: null
            };

            expect(component.isSecretExpired(secret)).toBe(false);
        });
    });

    describe('Allowed Roles Management', () => {
        const mockAllowedRoles: AllowedRole[] = [
            { clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true },
            { clientId: 'test_client_1', role: 'editor', isDefault: false, availableToForeignOrgs: false }
        ];

        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should toggle allowed roles view', async () => {
            const togglePromise = component.toggleAllowedRolesView(mockClients[0]);

            const rolesReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            expect(rolesReq.request.method).toBe('GET');
            rolesReq.flush(mockAllowedRoles);

            await togglePromise;

            expect(component.viewingAllowedRolesFor).toBe('test_client_1');
            expect(component.allowedRoles).toEqual(mockAllowedRoles);
            expect(component.allowedRolesLoading()).toBe(false);
        });

        it('should hide allowed roles view when toggling again', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = mockAllowedRoles;

            await component.toggleAllowedRolesView(mockClients[0]);

            expect(component.viewingAllowedRolesFor).toBeNull();
            expect(component.allowedRoles).toEqual([]);
            expect(component.showAddAllowedRoleForm).toBe(false);
            expect(component.editingAllowedRole).toBeNull();
        });

        it('should handle error when loading allowed roles', async () => {
            const togglePromise = component.toggleAllowedRolesView(mockClients[0]);

            const rolesReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            rolesReq.flush({ error: 'Failed to load' }, { status: 500, statusText: 'Internal Server Error' });

            await togglePromise;

            expect(component.allowedRolesError).toBe('Failed to load allowed roles');
            expect(component.allowedRolesLoading()).toBe(false);
        });

        it('should toggle add allowed role form', () => {
            expect(component.showAddAllowedRoleForm).toBe(false);

            component.toggleAddAllowedRoleForm();
            expect(component.showAddAllowedRoleForm).toBe(true);
            expect(component.addAllowedRoleData.role).toBe('');
            expect(component.addAllowedRoleData.isDefault).toBe(false);

            component.toggleAddAllowedRoleForm();
            expect(component.showAddAllowedRoleForm).toBe(false);
        });

        it('should add an allowed role successfully', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = [];
            component.addAllowedRoleData = { role: 'admin', isDefault: true, availableToForeignOrgs: true };

            component.addAllowedRole('test_client_1');
            await Promise.resolve(); TestBed.flushEffects();

            const addReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles');
            expect(addReq.request.method).toBe('POST');
            expect(addReq.request.body).toEqual({ role: 'admin', isDefault: true, availableToForeignOrgs: true });
            addReq.flush({ clientId: 'test_client_1', role: 'admin', isDefault: true, availableToForeignOrgs: true });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            expect(reloadReq.request.method).toBe('GET');
            reloadReq.flush([...mockAllowedRoles, { clientId: 'test_client_1', role: 'admin', isDefault: true, availableToForeignOrgs: true }]);
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.showAddAllowedRoleForm).toBe(false);
            expect(component.addAllowedRoleData.role).toBe('');
        });

        it('should reject empty allowed role name', async () => {
            component.addAllowedRoleData = { role: '', isDefault: false, availableToForeignOrgs: false };
            await component.addAllowedRole('test_client_1');
            httpMock.expectNone('/api/clients/test_client_1/allowed-roles');
        });

        it('should reject allowed role with uppercase letters', async () => {
            component.addAllowedRoleData = { role: 'Admin', isDefault: false, availableToForeignOrgs: false };
            await component.addAllowedRole('test_client_1');
            httpMock.expectNone('/api/clients/test_client_1/allowed-roles');
        });

        it('should reject allowed role with underscores', async () => {
            component.addAllowedRoleData = { role: 'api_reader', isDefault: false, availableToForeignOrgs: false };
            await component.addAllowedRole('test_client_1');
            httpMock.expectNone('/api/clients/test_client_1/allowed-roles');
        });

        it('should reject allowed role with spaces', async () => {
            component.addAllowedRoleData = { role: 'api reader', isDefault: false, availableToForeignOrgs: false };
            await component.addAllowedRole('test_client_1');
            httpMock.expectNone('/api/clients/test_client_1/allowed-roles');
        });

        it('should handle 409 conflict when adding duplicate allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.addAllowedRoleData = { role: 'viewer', isDefault: false, availableToForeignOrgs: false };

            const addPromise = component.addAllowedRole('test_client_1');

            const addReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles');
            addReq.flush({ error: 'Role already exists in allowlist' }, { status: 409, statusText: 'Conflict' });

            await addPromise;
        });

        it('should handle 403 permission error when adding allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.addAllowedRoleData = { role: 'new-role', isDefault: false, availableToForeignOrgs: false };

            const addPromise = component.addAllowedRole('test_client_1');

            const addReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles');
            addReq.flush({}, { status: 403, statusText: 'Forbidden' });

            await addPromise;
        });

        it('should handle 404 error when adding allowed role to non-existent client', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.addAllowedRoleData = { role: 'new-role', isDefault: false, availableToForeignOrgs: false };

            const addPromise = component.addAllowedRole('test_client_1');

            const addReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles');
            addReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

            await addPromise;
        });

        it('should start editing an allowed role', () => {
            component.startEditAllowedRole('viewer', true, true);
            expect(component.editingAllowedRole).toBe('viewer');
            expect(component.editAllowedRoleData.isDefault).toBe(true);
            expect(component.showAddAllowedRoleForm).toBe(false);
        });

        it('should cancel editing an allowed role', () => {
            component.startEditAllowedRole('viewer', true, true);
            component.cancelEditAllowedRole();
            expect(component.editingAllowedRole).toBeNull();
            expect(component.editAllowedRoleData.isDefault).toBe(false);
            expect(component.editAllowedRoleData.availableToForeignOrgs).toBe(false);
        });

        it('should update an allowed role successfully', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'editor';
            component.editAllowedRoleData = { isDefault: true, availableToForeignOrgs: false };

            component.updateAllowedRole('test_client_1', 'editor', false, false);
            await Promise.resolve(); TestBed.flushEffects();

            const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            expect(updateReq.request.method).toBe('PUT');
            expect(updateReq.request.body).toEqual({ isDefault: true, availableToForeignOrgs: false });
            updateReq.flush({ clientId: 'test_client_1', role: 'editor', isDefault: true, availableToForeignOrgs: false });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            reloadReq.flush([
                { clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true },
                { clientId: 'test_client_1', role: 'editor', isDefault: true, availableToForeignOrgs: false }
            ]);
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.editingAllowedRole).toBeNull();
        });

        it('should handle 403 permission error when updating allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'editor';
            component.editAllowedRoleData = { isDefault: true, availableToForeignOrgs: false };

            const updatePromise = component.updateAllowedRole('test_client_1', 'editor', false, false);

            const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            updateReq.flush({}, { status: 403, statusText: 'Forbidden' });

            await updatePromise;
        });

        it('should handle 404 error when updating non-existent allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'missing';
            component.editAllowedRoleData = { isDefault: true, availableToForeignOrgs: false };

            const updatePromise = component.updateAllowedRole('test_client_1', 'missing', false, false);

            const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/missing');
            updateReq.flush({ error: 'Role not found in allowlist' }, { status: 404, statusText: 'Not Found' });

            await updatePromise;
        });

        it('should remove an allowed role successfully', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = mockAllowedRoles;

            component.removeAllowedRole('test_client_1', 'editor');
            await Promise.resolve(); TestBed.flushEffects();

            const removeReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            expect(removeReq.request.method).toBe('DELETE');
            removeReq.flush(null, { status: 204, statusText: 'No Content' });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            reloadReq.flush([{ clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true }]);
            await Promise.resolve(); TestBed.flushEffects();
        });

        it('should not remove allowed role if user cancels confirmation', async () => {
            confirmService.confirm.mockResolvedValue(false);
            component.viewingAllowedRolesFor = 'test_client_1';

            await component.removeAllowedRole('test_client_1', 'editor');

            httpMock.expectNone('/api/clients/test_client_1/allowed-roles/editor');
        });

        it('should handle 403 permission error when removing allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';

            const removePromise = component.removeAllowedRole('test_client_1', 'editor');

            await Promise.resolve();

            const removeReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            removeReq.flush({}, { status: 403, statusText: 'Forbidden' });

            await removePromise;
        });

        it('should handle 404 error when removing non-existent allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';

            const removePromise = component.removeAllowedRole('test_client_1', 'non-existent');

            await Promise.resolve();

            const removeReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/non-existent');
            removeReq.flush({ error: 'Role not found in allowlist' }, { status: 404, statusText: 'Not Found' });

            await removePromise;
        });

        it('should handle generic error when removing allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';

            const removePromise = component.removeAllowedRole('test_client_1', 'editor');

            await Promise.resolve();

            const removeReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            removeReq.flush({}, { status: 500, statusText: 'Internal Server Error' });

            await removePromise;
        });
    });

    describe('Client-to-Client (M2M) Role Management', () => {
        const mockClient = mockClients[0];
        const mockClientRolesResponse = {
            srcClientId: 'test_client_1',
            roles: [
                { targetClientId: 'target_client_1', role: 'api-reader', createdAt: '2024-01-01T00:00:00Z' },
                { targetClientId: 'target_client_2', role: 'api-writer', createdAt: '2024-01-02T00:00:00Z' }
            ]
        };

        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
        });

        describe('toggleClientRolesView', () => {
            it('should toggle client roles view and load roles', async () => {
                expect(component.viewingClientRolesFor).toBeNull();
                expect(component.clientRoles).toEqual([]);

                const togglePromise = component.toggleClientRolesView(mockClient);

                const rolesReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                rolesReq.flush(mockClientRolesResponse);

                await togglePromise;

                expect(component.viewingClientRolesFor).toBe('test_client_1');
                expect(component.clientRoles.length).toBe(2);
                expect(component.clientRoles[0].targetClientId).toBe('target_client_1');
                expect(component.clientRoles[0].role).toBe('api-reader');
                expect(component.clientRolesLoading()).toBe(false);
            });

            it('should hide client roles view when toggling again', async () => {
                component.viewingClientRolesFor = 'test_client_1';
                component.clientRoles = [{ targetClientId: 'target_1', role: 'reader', createdAt: '' }];

                await component.toggleClientRolesView(mockClient);

                expect(component.viewingClientRolesFor).toBeNull();
                expect(component.clientRoles).toEqual([]);
                expect(component.showAddClientRoleForm).toBe(false);
            });

            it('should handle error when loading client roles', async () => {
                const togglePromise = component.toggleClientRolesView(mockClient);

                const rolesReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                rolesReq.flush({ error: 'Failed to load' }, { status: 500, statusText: 'Internal Server Error' });

                await togglePromise;

                expect(component.clientRolesError).toBe('Failed to load client roles');
                expect(component.clientRolesLoading()).toBe(false);
            });
        });

        describe('toggleAddClientRoleForm', () => {
            it('should toggle add client role form', () => {
                expect(component.showAddClientRoleForm).toBe(false);

                component.toggleAddClientRoleForm();
                expect(component.showAddClientRoleForm).toBe(true);
                expect(component.addClientRoleData.targetClientId).toBe('');
                expect(component.addClientRoleData.role).toBe('');

                component.toggleAddClientRoleForm();
                expect(component.showAddClientRoleForm).toBe(false);
            });
        });

        describe('addClientRole', () => {
            it('should add a client role successfully', async () => {
                component.viewingClientRolesFor = 'test_client_1';
                component.clientRoles = [];
                component.addClientRoleData = { targetClientId: 'target_client_1', role: 'new-role' };

                component.addClientRole('test_client_1');
                await Promise.resolve(); TestBed.flushEffects();

                const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                expect(addReq.request.method).toBe('POST');
                expect(addReq.request.body).toEqual({ targetClientId: 'target_client_1', role: 'new-role' });
                addReq.flush({ targetClientId: 'target_client_1', role: 'new-role', createdAt: '2024-01-03T00:00:00Z' });
                await Promise.resolve(); TestBed.flushEffects();
                await Promise.resolve(); TestBed.flushEffects();

                const reloadReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                reloadReq.flush({ srcClientId: 'test_client_1', roles: [{ targetClientId: 'target_client_1', role: 'new-role', createdAt: '2024-01-03T00:00:00Z' }] });
                await Promise.resolve(); TestBed.flushEffects();

                expect(component.showAddClientRoleForm).toBe(false);
                expect(component.addClientRoleData).toEqual({ targetClientId: '', role: '' });
            });

            it('should reject empty target client ID', async () => {
                component.addClientRoleData = { targetClientId: '', role: 'some-role' };
                await component.addClientRole('test_client_1');
                httpMock.expectNone('/api/clients/test_client_1/client-roles');
            });

            it('should reject empty role', async () => {
                component.addClientRoleData = { targetClientId: 'target_1', role: '' };
                await component.addClientRole('test_client_1');
                httpMock.expectNone('/api/clients/test_client_1/client-roles');
            });

            it('should handle 400 error when role not in target catalog', async () => {
                component.viewingClientRolesFor = 'test_client_1';
                component.showAddClientRoleForm = true;
                component.addClientRoleData = { targetClientId: 'target_1', role: 'unauthorized-role' };

                component.addClientRole('test_client_1');
                await Promise.resolve(); TestBed.flushEffects();

                const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                addReq.flush({ error: "Role 'unauthorized-role' is not in the target client's allowed roles catalog" }, { status: 400, statusText: 'Bad Request' });
                await Promise.resolve(); TestBed.flushEffects();
                await Promise.resolve(); TestBed.flushEffects(); // Extra tick for promise resolution

                expect(component.showAddClientRoleForm).toBe(true);
            });

            it('should handle 409 error for duplicate role', async () => {
                component.viewingClientRolesFor = 'test_client_1';
                component.addClientRoleData = { targetClientId: 'target_1', role: 'existing-role' };

                component.addClientRole('test_client_1');
                await Promise.resolve(); TestBed.flushEffects();

                const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                addReq.flush({ error: 'Role already assigned for this target client' }, { status: 409, statusText: 'Conflict' });
                await Promise.resolve(); TestBed.flushEffects();
            });

            it('should handle 404 error when client not found', async () => {
                component.viewingClientRolesFor = 'test_client_1';
                component.addClientRoleData = { targetClientId: 'target_1', role: 'some-role' };

                component.addClientRole('test_client_1');
                await Promise.resolve(); TestBed.flushEffects();

                const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                addReq.flush({ error: 'Source or target client not found' }, { status: 404, statusText: 'Not Found' });
                await Promise.resolve(); TestBed.flushEffects();
            });
        });

        describe('removeClientRole', () => {
            it('should remove a client role successfully when confirmed', async () => {
                confirmService.confirm.mockResolvedValue(true);
                component.viewingClientRolesFor = 'test_client_1';
                component.clientRoles = [{ targetClientId: 'target_1', role: 'reader', createdAt: '' }];

                component.removeClientRole('test_client_1', 'target_1', 'reader');
                await Promise.resolve(); TestBed.flushEffects();

                const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/reader');
                expect(removeReq.request.method).toBe('DELETE');
                removeReq.flush({});
                await Promise.resolve(); TestBed.flushEffects();
                await Promise.resolve(); TestBed.flushEffects();

                const reloadReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
                reloadReq.flush({ srcClientId: 'test_client_1', roles: [] });
                await Promise.resolve(); TestBed.flushEffects();
            });

            it('should not remove when user cancels confirmation', async () => {
                confirmService.confirm.mockResolvedValue(false);

                component.removeClientRole('test_client_1', 'target_1', 'reader');
                await Promise.resolve(); TestBed.flushEffects();
                await Promise.resolve(); TestBed.flushEffects(); // Extra tick for promise resolution

                httpMock.expectNone('/api/clients/test_client_1/client-roles/target_1/reader');
            });

            it('should handle 403 error when removing client role', async () => {
                confirmService.confirm.mockResolvedValue(true);
                component.viewingClientRolesFor = 'test_client_1';

                component.removeClientRole('test_client_1', 'target_1', 'reader');
                await Promise.resolve(); TestBed.flushEffects();

                const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/reader');
                removeReq.flush({}, { status: 403, statusText: 'Forbidden' });
                await Promise.resolve(); TestBed.flushEffects();
            });

            it('should handle 404 error when role assignment not found', async () => {
                confirmService.confirm.mockResolvedValue(true);
                component.viewingClientRolesFor = 'test_client_1';

                component.removeClientRole('test_client_1', 'target_1', 'non-existent');
                await Promise.resolve(); TestBed.flushEffects();

                const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/non-existent');
                removeReq.flush({ error: 'Role assignment not found' }, { status: 404, statusText: 'Not Found' });
                await Promise.resolve(); TestBed.flushEffects();
            });
        });
    });

    describe('Filtering', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should return all clients when filter is empty', () => {
            component.onFilterChange('');
            expect(component.filteredClients.length).toBe(2);
        });

        it('should filter by client name', () => {
            component.onFilterChange('Test Client 1');
            expect(component.filteredClients.length).toBe(1);
            expect(component.filteredClients[0].clientId).toBe('test_client_1');
        });

        it('should filter by client id', () => {
            component.onFilterChange('test_client_2');
            expect(component.filteredClients.length).toBe(1);
            expect(component.filteredClients[0].clientId).toBe('test_client_2');
        });

        it('should filter by client type', () => {
            component.onFilterChange('confidential');
            expect(component.filteredClients.length).toBe(2);
        });

        it('should filter by redirect URI', () => {
            component.onFilterChange('4000');
            expect(component.filteredClients.length).toBe(1);
            expect(component.filteredClients[0].clientId).toBe('test_client_2');
        });

        it('should filter by allowed scope', () => {
            component.onFilterChange('admin');
            expect(component.filteredClients.length).toBe(1);
            expect(component.filteredClients[0].clientId).toBe('test_client_2');
        });

        it('should return no results when filter does not match', () => {
            component.onFilterChange('nonexistent');
            expect(component.filteredClients.length).toBe(0);
        });
    });

    describe('Form helpers', () => {
        let clipboardSpy: ReturnType<typeof vi.fn>;

        beforeEach(() => {
            clipboardSpy = vi.fn();
            Object.defineProperty(navigator, 'userAgent', {
                get: () => 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36',
                configurable: true
            });
            Object.defineProperty(navigator, 'clipboard', {
                get: () => ({ writeText: clipboardSpy }),
                configurable: true
            });

            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        afterEach(() => {
            Object.defineProperty(navigator, 'clipboard', {
                get: () => undefined,
                configurable: true
            });
        });

        it('should clear autoSubscribe when publik is unchecked', () => {
            component.formData.publik = false;
            component.formData.autoSubscribe = true;
            component.onPublikChange();
            expect(component.formData.autoSubscribe).toBe(false);
        });

        it('should keep autoSubscribe when publik remains checked', () => {
            component.formData.publik = true;
            component.formData.autoSubscribe = true;
            component.onPublikChange();
            expect(component.formData.autoSubscribe).toBe(true);
        });

        it('should copy client id to clipboard', async () => {
            clipboardSpy.mockResolvedValue(undefined);
            component.newClientId = 'new_client_id';
            component.copyClientId();
            await Promise.resolve();
            expect(clipboardSpy).toHaveBeenCalledWith('new_client_id');
            expect(component.clientIdCopied()).toBe(true);
        });

        it('should handle error copying client id', async () => {
            clipboardSpy.mockRejectedValue(new Error('Clipboard denied'));
            component.newClientId = 'new_client_id';
            component.copyClientId();
            await Promise.resolve();
            expect(component.clientIdCopied()).toBe(false);
        });

        it('should copy secret to clipboard', async () => {
            clipboardSpy.mockResolvedValue(undefined);
            component.newClientSecret = 'super_secret';
            component.copySecret();
            await Promise.resolve();
            expect(clipboardSpy).toHaveBeenCalledWith('super_secret');
            expect(component.secretCopied()).toBe(true);
        });

        it('should handle error copying secret', async () => {
            clipboardSpy.mockRejectedValue(new Error('Clipboard denied'));
            component.newClientSecret = 'super_secret';
            component.copySecret();
            await Promise.resolve();
            expect(component.secretCopied()).toBe(false);
        });

        it('should close secret dialog and reset copied state', () => {
            component.newClientSecret = 'secret';
            component.newClientId = 'id';
            component.newClientName = 'name';
            component.clientIdCopied.set(true);
            component.secretCopied.set(true);
            component.closeSecretDialog();
            expect(component.newClientSecret).toBeNull();
            expect(component.newClientId).toBeNull();
            expect(component.newClientName).toBeNull();
            expect(component.clientIdCopied()).toBe(false);
            expect(component.secretCopied()).toBe(false);
        });
    });

    describe('Client Submission Edge Cases', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should show secret dialog when clientSecret is returned', async () => {
            component.formData = {
                clientId: 'secret_client',
                clientName: 'Secret Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: false,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({
                id: '3',
                orgId: 'test-org',
                clientId: 'secret_client',
                clientName: 'Secret Client',
                clientType: 'confidential',
                redirectUris: '["http://localhost:3000/callback"]',
                allowedScopes: '["openid"]',
                requirePkce: true,
                autoSubscribe: false,
                publik: false,
                createdAt: '2024-01-03T00:00:00Z',
                clientSecret: 'generated-secret'
            });

            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients');
            reloadReq.flush([...mockClients]);

            await submitPromise;

            expect(component.newClientSecret).toBe('generated-secret');
            expect(component.newClientId).toBe('secret_client');
            expect(component.newClientName).toBe('Secret Client');
        });

        it('should handle 400 with validation violations', async () => {
            component.formData = {
                clientId: 'new_client',
                clientName: 'New Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: false,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({
                violations: [{ message: 'Invalid client ID' }, { message: 'Name too short' }]
            }, { status: 400, statusText: 'Bad Request' });

            await submitPromise;

            expect(component.formError).toBe('Invalid client ID; Name too short');
            expect(component.formSubmitting).toBe(false);
        });

        it('should handle 400 with error object', async () => {
            component.formData = {
                clientId: 'new_client',
                clientName: 'New Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: false,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({ error: 'Bad request' }, { status: 400, statusText: 'Bad Request' });

            await submitPromise;

            expect(component.formError).toBe('Bad request');
        });

        it('should handle 400 with generic error', async () => {
            component.formData = {
                clientId: 'new_client',
                clientName: 'New Client',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: false,
                publik: false
            };

            const submitPromise = component.onSubmit();

            const createReq = httpMock.expectOne('/api/clients');
            createReq.flush({}, { status: 400, statusText: 'Bad Request' });

            await submitPromise;

            expect(component.formError).toBe('Invalid input. Please check your entries.');
        });

        it('should handle 400 error when deleting client', async () => {
            const deletePromise = component.deleteClient(mockClients[0]);
            await Promise.resolve();
            const deleteReq = httpMock.expectOne('/api/clients/test_client_1');
            deleteReq.flush({ error: 'Cannot delete client with active secrets' }, { status: 400, statusText: 'Bad Request' });
            await deletePromise;
        });
    });

    describe('Secret Management', () => {
        const mockSecret: ClientSecret = {
            id: 1,
            description: 'Test Secret',
            createdAt: '2024-01-01T00:00:00Z',
            expiresAt: null,
            active: true
        };

        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should toggle secrets view open and load secrets', async () => {
            const togglePromise = component.toggleSecretsView(mockClients[0]);
            const secretsReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            secretsReq.flush([mockSecret]);
            await togglePromise;
            expect(component.viewingSecretsFor).toBe('test_client_1');
            expect(component.clientSecrets.length).toBe(1);
            expect(component.showCreateSecretForm).toBe(false);
        });

        it('should toggle secrets view closed', async () => {
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [mockSecret];
            component.showCreateSecretForm = true;
            await component.toggleSecretsView(mockClients[0]);
            expect(component.viewingSecretsFor).toBeNull();
            expect(component.clientSecrets.length).toBe(0);
            expect(component.showCreateSecretForm).toBe(false);
        });

        it('should handle error loading secrets', async () => {
            const togglePromise = component.toggleSecretsView(mockClients[0]);
            const secretsReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            secretsReq.flush({}, { status: 500, statusText: 'Server Error' });
            await togglePromise;
            expect(component.secretsError).toBe('Failed to load secrets');
            expect(component.secretsLoading()).toBe(false);
        });

        it('should toggle create secret form', () => {
            expect(component.showCreateSecretForm).toBe(false);
            component.toggleCreateSecretForm();
            expect(component.showCreateSecretForm).toBe(true);
            expect(component.createSecretData.description).toBe('');
            component.toggleCreateSecretForm();
            expect(component.showCreateSecretForm).toBe(false);
        });

        it('should create secret successfully', async () => {
            component.viewingSecretsFor = 'test_client_1';
            component.createSecretData = { description: 'Production secret', expiresInDays: 90 };

            const createPromise = component.createSecret('test_client_1');
            await Promise.resolve(); TestBed.flushEffects();

            const createReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            expect(createReq.request.body).toEqual({ description: 'Production secret', expiresInDays: 90 });
            createReq.flush({
                id: 2,
                secret: 'new-secret-value',
                description: 'Production secret',
                createdAt: '2024-01-02T00:00:00Z',
                expiresAt: '2024-04-02T00:00:00Z'
            });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            reloadReq.flush([mockSecret, { id: 2, description: 'Production secret', createdAt: '2024-01-02T00:00:00Z', expiresAt: '2024-04-02T00:00:00Z', active: true }]);
            await Promise.resolve(); TestBed.flushEffects();

            await createPromise;

            expect(component.newClientSecret).toBe('new-secret-value');
            expect(component.showCreateSecretForm).toBe(false);
            expect(component.createSecretData.description).toBe('');
        });

        it('should reject creating secret without description', async () => {
            component.createSecretData = { description: '', expiresInDays: null };
            await component.createSecret('test_client_1');
            httpMock.expectNone('/api/clients/test_client_1/secrets');
        });

        it('should keep form open on error creating secret', async () => {
            component.viewingSecretsFor = 'test_client_1';
            component.showCreateSecretForm = true;
            component.createSecretData = { description: 'Test', expiresInDays: null };

            component.createSecret('test_client_1');
            await Promise.resolve(); TestBed.flushEffects();

            const createReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            createReq.flush({}, { status: 403, statusText: 'Forbidden' });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.showCreateSecretForm).toBe(true);
        });

        it('should revoke secret successfully', async () => {
            component.viewingSecretsFor = 'test_client_1';
            const revokePromise = component.revokeSecret('test_client_1', mockSecret);
            await Promise.resolve();
            const revokeReq = httpMock.expectOne('/api/clients/test_client_1/secrets/1');
            revokeReq.flush({});
            await Promise.resolve();
            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            reloadReq.flush([{ ...mockSecret, active: false }]);
            await revokePromise;
            expect(component.clientSecrets[0].active).toBe(false);
        });

        it('should not revoke secret when cancelled', async () => {
            confirmService.confirm.mockResolvedValue(false);
            await component.revokeSecret('test_client_1', mockSecret);
            httpMock.expectNone('/api/clients/test_client_1/secrets/1');
        });

        it('should handle 400 error when revoking secret', async () => {
            const revokePromise = component.revokeSecret('test_client_1', mockSecret);
            await Promise.resolve();
            const revokeReq = httpMock.expectOne('/api/clients/test_client_1/secrets/1');
            revokeReq.flush({}, { status: 400, statusText: 'Bad Request' });
            await revokePromise;
        });

        it('should handle 404 error when revoking secret', async () => {
            const revokePromise = component.revokeSecret('test_client_1', mockSecret);
            await Promise.resolve();
            const revokeReq = httpMock.expectOne('/api/clients/test_client_1/secrets/1');
            revokeReq.flush({}, { status: 404, statusText: 'Not Found' });
            await revokePromise;
        });

        it('should delete secret successfully', async () => {
            const inactiveSecret = { ...mockSecret, active: false };
            component.viewingSecretsFor = 'test_client_1';
            const deletePromise = component.deleteSecret('test_client_1', inactiveSecret);
            await Promise.resolve();
            const deleteReq = httpMock.expectOne('/api/clients/test_client_1/secrets/1/permanent');
            deleteReq.flush({});
            await Promise.resolve();
            await Promise.resolve();
            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            reloadReq.flush([]);
            await deletePromise;
            expect(component.clientSecrets.length).toBe(0);
        });

        it('should not delete secret when cancelled', async () => {
            confirmService.confirm.mockResolvedValue(false);
            await component.deleteSecret('test_client_1', { ...mockSecret, active: false });
            httpMock.expectNone('/api/clients/test_client_1/secrets/1/permanent');
        });

        it('should handle 400 error when deleting secret', async () => {
            const deletePromise = component.deleteSecret('test_client_1', { ...mockSecret, active: false });
            await Promise.resolve();
            const deleteReq = httpMock.expectOne('/api/clients/test_client_1/secrets/1/permanent');
            deleteReq.flush({}, { status: 400, statusText: 'Bad Request' });
            await deletePromise;
        });

        it('should handle 404 error when deleting secret', async () => {
            const deletePromise = component.deleteSecret('test_client_1', { ...mockSecret, active: false });
            await Promise.resolve();
            const deleteReq = httpMock.expectOne('/api/clients/test_client_1/secrets/1/permanent');
            deleteReq.flush({}, { status: 404, statusText: 'Not Found' });
            await deletePromise;
        });
    });

    describe('Role Utilities', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should allow role management when no scopes configured', () => {
            const client = { ...mockClients[0], allowedScopes: '[]' };
            expect(component.canManageRoles(client)).toBe(true);
        });

        it('should disallow role management when scopes configured', () => {
            expect(component.canManageRoles(mockClients[0])).toBe(false);
        });

        it('should strip valid UUID org prefix for group name', () => {
            const uuid = '12345678-1234-1234-1234-123456789012';
            const clientId = `${uuid}__my_client`;
            expect(component.groupNameFor(clientId, 'admin')).toBe('my_client_admin');
        });

        it('should keep client id unchanged when no valid prefix', () => {
            expect(component.groupNameFor('plain_client', 'admin')).toBe('plain_client_admin');
            expect(component.groupNameFor('short__client', 'admin')).toBe('short__client_admin');
        });

        it('should format date', () => {
            const formatted = component.formatDate('2024-01-01T00:00:00Z');
            expect(formatted).toContain('2024');
            expect(formatted).toContain(':'); // time portion present
        });
    });

    describe('Target Client Selection', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should reset role selection when no target client selected', async () => {
            component.addClientRoleData.role = 'some-role';
            component.availableClientRoleRoles = [{ clientId: 'x', role: 'reader', isDefault: false, availableToForeignOrgs: false }];
            await component.onTargetClientSelected('');
            expect(component.addClientRoleData.role).toBe('');
            expect(component.availableClientRoleRoles.length).toBe(0);
        });

        it('should load available roles for target client', async () => {
            const targetPromise = component.onTargetClientSelected('test_client_2');
            await Promise.resolve(); TestBed.flushEffects();
            const rolesReq = httpMock.expectOne('/api/clients/test_client_2/allowed-roles');
            rolesReq.flush([{ clientId: 'test_client_2', role: 'reader', isDefault: false, availableToForeignOrgs: false }]);
            await Promise.resolve(); TestBed.flushEffects();
            await targetPromise;
            expect(component.availableClientRoleRoles.length).toBe(1);
            expect(component.loadingClientRoleRoles()).toBe(false);
        });

        it('should handle error loading target client roles', async () => {
            const targetPromise = component.onTargetClientSelected('test_client_2');
            await Promise.resolve(); TestBed.flushEffects();
            const rolesReq = httpMock.expectOne('/api/clients/test_client_2/allowed-roles');
            rolesReq.flush({}, { status: 500, statusText: 'Server Error' });
            await Promise.resolve(); TestBed.flushEffects();
            await targetPromise;
            expect(component.availableClientRoleRoles.length).toBe(0);
            expect(component.loadingClientRoleRoles()).toBe(false);
        });
    });

    describe('Additional Role Error Handling', () => {
        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should handle 403 error when adding client role', async () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.addClientRoleData = { targetClientId: 'target_1', role: 'some-role' };
            component.addClientRole('test_client_1');
            await Promise.resolve(); TestBed.flushEffects();
            const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
            addReq.flush({}, { status: 403, statusText: 'Forbidden' });
            await Promise.resolve(); TestBed.flushEffects();
        });

        it('should handle generic error when adding client role', async () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.addClientRoleData = { targetClientId: 'target_1', role: 'some-role' };
            component.addClientRole('test_client_1');
            await Promise.resolve(); TestBed.flushEffects();
            const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
            addReq.flush({}, { status: 500, statusText: 'Server Error' });
            await Promise.resolve(); TestBed.flushEffects();
        });

        it('should handle generic error when removing client role', async () => {
            confirmService.confirm.mockResolvedValue(true);
            component.viewingClientRolesFor = 'test_client_1';
            component.removeClientRole('test_client_1', 'target_1', 'reader');
            await Promise.resolve(); TestBed.flushEffects();
            const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/reader');
            removeReq.flush({}, { status: 500, statusText: 'Server Error' });
            await Promise.resolve(); TestBed.flushEffects();
        });

        it('should handle generic error when adding allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.addAllowedRoleData = { role: 'new-role', isDefault: false, availableToForeignOrgs: false };
            component.addAllowedRole('test_client_1');
            await Promise.resolve(); TestBed.flushEffects();
            const addReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles');
            addReq.flush({}, { status: 500, statusText: 'Server Error' });
            await Promise.resolve(); TestBed.flushEffects();
        });

        it('should confirm retraction before updating allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'editor';
            component.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
            const updatePromise = component.updateAllowedRole('test_client_1', 'editor', true, false);
            await Promise.resolve();
            expect(confirmService.confirm).toHaveBeenCalled();
            const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            updateReq.flush({});
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            reloadReq.flush([]);
            await Promise.resolve(); TestBed.flushEffects();
            await updatePromise;
            expect(component.editingAllowedRole).toBeNull();
        });

        it('should cancel update if retraction is declined', async () => {
            confirmService.confirm.mockResolvedValue(false);
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'editor';
            component.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
            await component.updateAllowedRole('test_client_1', 'editor', true, false);
            httpMock.expectNone('/api/clients/test_client_1/allowed-roles/editor');
        });

        it('should show info toast when removing default status', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'editor';
            component.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
            const updatePromise = component.updateAllowedRole('test_client_1', 'editor', false, true);
            await Promise.resolve(); TestBed.flushEffects();
            const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            updateReq.flush({});
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            reloadReq.flush([]);
            await Promise.resolve(); TestBed.flushEffects();
            await updatePromise;
            expect(component.editingAllowedRole).toBeNull();
        });

        it('should handle generic error when updating allowed role', async () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.editingAllowedRole = 'editor';
            component.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
            const updatePromise = component.updateAllowedRole('test_client_1', 'editor', false, false);
            await Promise.resolve(); TestBed.flushEffects();
            const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
            updateReq.flush({}, { status: 500, statusText: 'Server Error' });
            await Promise.resolve(); TestBed.flushEffects();
            await updatePromise;
        });
    });

    describe('Template Rendering', () => {
        beforeEach(() => {
            const authService = TestBed.inject(AuthService);
            const token = {
                ...authService['token'],
                groups: [ROLE_MANAGE_CLIENTS],
                orgId: 'test-org'
            };
            authService['token'] = token;
            authService.token$.set(token);

            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should render client secret modal', () => {
            component.newClientSecret = 'secret';
            component.newClientId = 'client_id';
            component.newClientName = 'Client Name';
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Client Secret Created');
            expect(compiled.textContent).toContain('secret');
        });

        it('should render add client form', () => {
            component.showForm = true;
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('#clientId')).toBeTruthy();
            expect(compiled.querySelector('#clientName')).toBeTruthy();
        });

        it('should render edit client form', () => {
            component.editingClientId = '1';
            component.formData = {
                clientId: 'test_client_1',
                clientName: 'Test',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: false,
                publik: false
            };
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Edit Client');
            expect(compiled.querySelector('#edit-clientName')).toBeTruthy();
        });

        it('should render external badge for foreign org client', () => {
            const foreignClient = { ...mockClients[0], orgId: 'other-org' };
            TestBed.inject(ModelService).setClients([foreignClient]);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('External');
        });

        it('should render secrets management section', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Secret', createdAt: '2024-01-01T00:00:00Z', expiresAt: null, active: true }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Client Secrets');
            expect(compiled.textContent).toContain('Secret');
        });

        it('should render create secret form', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.showCreateSecretForm = true;
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Generate New Secret');
            expect(compiled.querySelector('#secret-description')).toBeTruthy();
        });

        it('should render client-to-client roles section', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.clientRoles = [
                { targetClientId: 'target_1', role: 'reader', createdAt: '2024-01-01T00:00:00Z' }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Client-to-Client Roles');
            expect(compiled.textContent).toContain('target_1');
            expect(compiled.textContent).toContain('reader');
        });

        it('should render allowed roles section with edit panel', () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = [
                { clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true }
            ];
            component.editingAllowedRole = 'viewer';
            component.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Allowed Roles');
            expect(compiled.textContent).toContain('viewer');
        });

        it('should render inactive secret with delete button', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Inactive', createdAt: '2024-01-01T00:00:00Z', expiresAt: null, active: false }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Revoked');
            expect(compiled.textContent).toContain('Delete');
        });

        it('should render expiring secret warning', () => {
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 5);
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Expiring', createdAt: '2024-01-01T00:00:00Z', expiresAt: tomorrow.toISOString(), active: true }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('will expire soon');
        });

        it('should render auto-subscribe Yes when public and enabled', () => {
            const publicClient = { ...mockClients[0], publik: true, autoSubscribe: true };
            TestBed.inject(ModelService).setClients([publicClient]);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const rows = compiled.querySelectorAll('.detail-row');
            let autoSubscribeText = '';
            rows.forEach((row: Element) => {
                if (row.textContent?.includes('Auto-subscribe')) {
                    autoSubscribeText = row.textContent || '';
                }
            });
            expect(autoSubscribeText).toContain('Yes');
        });

        it('should render create client form error', () => {
            component.showForm = true;
            component.formError = 'Failed to create client';
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Failed to create client');
        });

        it('should render edit client form error', () => {
            component.editingClientId = '1';
            component.formData = {
                clientId: 'test_client_1',
                clientName: 'Test',
                clientType: 'confidential',
                redirectUris: 'http://localhost:3000/callback',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: false,
                publik: false
            };
            component.formError = 'Failed to update client';
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Failed to update client');
        });

        it('should render no-scope message', () => {
            const noScopeClient = { ...mockClients[0], allowedScopes: '[]' };
            TestBed.inject(ModelService).setClients([noScopeClient]);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('None (role-based authorization only)');
        });

        it('should render no filter results message', () => {
            component.onFilterChange('nonexistent');
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('No clients match your filter criteria');
        });

        it('should render manage secrets button', () => {
            const compiled = fixture.nativeElement;
            const buttons = compiled.querySelectorAll('.secret-management button');
            expect(buttons.length).toBe(2);
            expect(compiled.textContent).toContain('Manage Secrets');
        });

        it('should render revoke secret button for active secret', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Active', createdAt: '2024-01-01T00:00:00Z', expiresAt: null, active: true }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Revoke');
        });

        it('should render expired secret warning', () => {
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Expired', createdAt: '2024-01-01T00:00:00Z', expiresAt: yesterday.toISOString(), active: true }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('has expired');
        });

        it('should render manage client roles button', () => {
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[data-testid="manage-client-roles-btn"]');
            expect(button).toBeTruthy();
            expect(button?.textContent).toContain('Manage Client To Client Roles');
        });

        it('should render add client role form with target options', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.showAddClientRoleForm = true;
            component.availableTargetClients = [
                { ...mockClients[0], clientId: 'target_1', clientName: 'Target Client' }
            ];
            component.loadingTargetClients.set(false);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const options = compiled.querySelectorAll('#target-client-select option');
            expect(options.length).toBe(2);
            expect(compiled.textContent).toContain('Target Client');
        });

        it('should render no client roles available message', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.showAddClientRoleForm = true;
            component.addClientRoleData.targetClientId = 'target_1';
            component.availableClientRoleRoles = [];
            component.loadingClientRoleRoles.set(false);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('[data-testid="no-roles-available"]')).toBeTruthy();
        });

        it('should render add client role button', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.showAddClientRoleForm = true;
            component.addClientRoleData = { targetClientId: 'target_1', role: 'reader' };
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[data-testid="submit-add-client-role-btn"]');
            expect(button).toBeTruthy();
            expect(button?.textContent).toContain('Add Role');
        });

        it('should render manage allowed roles button', () => {
            const compiled = fixture.nativeElement;
            const buttons = Array.from(compiled.querySelectorAll('.role-management button')) as HTMLElement[];
            const allowedRolesButton = buttons.find(b => b.textContent?.includes('Manage Allowed Roles'));
            expect(allowedRolesButton).toBeTruthy();
        });

        it('should render add allowed role form', () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.showAddAllowedRoleForm = true;
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('#allowed-role-name')).toBeTruthy();
            expect(compiled.textContent).toContain('Add Allowed Role');
        });

        it('should render remove allowed role button', () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = [
                { clientId: 'test_client_1', role: 'viewer', isDefault: false, availableToForeignOrgs: false }
            ];
            component.editingAllowedRole = null;
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[title="Remove role"]');
            expect(button).toBeTruthy();
        });
    });

    describe('Management UI branches', () => {
        beforeEach(async () => {
            TestBed.resetTestingModule();

            const routerSpy = createMock<Router>({
                navigate: vi.fn().mockName("Router.navigate"),
                createUrlTree: vi.fn().mockName("Router.createUrlTree"),
                serializeUrl: vi.fn().mockName("Router.serializeUrl"),
                events: EMPTY
            });
            routerSpy.createUrlTree.mockReturnValue({} as any);
            routerSpy.serializeUrl.mockReturnValue('');

            const confirmServiceSpy = createMock<ConfirmDialogService>({
                confirm: vi.fn().mockName("ConfirmDialogService.confirm")
            });
            confirmServiceSpy.confirm.mockResolvedValue(true);

            const authServiceMock = {
                token$: vi.fn().mockReturnValue({ orgId: 'test-org', groups: [ROLE_MANAGE_CLIENTS] }),
                hasRole: vi.fn().mockReturnValue(true),
                signout: vi.fn()
            };

            await TestBed.configureTestingModule({
                imports: [ClientsComponent],
                providers: [
                    provideHttpClient(withXhr()),
                    provideHttpClientTesting(),
                    { provide: Router, useValue: routerSpy },
                    { provide: ConfirmDialogService, useValue: confirmServiceSpy },
                    { provide: AuthService, useValue: authServiceMock },
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            queryParams: queryParamsSubject.asObservable()
                        }
                    }
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(ClientsComponent);
            component = fixture.componentInstance;
            httpMock = TestBed.inject(HttpTestingController);
            TestBed.inject(ModelService).reset();
            confirmService = TestBed.inject(ConfirmDialogService) as MockedObject<ConfirmDialogService>;

            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should render secret management buttons', () => {
            const compiled = fixture.nativeElement;
            const buttons = compiled.querySelectorAll('.secret-management button');
            expect(buttons.length).toBe(2);
            expect(compiled.textContent).toContain('Manage Secrets');
        });

        it('should render generate secret button', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.showCreateSecretForm = false;
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('.secrets-header button');
            expect(button).toBeTruthy();
            expect(button?.textContent).toContain('Generate New Secret');
        });

        it('should render revoke secret button', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Active', createdAt: '2024-01-01T00:00:00Z', expiresAt: null, active: true }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Revoke');
        });

        it('should render delete secret button', () => {
            component.viewingSecretsFor = 'test_client_1';
            component.clientSecrets = [
                { id: 1, description: 'Inactive', createdAt: '2024-01-01T00:00:00Z', expiresAt: null, active: false }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.textContent).toContain('Delete');
        });

        it('should render manage client roles button', () => {
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[data-testid="manage-client-roles-btn"]');
            expect(button).toBeTruthy();
        });

        it('should render target client options', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.showAddClientRoleForm = true;
            component.availableTargetClients = [
                { ...mockClients[0], clientId: 'target_1', clientName: 'Target Client' }
            ];
            component.loadingTargetClients.set(false);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const options = compiled.querySelectorAll('#target-client-select option');
            expect(options.length).toBe(2);
        });

        it('should render no roles available message', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.showAddClientRoleForm = true;
            component.addClientRoleData.targetClientId = 'target_1';
            component.availableClientRoleRoles = [];
            component.loadingClientRoleRoles.set(false);
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('[data-testid="no-roles-available"]')).toBeTruthy();
        });

        it('should render add client role button', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.showAddClientRoleForm = true;
            component.addClientRoleData = { targetClientId: 'target_1', role: 'reader' };
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[data-testid="submit-add-client-role-btn"]');
            expect(button).toBeTruthy();
            expect(button?.textContent).toContain('Add Role');
        });

        it('should render remove client role button', () => {
            component.viewingClientRolesFor = 'test_client_1';
            component.clientRoles = [
                { targetClientId: 'target_1', role: 'reader', createdAt: '2024-01-01T00:00:00Z' }
            ];
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[data-testid="remove-client-role-btn"]');
            expect(button).toBeTruthy();
        });

        it('should render manage allowed roles button', () => {
            const compiled = fixture.nativeElement;
            const buttons = Array.from(compiled.querySelectorAll('.role-management button')) as HTMLElement[];
            const allowedRolesButton = buttons.find(b => b.textContent?.includes('Manage Allowed Roles'));
            expect(allowedRolesButton).toBeTruthy();
        });

        it('should render remove allowed role button', () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = [
                { clientId: 'test_client_1', role: 'viewer', isDefault: false, availableToForeignOrgs: false }
            ];
            component.editingAllowedRole = null;
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const button = compiled.querySelector('[title="Remove role"]');
            expect(button).toBeTruthy();
        });

        it('should render edit allowed role panel', () => {
            component.viewingAllowedRolesFor = 'test_client_1';
            component.allowedRoles = [
                { clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: false }
            ];
            component.editingAllowedRole = 'viewer';
            component.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('.role-edit-panel')).toBeTruthy();
        });
    });

    describe('Deep-link query params', () => {
        let scrollIntoViewSpy: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            scrollIntoViewSpy = vi.spyOn(Element.prototype, 'scrollIntoView').mockImplementation(() => {});
        });

        afterEach(() => {
            scrollIntoViewSpy.mockRestore();
        });

        beforeEach(() => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();
        });

        it('should open allowed roles view from query param', async () => {
            queryParamsSubject.next({ viewAllowedRoles: 'test_client_1' });
            component.loadClients();
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();

            vi.advanceTimersByTime(0);
            await Promise.resolve();

            const rolesReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
            rolesReq.flush([]);
            await Promise.resolve();
            vi.advanceTimersByTime(100);

            expect(component.viewingAllowedRolesFor).toBe('test_client_1');
        });

        it('should open secrets view from query param', async () => {
            queryParamsSubject.next({ viewSecrets: 'test_client_1' });
            component.loadClients();
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();

            vi.advanceTimersByTime(0);
            await Promise.resolve();

            const secretsReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            secretsReq.flush([]);
            await Promise.resolve();
            vi.advanceTimersByTime(100);

            expect(component.viewingSecretsFor).toBe('test_client_1');
        });

        it('should open secrets view and highlight secret from query param', async () => {
            queryParamsSubject.next({ viewSecrets: 'test_client_1', highlightSecret: '42' });
            component.loadClients();
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();

            vi.advanceTimersByTime(0);
            await Promise.resolve();

            const secretsReq = httpMock.expectOne('/api/clients/test_client_1/secrets');
            secretsReq.flush([]);
            await Promise.resolve();
            vi.advanceTimersByTime(100);

            expect(component.viewingSecretsFor).toBe('test_client_1');
        });

        it('should do nothing when query param client is not found', async () => {
            queryParamsSubject.next({ viewAllowedRoles: 'nonexistent' });
            component.loadClients();
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/clients');
            req.flush(mockClients);
            fixture.detectChanges();

            vi.advanceTimersByTime(0);
            await Promise.resolve();
            vi.advanceTimersByTime(100);

            expect(component.viewingAllowedRolesFor).toBeNull();
        });
    });
});