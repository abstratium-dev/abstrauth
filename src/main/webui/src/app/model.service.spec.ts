import { TestBed } from '@angular/core/testing';
import { ModelService } from './model.service';

describe('ModelService', () => {
  let service: ModelService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ModelService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Initial State', () => {
    it('should have empty signUpUsername initially', () => {
      expect(service.signUpUsername$()).toBe('');
    });

    it('should have empty signUpPassword initially', () => {
      expect(service.signUpPassword$()).toBe('');
    });

    it('should have empty signInRequestId initially', () => {
      expect(service.signInRequestId$()).toBe('');
    });
  });

  describe('SignUp Username Management', () => {
    it('should set signUpUsername', () => {
      service.setSignUpUsername('testuser');
      expect(service.signUpUsername$()).toBe('testuser');
    });

    it('should update signUpUsername', () => {
      service.setSignUpUsername('user1');
      expect(service.signUpUsername$()).toBe('user1');
      
      service.setSignUpUsername('user2');
      expect(service.signUpUsername$()).toBe('user2');
    });

    it('should handle empty username', () => {
      service.setSignUpUsername('testuser');
      service.setSignUpUsername('');
      expect(service.signUpUsername$()).toBe('');
    });

    it('should handle username with special characters', () => {
      service.setSignUpUsername('test.user+123@example');
      expect(service.signUpUsername$()).toBe('test.user+123@example');
    });

    it('should handle very long username', () => {
      const longUsername = 'a'.repeat(1000);
      service.setSignUpUsername(longUsername);
      expect(service.signUpUsername$()).toBe(longUsername);
    });

    it('should handle username with spaces', () => {
      service.setSignUpUsername('test user name');
      expect(service.signUpUsername$()).toBe('test user name');
    });

    it('should handle unicode characters', () => {
      service.setSignUpUsername('用户名123');
      expect(service.signUpUsername$()).toBe('用户名123');
    });
  });

  describe('SignUp Password Management', () => {
    it('should set signUpPassword', () => {
      service.setSignUpPassword('password123');
      expect(service.signUpPassword$()).toBe('password123');
    });

    it('should update signUpPassword', () => {
      service.setSignUpPassword('pass1');
      expect(service.signUpPassword$()).toBe('pass1');
      
      service.setSignUpPassword('pass2');
      expect(service.signUpPassword$()).toBe('pass2');
    });

    it('should handle empty password', () => {
      service.setSignUpPassword('password');
      service.setSignUpPassword('');
      expect(service.signUpPassword$()).toBe('');
    });

    it('should handle password with special characters', () => {
      service.setSignUpPassword('P@ssw0rd!#$%');
      expect(service.signUpPassword$()).toBe('P@ssw0rd!#$%');
    });

    it('should handle very long password', () => {
      const longPassword = 'x'.repeat(500);
      service.setSignUpPassword(longPassword);
      expect(service.signUpPassword$()).toBe(longPassword);
    });

    it('should handle password with whitespace', () => {
      service.setSignUpPassword('pass word 123');
      expect(service.signUpPassword$()).toBe('pass word 123');
    });
  });

  describe('SignIn RequestId Management', () => {
    it('should set signInRequestId', () => {
      service.setSignInRequestId('req-123-456');
      expect(service.signInRequestId$()).toBe('req-123-456');
    });

    it('should update signInRequestId', () => {
      service.setSignInRequestId('req-1');
      expect(service.signInRequestId$()).toBe('req-1');
      
      service.setSignInRequestId('req-2');
      expect(service.signInRequestId$()).toBe('req-2');
    });

    it('should handle empty requestId', () => {
      service.setSignInRequestId('req-123');
      service.setSignInRequestId('');
      expect(service.signInRequestId$()).toBe('');
    });

    it('should handle UUID format requestId', () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440000';
      service.setSignInRequestId(uuid);
      expect(service.signInRequestId$()).toBe(uuid);
    });

    it('should handle numeric requestId', () => {
      service.setSignInRequestId('12345');
      expect(service.signInRequestId$()).toBe('12345');
    });
  });

  describe('Signal Reactivity', () => {
    it('should have readonly signals', () => {
      // Signals should be readonly and not allow direct mutation
      expect(service.signUpUsername$).toBeDefined();
      expect(service.signUpPassword$).toBeDefined();
      expect(service.signInRequestId$).toBeDefined();
    });

    it('should update signals independently', () => {
      service.setSignUpUsername('user1');
      service.setSignUpPassword('pass1');
      service.setSignInRequestId('req1');

      expect(service.signUpUsername$()).toBe('user1');
      expect(service.signUpPassword$()).toBe('pass1');
      expect(service.signInRequestId$()).toBe('req1');

      service.setSignUpUsername('user2');
      expect(service.signUpUsername$()).toBe('user2');
      expect(service.signUpPassword$()).toBe('pass1'); // unchanged
      expect(service.signInRequestId$()).toBe('req1'); // unchanged
    });
  });

  describe('State Transitions', () => {
    it('should handle complete signup flow', () => {
      // Initial state
      expect(service.signUpUsername$()).toBe('');
      expect(service.signUpPassword$()).toBe('');

      // User enters username
      service.setSignUpUsername('newuser');
      expect(service.signUpUsername$()).toBe('newuser');

      // User enters password
      service.setSignUpPassword('securepass123');
      expect(service.signUpPassword$()).toBe('securepass123');

      // After signup, clear values
      service.setSignUpUsername('');
      service.setSignUpPassword('');
      expect(service.signUpUsername$()).toBe('');
      expect(service.signUpPassword$()).toBe('');
    });

    it('should handle signin flow with request ID', () => {
      // Initial state
      expect(service.signInRequestId$()).toBe('');

      // Server provides request ID
      service.setSignInRequestId('auth-req-789');
      expect(service.signInRequestId$()).toBe('auth-req-789');

      // After signin, clear request ID
      service.setSignInRequestId('');
      expect(service.signInRequestId$()).toBe('');
    });

    it('should handle switching between signup and signin', () => {
      // Start signup
      service.setSignUpUsername('user1');
      service.setSignUpPassword('pass1');

      // Switch to signin
      service.setSignInRequestId('req-123');

      // All values should be independent
      expect(service.signUpUsername$()).toBe('user1');
      expect(service.signUpPassword$()).toBe('pass1');
      expect(service.signInRequestId$()).toBe('req-123');
    });
  });

  describe('Edge Cases', () => {
    it('should handle null-like strings', () => {
      service.setSignUpUsername('null');
      service.setSignUpPassword('undefined');
      service.setSignInRequestId('NaN');

      expect(service.signUpUsername$()).toBe('null');
      expect(service.signUpPassword$()).toBe('undefined');
      expect(service.signInRequestId$()).toBe('NaN');
    });

    it('should handle repeated same value sets', () => {
      service.setSignUpUsername('sameuser');
      service.setSignUpUsername('sameuser');
      service.setSignUpUsername('sameuser');

      expect(service.signUpUsername$()).toBe('sameuser');
    });

    it('should handle rapid value changes', () => {
      for (let i = 0; i < 100; i++) {
        service.setSignUpUsername(`user${i}`);
      }
      expect(service.signUpUsername$()).toBe('user99');
    });

    it('should handle values with newlines', () => {
      service.setSignUpUsername('user\nwith\nnewlines');
      expect(service.signUpUsername$()).toBe('user\nwith\nnewlines');
    });

    it('should handle values with tabs', () => {
      service.setSignUpPassword('pass\twith\ttabs');
      expect(service.signUpPassword$()).toBe('pass\twith\ttabs');
    });
  });

  describe('Multiple Service Instances', () => {
    it('should maintain state across same service instance', () => {
      service.setSignUpUsername('testuser');
      
      // Get the same service instance
      const sameService = TestBed.inject(ModelService);
      
      // Should have the same state
      expect(sameService.signUpUsername$()).toBe('testuser');
    });
  });
});
