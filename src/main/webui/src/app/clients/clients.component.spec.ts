import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { ClientsComponent } from './clients.component';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';

describe('ClientsComponent', () => {
  let component: ClientsComponent;
  let fixture: ComponentFixture<ClientsComponent>;
  let httpMock: HttpTestingController;
  let queryParamsSubject: BehaviorSubject<any>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;

  const mockClients = [
    {
      id: '1',
      clientId: 'test-client-1',
      clientName: 'Test Client 1',
      clientType: 'confidential',
      redirectUris: '["http://localhost:3000/callback"]',
      allowedScopes: '["openid", "profile", "email"]',
      requirePkce: true,
      createdAt: '2024-01-01T00:00:00Z'
    },
    {
      id: '2',
      clientId: 'test-client-2',
      clientName: 'Test Client 2',
      clientType: 'confidential',
      redirectUris: '["http://localhost:4000/callback", "http://localhost:4000/auth"]',
      allowedScopes: '["openid", "admin"]',
      requirePkce: true,
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
        provideHttpClient(),
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
      
      expect(compiled.textContent).toContain('test-client-1');
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
      const card = compiled.querySelector('[data-client-id="test-client-1"]');
      
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
        clientId: 'new-client',
        clientName: 'New Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: 'openid profile',
        requirePkce: true
      };

      const submitPromise = component.onSubmit();

      const createReq = httpMock.expectOne('/api/clients');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body.clientId).toBe('new-client');
      expect(createReq.request.body.redirectUris).toBe('["http://localhost:3000/callback"]');
      expect(createReq.request.body.allowedScopes).toBe('["openid","profile"]');
      
      createReq.flush({
        id: '3',
        clientId: 'new-client',
        clientName: 'New Client',
        clientType: 'confidential',
        redirectUris: '["http://localhost:3000/callback"]',
        allowedScopes: '["openid", "profile"]',
        requirePkce: true,
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
        clientId: 'multi-uri-client',
        clientName: 'Multi URI Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback\nhttp://localhost:4000/callback',
        allowedScopes: 'openid',
        requirePkce: true
      };

      const submitPromise = component.onSubmit();

      const createReq = httpMock.expectOne('/api/clients');
      expect(createReq.request.body.redirectUris).toBe('["http://localhost:3000/callback","http://localhost:4000/callback"]');
      
      createReq.flush({ id: '3', ...component.formData });
      
      await Promise.resolve();
      const reloadReq = httpMock.expectOne('/api/clients');
      reloadReq.flush([]);

      await submitPromise;
    });

    it('should handle comma-separated scopes', async () => {
      component.formData = {
        clientId: 'comma-scopes-client',
        clientName: 'Comma Scopes Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: 'openid, profile, email',
        requirePkce: true
      };

      const submitPromise = component.onSubmit();

      const createReq = httpMock.expectOne('/api/clients');
      expect(createReq.request.body.allowedScopes).toBe('["openid","profile","email"]');
      
      createReq.flush({ id: '3', ...component.formData });
      
      await Promise.resolve();
      const reloadReq = httpMock.expectOne('/api/clients');
      reloadReq.flush([]);

      await submitPromise;
    });

    it('should validate redirect URIs are not empty', async () => {
      component.formData = {
        clientId: 'test-client',
        clientName: 'Test Client',
        clientType: 'confidential',
        redirectUris: '',
        allowedScopes: 'openid',
        requirePkce: true
      };

      await component.onSubmit();

      expect(component.formError).toBe('At least one redirect URI is required');
      expect(component.formSubmitting).toBe(false);
    });

    it('should validate scopes are not empty', async () => {
      component.formData = {
        clientId: 'test-client',
        clientName: 'Test Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: '',
        requirePkce: true
      };

      await component.onSubmit();

      expect(component.formError).toBe('At least one scope is required');
      expect(component.formSubmitting).toBe(false);
    });

    it('should handle duplicate client ID error', async () => {
      component.showForm = true;
      component.formData = {
        clientId: 'existing-client',
        clientName: 'Existing Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: 'openid',
        requirePkce: true
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
        clientId: 'test-client',
        clientName: 'Test Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: 'openid',
        requirePkce: true
      };

      const submitPromise = component.onSubmit();

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({}, { status: 403, statusText: 'Forbidden' });

      await submitPromise;

      expect(component.formError).toBe('You do not have permission to create clients');
    });

    it('should handle generic error', async () => {
      component.formData = {
        clientId: 'test-client',
        clientName: 'Test Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: 'openid',
        requirePkce: true
      };

      const submitPromise = component.onSubmit();

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({}, { status: 500, statusText: 'Server Error' });

      await submitPromise;

      expect(component.formError).toBe('Failed to create client. Please try again.');
    });

    it('should set formSubmitting during submission', async () => {
      component.formData = {
        clientId: 'test-client',
        clientName: 'Test Client',
        clientType: 'confidential',
        redirectUris: 'http://localhost:3000/callback',
        allowedScopes: 'openid',
        requirePkce: true
      };

      expect(component.formSubmitting).toBe(false);
      
      const submitPromise = component.onSubmit();
      
      // Should be true during submission
      expect(component.formSubmitting).toBe(true);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({ id: '3', ...component.formData });
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
      expect(component.formData.clientId).toBe('test-client-1');
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

      const deleteReq = httpMock.expectOne('/api/clients/1');
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

      httpMock.expectNone('/api/clients/1');
      expect(confirmService.confirm).toHaveBeenCalled();
    });

    it('should cancel edit mode if deleting the client being edited', async () => {
      component.startEdit(mockClients[0]);
      expect(component.editingClientId).toBe('1');

      const deletePromise = component.deleteClient(mockClients[0]);

      // Wait for confirmation Promise to resolve
      await Promise.resolve();

      const deleteReq = httpMock.expectOne('/api/clients/1');
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

      const deleteReq = httpMock.expectOne('/api/clients/1');
      deleteReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

      await deletePromise;
    });

    it('should handle 403 permission error when deleting', async () => {
      const deletePromise = component.deleteClient(mockClients[0]);

      // Wait for confirmation Promise to resolve
      await Promise.resolve();

      const deleteReq = httpMock.expectOne('/api/clients/1');
      deleteReq.flush({}, { status: 403, statusText: 'Forbidden' });

      await deletePromise;
    });

    it('should handle generic error when deleting', async () => {
      const deletePromise = component.deleteClient(mockClients[0]);

      // Wait for confirmation Promise to resolve
      await Promise.resolve();

      const deleteReq = httpMock.expectOne('/api/clients/1');
      deleteReq.flush({}, { status: 500, statusText: 'Internal Server Error' });

      await deletePromise;
    });
  });

  describe('Role Management', () => {
    const mockClient = mockClients[0];
    const mockRolesResponse = {
      clientId: 'test-client-1',
      roles: ['api-reader', 'api-writer']
    };

    beforeEach(() => {
      // Load clients first
      const req = httpMock.expectOne('/api/clients');
      req.flush(mockClients);
    });

    it('should toggle roles view and load roles', async () => {
      expect(component.viewingRolesFor).toBeNull();
      expect(component.serviceAccountRoles).toEqual([]);

      const togglePromise = component.toggleRolesView(mockClient);

      const rolesReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      rolesReq.flush(mockRolesResponse);

      await togglePromise;

      expect(component.viewingRolesFor).toBe('test-client-1');
      expect(component.serviceAccountRoles).toEqual(['api-reader', 'api-writer']);
      expect(component.rolesLoading).toBeFalse();
    });

    it('should hide roles view when toggling again', async () => {
      // First toggle to show
      component.viewingRolesFor = 'test-client-1';
      component.serviceAccountRoles = ['api-reader'];

      await component.toggleRolesView(mockClient);

      expect(component.viewingRolesFor).toBeNull();
      expect(component.serviceAccountRoles).toEqual([]);
      expect(component.showAddRoleForm).toBeFalse();
    });

    it('should handle error when loading roles', async () => {
      const togglePromise = component.toggleRolesView(mockClient);

      const rolesReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      rolesReq.flush({ error: 'Failed to load' }, { status: 500, statusText: 'Internal Server Error' });

      await togglePromise;

      expect(component.rolesError).toBe('Failed to load roles');
      expect(component.rolesLoading).toBeFalse();
    });

    it('should toggle add role form', () => {
      expect(component.showAddRoleForm).toBeFalse();

      component.toggleAddRoleForm();
      expect(component.showAddRoleForm).toBeTrue();
      expect(component.addRoleData.role).toBe('');

      component.toggleAddRoleForm();
      expect(component.showAddRoleForm).toBeFalse();
    });

    it('should add a role successfully', async () => {
      component.viewingRolesFor = 'test-client-1';
      component.addRoleData.role = 'new-role';

      const addPromise = component.addRole('test-client-1');

      const addReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      expect(addReq.request.method).toBe('POST');
      expect(addReq.request.body).toEqual({ role: 'new-role' });
      addReq.flush({
        clientId: 'test-client-1',
        role: 'new-role',
        groupName: 'test-client-1_new-role'
      });

      // Expect reload of roles
      const reloadReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      reloadReq.flush({ clientId: 'test-client-1', roles: ['new-role'] });

      await addPromise;

      expect(component.showAddRoleForm).toBeFalse();
      expect(component.addRoleData.role).toBe('');
    });

    it('should reject empty role name', async () => {
      component.addRoleData.role = '';
      await component.addRole('test-client-1');
      // Should not make HTTP request
      httpMock.expectNone('/api/clients/test-client-1/roles');
    });

    it('should reject role name with uppercase', async () => {
      component.addRoleData.role = 'API-Reader';
      await component.addRole('test-client-1');
      // Should not make HTTP request
      httpMock.expectNone('/api/clients/test-client-1/roles');
    });

    it('should reject role name with underscores', async () => {
      component.addRoleData.role = 'api_reader';
      await component.addRole('test-client-1');
      // Should not make HTTP request
      httpMock.expectNone('/api/clients/test-client-1/roles');
    });

    it('should reject role name with spaces', async () => {
      component.addRoleData.role = 'api reader';
      await component.addRole('test-client-1');
      // Should not make HTTP request
      httpMock.expectNone('/api/clients/test-client-1/roles');
    });

    it('should handle 400 error when adding duplicate role', async () => {
      component.viewingRolesFor = 'test-client-1';
      component.addRoleData.role = 'existing-role';

      const addPromise = component.addRole('test-client-1');

      const addReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      addReq.flush({ error: 'Role already exists' }, { status: 400, statusText: 'Bad Request' });

      await addPromise;
    });

    it('should handle 403 permission error when adding role', async () => {
      component.viewingRolesFor = 'test-client-1';
      component.addRoleData.role = 'new-role';

      const addPromise = component.addRole('test-client-1');

      const addReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      addReq.flush({}, { status: 403, statusText: 'Forbidden' });

      await addPromise;
    });

    it('should handle 404 error when adding role to non-existent client', async () => {
      component.viewingRolesFor = 'test-client-1';
      component.addRoleData.role = 'new-role';

      const addPromise = component.addRole('test-client-1');

      const addReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      addReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

      await addPromise;
    });

    it('should remove a role successfully', async () => {
      component.viewingRolesFor = 'test-client-1';

      const removePromise = component.removeRole('test-client-1', 'api-reader');

      // Wait for confirmation
      await Promise.resolve();

      const removeReq = httpMock.expectOne('/api/clients/test-client-1/roles/api-reader');
      expect(removeReq.request.method).toBe('DELETE');
      removeReq.flush(null, { status: 204, statusText: 'No Content' });

      // Expect reload of roles
      const reloadReq = httpMock.expectOne('/api/clients/test-client-1/roles');
      reloadReq.flush({ clientId: 'test-client-1', roles: ['api-writer'] });

      await removePromise;
    });

    it('should not remove role if user cancels confirmation', async () => {
      confirmService.confirm.and.returnValue(Promise.resolve(false));
      component.viewingRolesFor = 'test-client-1';

      await component.removeRole('test-client-1', 'api-reader');

      // Should not make HTTP request
      httpMock.expectNone('/api/clients/test-client-1/roles/api-reader');
    });

    it('should handle 403 permission error when removing role', async () => {
      component.viewingRolesFor = 'test-client-1';

      const removePromise = component.removeRole('test-client-1', 'api-reader');

      await Promise.resolve();

      const removeReq = httpMock.expectOne('/api/clients/test-client-1/roles/api-reader');
      removeReq.flush({}, { status: 403, statusText: 'Forbidden' });

      await removePromise;
    });

    it('should handle 404 error when removing non-existent role', async () => {
      component.viewingRolesFor = 'test-client-1';

      const removePromise = component.removeRole('test-client-1', 'non-existent');

      await Promise.resolve();

      const removeReq = httpMock.expectOne('/api/clients/test-client-1/roles/non-existent');
      removeReq.flush({ error: 'Role not found' }, { status: 404, statusText: 'Not Found' });

      await removePromise;
    });

    it('should handle generic error when removing role', async () => {
      component.viewingRolesFor = 'test-client-1';

      const removePromise = component.removeRole('test-client-1', 'api-reader');

      await Promise.resolve();

      const removeReq = httpMock.expectOne('/api/clients/test-client-1/roles/api-reader');
      removeReq.flush({}, { status: 500, statusText: 'Internal Server Error' });

      await removePromise;
    });
  });
});
