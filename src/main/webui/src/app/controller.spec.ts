import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Controller } from './controller';
import { ModelService, OAuthClient } from './model.service';

describe('Controller', () => {
  let controller: Controller;
  let modelService: ModelService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    controller = TestBed.inject(Controller);
    modelService = TestBed.inject(ModelService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(controller).toBeTruthy();
  });

  describe('SignUp Username Delegation', () => {
    it('should delegate setSignUpUsername to modelService', () => {
      spyOn(modelService, 'setSignUpUsername');
      
      controller.setSignUpUsername('testuser');
      
      expect(modelService.setSignUpUsername).toHaveBeenCalledWith('testuser');
      expect(modelService.setSignUpUsername).toHaveBeenCalledTimes(1);
    });

    it('should update modelService state when setting username', () => {
      controller.setSignUpUsername('newuser');
      
      expect(modelService.signUpUsername$()).toBe('newuser');
    });

    it('should handle empty username', () => {
      controller.setSignUpUsername('');
      
      expect(modelService.signUpUsername$()).toBe('');
    });

    it('should handle special characters in username', () => {
      const specialUsername = 'user+test@example.com';
      controller.setSignUpUsername(specialUsername);
      
      expect(modelService.signUpUsername$()).toBe(specialUsername);
    });

    it('should handle multiple username updates', () => {
      controller.setSignUpUsername('user1');
      controller.setSignUpUsername('user2');
      controller.setSignUpUsername('user3');
      
      expect(modelService.signUpUsername$()).toBe('user3');
    });
  });

  describe('SignUp Password Delegation', () => {
    it('should delegate setSignUpPassword to modelService', () => {
      spyOn(modelService, 'setSignUpPassword');
      
      controller.setSignUpPassword('password123');
      
      expect(modelService.setSignUpPassword).toHaveBeenCalledWith('password123');
      expect(modelService.setSignUpPassword).toHaveBeenCalledTimes(1);
    });

    it('should update modelService state when setting password', () => {
      controller.setSignUpPassword('securepass');
      
      expect(modelService.signUpPassword$()).toBe('securepass');
    });

    it('should handle empty password', () => {
      controller.setSignUpPassword('');
      
      expect(modelService.signUpPassword$()).toBe('');
    });

    it('should handle special characters in password', () => {
      const specialPassword = 'P@ssw0rd!#$%^&*()';
      controller.setSignUpPassword(specialPassword);
      
      expect(modelService.signUpPassword$()).toBe(specialPassword);
    });

    it('should handle multiple password updates', () => {
      controller.setSignUpPassword('pass1');
      controller.setSignUpPassword('pass2');
      controller.setSignUpPassword('pass3');
      
      expect(modelService.signUpPassword$()).toBe('pass3');
    });
  });

  describe('SignIn RequestId Delegation', () => {
    it('should delegate setSignInRequestId to modelService', () => {
      spyOn(modelService, 'setSignInRequestId');
      
      controller.setSignInRequestId('req-123');
      
      expect(modelService.setSignInRequestId).toHaveBeenCalledWith('req-123');
      expect(modelService.setSignInRequestId).toHaveBeenCalledTimes(1);
    });

    it('should update modelService state when setting requestId', () => {
      controller.setSignInRequestId('auth-req-456');
      
      expect(modelService.signInRequestId$()).toBe('auth-req-456');
    });

    it('should handle empty requestId', () => {
      controller.setSignInRequestId('');
      
      expect(modelService.signInRequestId$()).toBe('');
    });

    it('should handle UUID format requestId', () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440000';
      controller.setSignInRequestId(uuid);
      
      expect(modelService.signInRequestId$()).toBe(uuid);
    });

    it('should handle multiple requestId updates', () => {
      controller.setSignInRequestId('req1');
      controller.setSignInRequestId('req2');
      controller.setSignInRequestId('req3');
      
      expect(modelService.signInRequestId$()).toBe('req3');
    });
  });

  describe('Combined Operations', () => {
    it('should handle setting all values', () => {
      controller.setSignUpUsername('testuser');
      controller.setSignUpPassword('testpass');
      controller.setSignInRequestId('testreq');
      
      expect(modelService.signUpUsername$()).toBe('testuser');
      expect(modelService.signUpPassword$()).toBe('testpass');
      expect(modelService.signInRequestId$()).toBe('testreq');
    });

    it('should maintain independent state for each value', () => {
      controller.setSignUpUsername('user1');
      expect(modelService.signUpUsername$()).toBe('user1');
      expect(modelService.signUpPassword$()).toBe('');
      expect(modelService.signInRequestId$()).toBe('');
      
      controller.setSignUpPassword('pass1');
      expect(modelService.signUpUsername$()).toBe('user1');
      expect(modelService.signUpPassword$()).toBe('pass1');
      expect(modelService.signInRequestId$()).toBe('');
      
      controller.setSignInRequestId('req1');
      expect(modelService.signUpUsername$()).toBe('user1');
      expect(modelService.signUpPassword$()).toBe('pass1');
      expect(modelService.signInRequestId$()).toBe('req1');
    });

    it('should handle clearing all values', () => {
      controller.setSignUpUsername('user');
      controller.setSignUpPassword('pass');
      controller.setSignInRequestId('req');
      
      controller.setSignUpUsername('');
      controller.setSignUpPassword('');
      controller.setSignInRequestId('');
      
      expect(modelService.signUpUsername$()).toBe('');
      expect(modelService.signUpPassword$()).toBe('');
      expect(modelService.signInRequestId$()).toBe('');
    });
  });

  describe('Method Call Verification', () => {
    it('should call modelService methods with correct parameters', () => {
      spyOn(modelService, 'setSignUpUsername');
      spyOn(modelService, 'setSignUpPassword');
      spyOn(modelService, 'setSignInRequestId');
      
      controller.setSignUpUsername('user');
      controller.setSignUpPassword('pass');
      controller.setSignInRequestId('req');
      
      expect(modelService.setSignUpUsername).toHaveBeenCalledWith('user');
      expect(modelService.setSignUpPassword).toHaveBeenCalledWith('pass');
      expect(modelService.setSignInRequestId).toHaveBeenCalledWith('req');
    });

    it('should not call other methods when setting username', () => {
      spyOn(modelService, 'setSignUpUsername');
      spyOn(modelService, 'setSignUpPassword');
      spyOn(modelService, 'setSignInRequestId');
      
      controller.setSignUpUsername('user');
      
      expect(modelService.setSignUpUsername).toHaveBeenCalled();
      expect(modelService.setSignUpPassword).not.toHaveBeenCalled();
      expect(modelService.setSignInRequestId).not.toHaveBeenCalled();
    });

    it('should not call other methods when setting password', () => {
      spyOn(modelService, 'setSignUpUsername');
      spyOn(modelService, 'setSignUpPassword');
      spyOn(modelService, 'setSignInRequestId');
      
      controller.setSignUpPassword('pass');
      
      expect(modelService.setSignUpUsername).not.toHaveBeenCalled();
      expect(modelService.setSignUpPassword).toHaveBeenCalled();
      expect(modelService.setSignInRequestId).not.toHaveBeenCalled();
    });

    it('should not call other methods when setting requestId', () => {
      spyOn(modelService, 'setSignUpUsername');
      spyOn(modelService, 'setSignUpPassword');
      spyOn(modelService, 'setSignInRequestId');
      
      controller.setSignInRequestId('req');
      
      expect(modelService.setSignUpUsername).not.toHaveBeenCalled();
      expect(modelService.setSignUpPassword).not.toHaveBeenCalled();
      expect(modelService.setSignInRequestId).toHaveBeenCalled();
    });
  });

  describe('Edge Cases', () => {
    it('should handle very long strings', () => {
      const longString = 'x'.repeat(10000);
      
      controller.setSignUpUsername(longString);
      controller.setSignUpPassword(longString);
      controller.setSignInRequestId(longString);
      
      expect(modelService.signUpUsername$()).toBe(longString);
      expect(modelService.signUpPassword$()).toBe(longString);
      expect(modelService.signInRequestId$()).toBe(longString);
    });

    it('should handle unicode characters', () => {
      controller.setSignUpUsername('用户名');
      controller.setSignUpPassword('密码123');
      controller.setSignInRequestId('请求ID');
      
      expect(modelService.signUpUsername$()).toBe('用户名');
      expect(modelService.signUpPassword$()).toBe('密码123');
      expect(modelService.signInRequestId$()).toBe('请求ID');
    });

    it('should handle strings with special whitespace', () => {
      controller.setSignUpUsername('user\nname');
      controller.setSignUpPassword('pass\tword');
      controller.setSignInRequestId('req\r\nid');
      
      expect(modelService.signUpUsername$()).toBe('user\nname');
      expect(modelService.signUpPassword$()).toBe('pass\tword');
      expect(modelService.signInRequestId$()).toBe('req\r\nid');
    });
  });

  describe('Service Integration', () => {
    it('should use the same ModelService instance', () => {
      const directModelService = TestBed.inject(ModelService);
      
      controller.setSignUpUsername('testuser');
      
      expect(directModelService.signUpUsername$()).toBe('testuser');
    });

    it('should reflect changes made directly to ModelService', () => {
      modelService.setSignUpUsername('directuser');
      
      // Controller should see the change through the shared service
      expect(modelService.signUpUsername$()).toBe('directuser');
    });
  });

  describe('loadAccounts', () => {
    it('should load accounts and update model service', () => {
      const mockAccounts = [
        { id: '1', email: 'test@example.com', name: 'Test User', emailVerified: true, authProvider: 'native', createdAt: '2024-01-01', roles: [] }
      ];

      controller.loadAccounts();

      const req = httpMock.expectOne('/api/accounts');
      expect(req.request.method).toBe('GET');
      req.flush(mockAccounts);

      expect(modelService.accounts$()).toEqual(mockAccounts);
    });

    it('should handle error when loading accounts', () => {
      controller.loadAccounts();

      const req = httpMock.expectOne('/api/accounts');
      req.error(new ProgressEvent('error'));

      expect(modelService.accounts$()).toEqual([]);
    });
  });

  describe('loadClients', () => {
    it('should load clients and update model service', () => {
      const mockClients = [
        { id: '1', clientId: 'test-client', clientName: 'Test Client', clientType: 'confidential', redirectUris: '[]', allowedScopes: '[]', requirePkce: true, createdAt: '2024-01-01' }
      ];

      controller.loadClients();

      expect(modelService.clientsLoading$()).toBe(true);
      expect(modelService.clientsError$()).toBeNull();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.method).toBe('GET');
      req.flush(mockClients);

      expect(modelService.clients$()).toEqual(mockClients);
      expect(modelService.clientsLoading$()).toBe(false);
      expect(modelService.clientsError$()).toBeNull();
    });

    it('should handle error when loading clients', () => {
      controller.loadClients();

      expect(modelService.clientsLoading$()).toBe(true);

      const req = httpMock.expectOne('/api/clients');
      req.error(new ProgressEvent('error'));

      expect(modelService.clients$()).toEqual([]);
      expect(modelService.clientsLoading$()).toBe(false);
      expect(modelService.clientsError$()).toBe('Failed to load clients');
    });
  });

  describe('loadConfig', () => {
    it('should load config and update model service', () => {
      controller.loadConfig();

      const req = httpMock.expectOne('/public/config');
      expect(req.request.method).toBe('GET');
      req.flush({ signupAllowed: true, allowNativeSignin: true, sessionTimeoutSeconds: 900 });

      expect(modelService.signupAllowed$()).toBe(true);
      expect(modelService.allowNativeSignin$()).toBe(true);
      expect(modelService.sessionTimeoutSeconds$()).toBe(900);
    });

    it('should handle false signup allowed', () => {
      controller.loadConfig();

      const req = httpMock.expectOne('/public/config');
      req.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 1800 });

      expect(modelService.signupAllowed$()).toBe(false);
      expect(modelService.allowNativeSignin$()).toBe(false);
      expect(modelService.sessionTimeoutSeconds$()).toBe(1800);
    });

    it('should handle error when loading config', () => {
      controller.loadConfig();

      const req = httpMock.expectOne('/public/config');
      req.error(new ProgressEvent('error'));

      expect(modelService.signupAllowed$()).toBe(false);
      expect(modelService.allowNativeSignin$()).toBe(false);
      expect(modelService.sessionTimeoutSeconds$()).toBe(900); // Default fallback
    });
  });

  describe('loadSignupAllowed (deprecated)', () => {
    it('should call loadConfig', () => {
      spyOn(controller, 'loadConfig');
      controller.loadSignupAllowed();
      expect(controller.loadConfig).toHaveBeenCalled();
    });
  });

  describe('createClient', () => {
    const mockClientData = {
      clientId: 'new-client',
      clientName: 'New Client',
      clientType: 'confidential',
      redirectUris: '["http://localhost:3000/callback"]',
      allowedScopes: '["openid", "profile"]',
      requirePkce: true
    };

    const mockCreatedClient = {
      id: '123',
      ...mockClientData,
      createdAt: '2024-01-01T00:00:00Z'
    };

    it('should create client and return the created client', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body).toEqual(mockClientData);
      createReq.flush(mockCreatedClient);

      await Promise.resolve();
      // Should trigger loadClients
      const loadReq = httpMock.expectOne('/api/clients');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([mockCreatedClient]);

      const result = await promise;
      expect(result).toEqual(mockCreatedClient);
    });

    it('should reload clients list after successful creation', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush(mockCreatedClient);

      await Promise.resolve();
      const loadReq = httpMock.expectOne('/api/clients');
      loadReq.flush([mockCreatedClient]);

      await promise;

      expect(modelService.clients$()).toEqual([mockCreatedClient]);
    });

    it('should handle error when creating client', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({ error: 'Client ID already exists' }, { status: 409, statusText: 'Conflict' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(409);
        expect(err.error.error).toBe('Client ID already exists');
      }
    });

    it('should handle validation error', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({ error: 'Client ID is required' }, { status: 400, statusText: 'Bad Request' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(400);
      }
    });

    it('should handle permission error', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({}, { status: 403, statusText: 'Forbidden' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(403);
      }
    });

    it('should handle network error', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.error(new ProgressEvent('error'));

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err) {
        expect(err).toBeTruthy();
      }
    });

    it('should send correct data format', async () => {
      const clientData = {
        clientId: 'test-client',
        clientName: 'Test Client',
        clientType: 'confidential',
        redirectUris: '["https://example.com/callback"]',
        allowedScopes: '["openid", "email", "profile"]',
        requirePkce: false
      };

      const promise = controller.createClient(clientData);

      const createReq = httpMock.expectOne('/api/clients');
      expect(createReq.request.body.clientId).toBe('test-client');
      expect(createReq.request.body.clientName).toBe('Test Client');
      expect(createReq.request.body.clientType).toBe('confidential');
      expect(createReq.request.body.redirectUris).toBe('["https://example.com/callback"]');
      expect(createReq.request.body.allowedScopes).toBe('["openid", "email", "profile"]');
      expect(createReq.request.body.requirePkce).toBe(false);
      
      createReq.flush({ id: '456', ...clientData, createdAt: '2024-01-01' });
      
      await Promise.resolve();
      const loadReq = httpMock.expectOne('/api/clients');
      loadReq.flush([]);

      await promise;
    });

    it('should handle server error', async () => {
      const promise = controller.createClient(mockClientData);

      const createReq = httpMock.expectOne('/api/clients');
      createReq.flush({}, { status: 500, statusText: 'Internal Server Error' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(500);
      }
    });
  });

  describe('updateClient', () => {
    const mockClientId = '123';
    const mockUpdateData = {
      clientName: 'Updated Client',
      clientType: 'confidential',
      redirectUris: '["http://localhost:4000/callback"]',
      allowedScopes: '["openid", "email"]',
      requirePkce: false
    };

    const mockUpdatedClient = {
      id: mockClientId,
      clientId: 'test-client',
      ...mockUpdateData,
      createdAt: '2024-01-01T00:00:00Z'
    };

    it('should update client and return the updated client', async () => {
      const promise = controller.updateClient(mockClientId, mockUpdateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      expect(updateReq.request.method).toBe('PUT');
      expect(updateReq.request.body).toEqual(mockUpdateData);
      updateReq.flush(mockUpdatedClient);

      await Promise.resolve();
      // Should trigger loadClients
      const loadReq = httpMock.expectOne('/api/clients');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([mockUpdatedClient]);

      const result = await promise;
      expect(result).toEqual(mockUpdatedClient);
    });

    it('should reload clients list after successful update', async () => {
      const promise = controller.updateClient(mockClientId, mockUpdateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      updateReq.flush(mockUpdatedClient);

      await Promise.resolve();
      const loadReq = httpMock.expectOne('/api/clients');
      loadReq.flush([mockUpdatedClient]);

      await promise;

      expect(modelService.clients$()).toEqual([mockUpdatedClient]);
    });

    it('should handle error when updating client', async () => {
      const promise = controller.updateClient(mockClientId, mockUpdateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      updateReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(404);
        expect(err.error.error).toBe('Client not found');
      }
    });

    it('should handle validation error', async () => {
      const promise = controller.updateClient(mockClientId, mockUpdateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      updateReq.flush({ error: 'Client name is required' }, { status: 400, statusText: 'Bad Request' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(400);
      }
    });

    it('should handle permission error', async () => {
      const promise = controller.updateClient(mockClientId, mockUpdateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      updateReq.flush({}, { status: 403, statusText: 'Forbidden' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(403);
      }
    });

    it('should handle network error', async () => {
      const promise = controller.updateClient(mockClientId, mockUpdateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      updateReq.error(new ProgressEvent('error'));

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err) {
        expect(err).toBeTruthy();
      }
    });

    it('should send correct data format', async () => {
      const updateData = {
        clientName: 'New Name',
        clientType: 'confidential',
        redirectUris: '["https://example.com/callback"]',
        allowedScopes: '["openid"]',
        requirePkce: true
      };

      const promise = controller.updateClient(mockClientId, updateData);

      const updateReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      expect(updateReq.request.body.clientName).toBe('New Name');
      expect(updateReq.request.body.clientType).toBe('confidential');
      expect(updateReq.request.body.redirectUris).toBe('["https://example.com/callback"]');
      expect(updateReq.request.body.allowedScopes).toBe('["openid"]');
      expect(updateReq.request.body.requirePkce).toBe(true);
      
      updateReq.flush({ id: mockClientId, ...updateData, createdAt: '2024-01-01' });
      
      await Promise.resolve();
      const loadReq = httpMock.expectOne('/api/clients');
      loadReq.flush([]);

      await promise;
    });
  });

  describe('deleteClient', () => {
    const mockClientId = '123';

    it('should delete client successfully', async () => {
      const promise = controller.deleteClient(mockClientId);

      const deleteReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null, { status: 204, statusText: 'No Content' });

      await Promise.resolve();
      // Should trigger loadClients
      const loadReq = httpMock.expectOne('/api/clients');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([]);

      await promise;
    });

    it('should reload clients list after successful deletion', async () => {
      const mockClients: OAuthClient[] = [
        {
          id: '456',
          clientId: 'remaining-client',
          clientName: 'Remaining Client',
          clientType: 'confidential',
          redirectUris: '["http://localhost:3000/callback"]',
          allowedScopes: '["openid"]',
          requirePkce: true,
          createdAt: '2024-01-01T00:00:00Z'
        }
      ];

      const promise = controller.deleteClient(mockClientId);

      const deleteReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      deleteReq.flush(null, { status: 204, statusText: 'No Content' });

      await Promise.resolve();
      const loadReq = httpMock.expectOne('/api/clients');
      loadReq.flush(mockClients);

      await promise;

      expect(modelService.clients$()).toEqual(mockClients);
    });

    it('should handle error when client not found', async () => {
      const promise = controller.deleteClient(mockClientId);

      const deleteReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      deleteReq.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(404);
        expect(err.error.error).toBe('Client not found');
      }
    });

    it('should handle permission error', async () => {
      const promise = controller.deleteClient(mockClientId);

      const deleteReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      deleteReq.flush({}, { status: 403, statusText: 'Forbidden' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(403);
      }
    });

    it('should handle network error', async () => {
      const promise = controller.deleteClient(mockClientId);

      const deleteReq = httpMock.expectOne(`/api/clients/${mockClientId}`);
      deleteReq.error(new ProgressEvent('error'));

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err) {
        expect(err).toBeTruthy();
      }
    });
  });

  describe('addAccountRole', () => {
    const mockAccountId = 'account-123';
    const mockClientId = 'client-456';
    const mockRole = 'admin';
    const mockResponse = { clientId: mockClientId, role: mockRole };

    it('should add account role successfully', async () => {
      const promise = controller.addAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        accountId: mockAccountId,
        clientId: mockClientId,
        role: mockRole
      });
      req.flush(mockResponse);

      await Promise.resolve();
      const accountsReq = httpMock.expectOne('/api/accounts');
      accountsReq.flush([]);

      const result = await promise;
      expect(result).toEqual(mockResponse);
    });

    it('should reload accounts after adding role', async () => {
      const promise = controller.addAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush(mockResponse);

      await Promise.resolve();
      const accountsReq = httpMock.expectOne('/api/accounts');
      accountsReq.flush([]);

      await promise;
    });

    it('should handle 400 validation error', async () => {
      const promise = controller.addAccountRole(mockAccountId, mockClientId, 'invalid@role');

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush({ error: 'Invalid role format' }, { status: 400, statusText: 'Bad Request' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(400);
      }
    });

    it('should handle 403 permission error', async () => {
      const promise = controller.addAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(403);
      }
    });

    it('should handle 404 account not found error', async () => {
      const promise = controller.addAccountRole('non-existent', mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush({ error: 'Account not found' }, { status: 404, statusText: 'Not Found' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(404);
      }
    });

    it('should handle network error', async () => {
      const promise = controller.addAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.error(new ProgressEvent('error'));

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err) {
        expect(err).toBeTruthy();
      }
    });
  });

  describe('removeAccountRole', () => {
    const mockAccountId = 'account-123';
    const mockClientId = 'client-456';
    const mockRole = 'admin';

    it('should remove account role successfully', async () => {
      const promise = controller.removeAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      expect(req.request.method).toBe('DELETE');
      expect(req.request.body).toEqual({
        accountId: mockAccountId,
        clientId: mockClientId,
        role: mockRole
      });
      req.flush(null, { status: 204, statusText: 'No Content' });

      await Promise.resolve();
      const accountsReq = httpMock.expectOne('/api/accounts');
      accountsReq.flush([]);

      await promise;
    });

    it('should reload accounts after removing role', async () => {
      const promise = controller.removeAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush(null, { status: 204, statusText: 'No Content' });

      await Promise.resolve();
      const accountsReq = httpMock.expectOne('/api/accounts');
      accountsReq.flush([]);

      await promise;
    });

    it('should handle 403 permission error', async () => {
      const promise = controller.removeAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(403);
      }
    });

    it('should handle 404 account not found error', async () => {
      const promise = controller.removeAccountRole('non-existent', mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.flush({ error: 'Account not found' }, { status: 404, statusText: 'Not Found' });

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err: any) {
        expect(err.status).toBe(404);
      }
    });

    it('should handle network error', async () => {
      const promise = controller.removeAccountRole(mockAccountId, mockClientId, mockRole);

      const req = httpMock.expectOne('/api/accounts/role');
      req.error(new ProgressEvent('error'));

      try {
        await promise;
        fail('Should have thrown an error');
      } catch (err) {
        expect(err).toBeTruthy();
      }
    });
  });

  describe('Service Account Roles', () => {
    const mockClientId = 'test-service-client';
    const mockRolesResponse = {
      clientId: mockClientId,
      roles: ['api-reader', 'api-writer']
    };
    const mockRoleResponse = {
      clientId: mockClientId,
      role: 'new-role',
      groupName: 'test-service-client_new-role'
    };

    describe('listServiceAccountRoles', () => {
      it('should fetch roles for a client', async () => {
        const promise = controller.listServiceAccountRoles(mockClientId);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        expect(req.request.method).toBe('GET');
        req.flush(mockRolesResponse);

        const result = await promise;
        expect(result).toEqual(mockRolesResponse);
        expect(result.roles).toEqual(['api-reader', 'api-writer']);
      });

      it('should handle 404 client not found error', async () => {
        const promise = controller.listServiceAccountRoles('non-existent');

        const req = httpMock.expectOne('/api/clients/non-existent/roles');
        req.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(404);
        }
      });

      it('should handle 403 permission error', async () => {
        const promise = controller.listServiceAccountRoles(mockClientId);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(403);
        }
      });

      it('should handle network error', async () => {
        const promise = controller.listServiceAccountRoles(mockClientId);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        req.error(new ProgressEvent('error'));

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err) {
          expect(err).toBeTruthy();
        }
      });
    });

    describe('addServiceAccountRole', () => {
      it('should add a role to a client', async () => {
        const addRequest = { role: 'new-role' };
        const promise = controller.addServiceAccountRole(mockClientId, addRequest);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(addRequest);
        req.flush(mockRoleResponse);

        const result = await promise;
        expect(result).toEqual(mockRoleResponse);
        expect(result.role).toBe('new-role');
        expect(result.groupName).toBe('test-service-client_new-role');
      });

      it('should handle 400 duplicate role error', async () => {
        const addRequest = { role: 'existing-role' };
        const promise = controller.addServiceAccountRole(mockClientId, addRequest);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        req.flush({ error: 'Role already exists' }, { status: 400, statusText: 'Bad Request' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(400);
        }
      });

      it('should handle 400 invalid role name error', async () => {
        const addRequest = { role: 'Invalid_Role' };
        const promise = controller.addServiceAccountRole(mockClientId, addRequest);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        req.flush({ error: 'Invalid role name' }, { status: 400, statusText: 'Bad Request' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(400);
        }
      });

      it('should handle 403 permission error', async () => {
        const addRequest = { role: 'new-role' };
        const promise = controller.addServiceAccountRole(mockClientId, addRequest);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles`);
        req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(403);
        }
      });

      it('should handle 404 client not found error', async () => {
        const addRequest = { role: 'new-role' };
        const promise = controller.addServiceAccountRole('non-existent', addRequest);

        const req = httpMock.expectOne('/api/clients/non-existent/roles');
        req.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(404);
        }
      });
    });

    describe('removeServiceAccountRole', () => {
      it('should remove a role from a client', async () => {
        const roleName = 'api-reader';
        const promise = controller.removeServiceAccountRole(mockClientId, roleName);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles/${roleName}`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null, { status: 204, statusText: 'No Content' });

        await promise;
        // Should complete without error
      });

      it('should handle 403 permission error', async () => {
        const roleName = 'api-reader';
        const promise = controller.removeServiceAccountRole(mockClientId, roleName);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles/${roleName}`);
        req.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(403);
        }
      });

      it('should handle 404 role not found error', async () => {
        const roleName = 'non-existent-role';
        const promise = controller.removeServiceAccountRole(mockClientId, roleName);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles/${roleName}`);
        req.flush({ error: 'Role not found' }, { status: 404, statusText: 'Not Found' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(404);
        }
      });

      it('should handle 404 client not found error', async () => {
        const roleName = 'api-reader';
        const promise = controller.removeServiceAccountRole('non-existent', roleName);

        const req = httpMock.expectOne('/api/clients/non-existent/roles/api-reader');
        req.flush({ error: 'Client not found' }, { status: 404, statusText: 'Not Found' });

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err: any) {
          expect(err.status).toBe(404);
        }
      });

      it('should handle network error', async () => {
        const roleName = 'api-reader';
        const promise = controller.removeServiceAccountRole(mockClientId, roleName);

        const req = httpMock.expectOne(`/api/clients/${mockClientId}/roles/${roleName}`);
        req.error(new ProgressEvent('error'));

        try {
          await promise;
          fail('Should have thrown an error');
        } catch (err) {
          expect(err).toBeTruthy();
        }
      });
    });
  });
});
