import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { ClientsComponent } from './clients.component';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';
import { AllowedRole } from '../model.service';

describe('ClientsComponent', () => {
  let component: ClientsComponent;
  let fixture: ComponentFixture<ClientsComponent>;
  let httpMock: HttpTestingController;
  let queryParamsSubject: BehaviorSubject<any>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;

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
    queryParamsSubject = new BehaviorSubject({});
    
    const routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    routerSpy.createUrlTree.and.returnValue({});
    routerSpy.serializeUrl.and.returnValue('');
    routerSpy.events = EMPTY;
    
    const confirmServiceSpy = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirmServiceSpy.confirm.and.returnValue(Promise.resolve(true)); // Default to confirming
    
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
    httpMock = TestBed.inject(HttpTestingController);
    confirmService = TestBed.inject(ConfirmDialogService) as jasmine.SpyObj<ConfirmDialogService>;
  });

  afterEach(() => {
    // Flush any pending /public/config requests from app initializer
    const configRequests = httpMock.match('/public/config');
    configRequests.forEach(req => {
      if (!req.cancelled) {
        req.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 900, insecureClientSecret: false, warningMessage: '' });
      }
    });
    
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Component Initialization', () => {
    it('should start with loading state true', () => {
      expect(component.loading).toBe(true);
    });

    it('should start with empty clients array', () => {
      expect(component.clients).toEqual([]);
    });

    it('should start with no error', () => {
      expect(component.error).toBeNull();
    });

    it('should call loadClients on init', () => {
      spyOn(component, 'loadClients');
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
      
      // Component is created but HTTP hasn't been called yet
      expect(component.loading).toBe(true);
      expect(component.error).toBeNull();
      expect(component.clients).toEqual([]);
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
      confirmService.confirm.and.returnValue(Promise.resolve(false));

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
      
      expect(component.isSecretExpiringSoon(secret)).toBeTrue();
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
      
      expect(component.isSecretExpiringSoon(secret)).toBeTrue();
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
      
      expect(component.isSecretExpiringSoon(secret)).toBeFalse();
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
      
      expect(component.isSecretExpiringSoon(secret)).toBeFalse();
    });

    it('should not detect secret without expiration as expiring soon', () => {
      const secret = {
        id: 109,
        description: 'No expiration',
        active: true,
        createdAt: '2026-02-05T20:30:58Z',
        expiresAt: null
      };
      
      expect(component.isSecretExpiringSoon(secret)).toBeFalse();
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
      
      expect(component.isSecretExpired(secret)).toBeTrue();
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
      
      expect(component.isSecretExpired(secret)).toBeFalse();
    });

    it('should not detect secret without expiration as expired', () => {
      const secret = {
        id: 112,
        description: 'No expiration',
        active: true,
        createdAt: '2026-02-05T20:30:58Z',
        expiresAt: null
      };
      
      expect(component.isSecretExpired(secret)).toBeFalse();
    });
  });

  describe('Allowed Roles Management', () => {
    const mockAllowedRoles: AllowedRole[] = [
      { clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true },
      { clientId: 'test_client_1', role: 'editor', isDefault: false, availableToForeignOrgs: false }
    ];

    it('should toggle allowed roles view', async () => {
      const togglePromise = component.toggleAllowedRolesView(mockClients[0]);

      const rolesReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
      expect(rolesReq.request.method).toBe('GET');
      rolesReq.flush(mockAllowedRoles);

      await togglePromise;

      expect(component.viewingAllowedRolesFor).toBe('test_client_1');
      expect(component.allowedRoles).toEqual(mockAllowedRoles);
      expect(component.allowedRolesLoading).toBeFalse();
    });

    it('should hide allowed roles view when toggling again', async () => {
      component.viewingAllowedRolesFor = 'test_client_1';
      component.allowedRoles = mockAllowedRoles;

      await component.toggleAllowedRolesView(mockClients[0]);

      expect(component.viewingAllowedRolesFor).toBeNull();
      expect(component.allowedRoles).toEqual([]);
      expect(component.showAddAllowedRoleForm).toBeFalse();
      expect(component.editingAllowedRole).toBeNull();
    });

    it('should handle error when loading allowed roles', async () => {
      const togglePromise = component.toggleAllowedRolesView(mockClients[0]);

      const rolesReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
      rolesReq.flush({ error: 'Failed to load' }, { status: 500, statusText: 'Internal Server Error' });

      await togglePromise;

      expect(component.allowedRolesError).toBe('Failed to load allowed roles');
      expect(component.allowedRolesLoading).toBeFalse();
    });

    it('should toggle add allowed role form', () => {
      expect(component.showAddAllowedRoleForm).toBeFalse();

      component.toggleAddAllowedRoleForm();
      expect(component.showAddAllowedRoleForm).toBeTrue();
      expect(component.addAllowedRoleData.role).toBe('');
      expect(component.addAllowedRoleData.isDefault).toBeFalse();

      component.toggleAddAllowedRoleForm();
      expect(component.showAddAllowedRoleForm).toBeFalse();
    });

    it('should add an allowed role successfully', fakeAsync(() => {
      component.viewingAllowedRolesFor = 'test_client_1';
      component.allowedRoles = [];
      component.addAllowedRoleData = { role: 'admin', isDefault: true, availableToForeignOrgs: true };

      component.addAllowedRole('test_client_1');
      tick();

      const addReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles');
      expect(addReq.request.method).toBe('POST');
      expect(addReq.request.body).toEqual({ role: 'admin', isDefault: true, availableToForeignOrgs: true });
      addReq.flush({ clientId: 'test_client_1', role: 'admin', isDefault: true, availableToForeignOrgs: true });
      tick();

      const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
      expect(reloadReq.request.method).toBe('GET');
      reloadReq.flush([...mockAllowedRoles, { clientId: 'test_client_1', role: 'admin', isDefault: true, availableToForeignOrgs: true }]);
      tick();

      expect(component.showAddAllowedRoleForm).toBeFalse();
      expect(component.addAllowedRoleData.role).toBe('');
    }));

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
      expect(component.editAllowedRoleData.isDefault).toBeTrue();
      expect(component.showAddAllowedRoleForm).toBeFalse();
    });

    it('should cancel editing an allowed role', () => {
      component.startEditAllowedRole('viewer', true, true);
      component.cancelEditAllowedRole();
      expect(component.editingAllowedRole).toBeNull();
      expect(component.editAllowedRoleData.isDefault).toBeFalse();
      expect(component.editAllowedRoleData.availableToForeignOrgs).toBeFalse();
    });

    it('should update an allowed role successfully', fakeAsync(() => {
      component.viewingAllowedRolesFor = 'test_client_1';
      component.editingAllowedRole = 'editor';
      component.editAllowedRoleData = { isDefault: true, availableToForeignOrgs: false };

      component.updateAllowedRole('test_client_1', 'editor', false, false);
      tick();

      const updateReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
      expect(updateReq.request.method).toBe('PUT');
      expect(updateReq.request.body).toEqual({ isDefault: true, availableToForeignOrgs: false });
      updateReq.flush({ clientId: 'test_client_1', role: 'editor', isDefault: true, availableToForeignOrgs: false });
      tick();

      const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
      reloadReq.flush([
        { clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true },
        { clientId: 'test_client_1', role: 'editor', isDefault: true, availableToForeignOrgs: false }
      ]);
      tick();

      expect(component.editingAllowedRole).toBeNull();
    }));

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

    it('should remove an allowed role successfully', fakeAsync(() => {
      component.viewingAllowedRolesFor = 'test_client_1';
      component.allowedRoles = mockAllowedRoles;

      component.removeAllowedRole('test_client_1', 'editor');
      tick();

      const removeReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles/editor');
      expect(removeReq.request.method).toBe('DELETE');
      removeReq.flush(null, { status: 204, statusText: 'No Content' });
      tick();

      const reloadReq = httpMock.expectOne('/api/clients/test_client_1/allowed-roles-for-users-in-clients-org');
      reloadReq.flush([{ clientId: 'test_client_1', role: 'viewer', isDefault: true, availableToForeignOrgs: true }]);
      tick();
    }));

    it('should not remove allowed role if user cancels confirmation', async () => {
      confirmService.confirm.and.returnValue(Promise.resolve(false));
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
        expect(component.clientRolesLoading).toBeFalse();
      });

      it('should hide client roles view when toggling again', async () => {
        component.viewingClientRolesFor = 'test_client_1';
        component.clientRoles = [{ targetClientId: 'target_1', role: 'reader', createdAt: '' }];

        await component.toggleClientRolesView(mockClient);

        expect(component.viewingClientRolesFor).toBeNull();
        expect(component.clientRoles).toEqual([]);
        expect(component.showAddClientRoleForm).toBeFalse();
      });

      it('should handle error when loading client roles', async () => {
        const togglePromise = component.toggleClientRolesView(mockClient);

        const rolesReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        rolesReq.flush({ error: 'Failed to load' }, { status: 500, statusText: 'Internal Server Error' });

        await togglePromise;

        expect(component.clientRolesError).toBe('Failed to load client roles');
        expect(component.clientRolesLoading).toBeFalse();
      });
    });

    describe('toggleAddClientRoleForm', () => {
      it('should toggle add client role form', () => {
        expect(component.showAddClientRoleForm).toBeFalse();

        component.toggleAddClientRoleForm();
        expect(component.showAddClientRoleForm).toBeTrue();
        expect(component.addClientRoleData.targetClientId).toBe('');
        expect(component.addClientRoleData.role).toBe('');

        component.toggleAddClientRoleForm();
        expect(component.showAddClientRoleForm).toBeFalse();
      });
    });

    describe('addClientRole', () => {
      it('should add a client role successfully', fakeAsync(() => {
        component.viewingClientRolesFor = 'test_client_1';
        component.clientRoles = [];
        component.addClientRoleData = { targetClientId: 'target_client_1', role: 'new-role' };

        component.addClientRole('test_client_1');
        tick();

        const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        expect(addReq.request.method).toBe('POST');
        expect(addReq.request.body).toEqual({ targetClientId: 'target_client_1', role: 'new-role' });
        addReq.flush({ targetClientId: 'target_client_1', role: 'new-role', createdAt: '2024-01-03T00:00:00Z' });
        tick();

        const reloadReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        reloadReq.flush({ srcClientId: 'test_client_1', roles: [{ targetClientId: 'target_client_1', role: 'new-role', createdAt: '2024-01-03T00:00:00Z' }] });
        tick();

        expect(component.showAddClientRoleForm).toBeFalse();
        expect(component.addClientRoleData).toEqual({ targetClientId: '', role: '' });
      }));

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

      it('should handle 400 error when role not in target catalog', fakeAsync(() => {
        component.viewingClientRolesFor = 'test_client_1';
        component.showAddClientRoleForm = true;
        component.addClientRoleData = { targetClientId: 'target_1', role: 'unauthorized-role' };

        component.addClientRole('test_client_1');
        tick();

        const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        addReq.flush({ error: "Role 'unauthorized-role' is not in the target client's allowed roles catalog" }, { status: 400, statusText: 'Bad Request' });
        tick();
        tick(); // Extra tick for promise resolution

        expect(component.showAddClientRoleForm).toBeTrue();
      }));

      it('should handle 409 error for duplicate role', fakeAsync(() => {
        component.viewingClientRolesFor = 'test_client_1';
        component.addClientRoleData = { targetClientId: 'target_1', role: 'existing-role' };

        component.addClientRole('test_client_1');
        tick();

        const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        addReq.flush({ error: 'Role already assigned for this target client' }, { status: 409, statusText: 'Conflict' });
        tick();
      }));

      it('should handle 404 error when client not found', fakeAsync(() => {
        component.viewingClientRolesFor = 'test_client_1';
        component.addClientRoleData = { targetClientId: 'target_1', role: 'some-role' };

        component.addClientRole('test_client_1');
        tick();

        const addReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        addReq.flush({ error: 'Source or target client not found' }, { status: 404, statusText: 'Not Found' });
        tick();
      }));
    });

    describe('removeClientRole', () => {
      it('should remove a client role successfully when confirmed', fakeAsync(() => {
        confirmService.confirm.and.returnValue(Promise.resolve(true));
        component.viewingClientRolesFor = 'test_client_1';
        component.clientRoles = [{ targetClientId: 'target_1', role: 'reader', createdAt: '' }];

        component.removeClientRole('test_client_1', 'target_1', 'reader');
        tick();

        const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/reader');
        expect(removeReq.request.method).toBe('DELETE');
        removeReq.flush({});
        tick();

        const reloadReq = httpMock.expectOne('/api/clients/test_client_1/client-roles');
        reloadReq.flush({ srcClientId: 'test_client_1', roles: [] });
        tick();
      }));

      it('should not remove when user cancels confirmation', fakeAsync(() => {
        confirmService.confirm.and.returnValue(Promise.resolve(false));

        component.removeClientRole('test_client_1', 'target_1', 'reader');
        tick();
        tick(); // Extra tick for promise resolution

        httpMock.expectNone('/api/clients/test_client_1/client-roles/target_1/reader');
      }));

      it('should handle 403 error when removing client role', fakeAsync(() => {
        confirmService.confirm.and.returnValue(Promise.resolve(true));
        component.viewingClientRolesFor = 'test_client_1';

        component.removeClientRole('test_client_1', 'target_1', 'reader');
        tick();

        const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/reader');
        removeReq.flush({}, { status: 403, statusText: 'Forbidden' });
        tick();
      }));

      it('should handle 404 error when role assignment not found', fakeAsync(() => {
        confirmService.confirm.and.returnValue(Promise.resolve(true));
        component.viewingClientRolesFor = 'test_client_1';

        component.removeClientRole('test_client_1', 'target_1', 'non-existent');
        tick();

        const removeReq = httpMock.expectOne('/api/clients/test_client_1/client-roles/target_1/non-existent');
        removeReq.flush({ error: 'Role assignment not found' }, { status: 404, statusText: 'Not Found' });
        tick();
      }));
    });
  });
});
