import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Controller } from './controller';
import { ModelService } from './model.service';

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
        { id: '1', email: 'test@example.com', name: 'Test User', emailVerified: true, authProvider: 'native', createdAt: '2024-01-01' }
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

  describe('loadSignupAllowed', () => {
    it('should load signup allowed flag and update model service', () => {
      controller.loadSignupAllowed();

      const req = httpMock.expectOne('/api/signup/allowed');
      expect(req.request.method).toBe('GET');
      req.flush({ allowed: true });

      expect(modelService.signupAllowed$()).toBe(true);
    });

    it('should handle false signup allowed', () => {
      controller.loadSignupAllowed();

      const req = httpMock.expectOne('/api/signup/allowed');
      req.flush({ allowed: false });

      expect(modelService.signupAllowed$()).toBe(false);
    });

    it('should handle error when loading signup allowed', () => {
      controller.loadSignupAllowed();

      const req = httpMock.expectOne('/api/signup/allowed');
      req.error(new ProgressEvent('error'));

      expect(modelService.signupAllowed$()).toBe(false);
    });
  });
});
