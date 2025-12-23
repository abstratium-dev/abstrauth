import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AuthService, ANONYMOUS } from './auth.service';

describe('AuthService', () => {

  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthService);
    jasmine.clock().install();
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Initial State', () => {
    it('should start with anonymous token', () => {
      const token = service.getAccessToken();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should have token$ signal set to anonymous', () => {
      const token = service.token$();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should not be authenticated initially', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should have empty JWT initially', () => {
      expect(service.getJwt()).toBe('');
    });

    it('should have default route before sign in', () => {
      expect(service.getRouteBeforeSignIn()).toBe('/accounts');
    });
  });

  describe('Route Management', () => {
    it('should set route before sign in', () => {
      service.setRouteBeforeSignIn('/clients');
      expect(service.getRouteBeforeSignIn()).toBe('/clients');
    });

    it('should update route before sign in', () => {
      service.setRouteBeforeSignIn('/clients');
      service.setRouteBeforeSignIn('/user/123');
      expect(service.getRouteBeforeSignIn()).toBe('/user/123');
    });

    it('should handle empty route', () => {
      service.setRouteBeforeSignIn('');
      expect(service.getRouteBeforeSignIn()).toBe('');
    });

    it('should handle complex routes with query params', () => {
      service.setRouteBeforeSignIn('/clients?filter=active&sort=name');
      expect(service.getRouteBeforeSignIn()).toBe('/clients?filter=active&sort=name');
    });
  });

  describe('JWT Token Parsing and Setting', () => {
    const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbImFkbWluIiwidXNlcnMiXSwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFVzZXIiLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk1NDU2MDAsImNsaWVudF9pZCI6InRlc3QtY2xpZW50IiwianRpIjoiand0LWlkLTEyMyIsInVwbiI6InRlc3RAZXhhbXBsZS5jb20ifQ.signature';

    it('should parse and set JWT token correctly', () => {
      service.setAccessToken(validJwt);

      const token = service.getAccessToken();
      expect(token.sub).toBe('user-123');
      expect(token.email).toBe('test@example.com');
      expect(token.name).toBe('Test User');
      expect(token.isAuthenticated).toBe(true);
    });

    it('should update token$ signal when setting JWT', () => {
      service.setAccessToken(validJwt);

      const token = service.token$();
      expect(token.sub).toBe('user-123');
      expect(token.email).toBe('test@example.com');
      expect(token.isAuthenticated).toBe(true);
    });

    it('should store JWT string', () => {
      service.setAccessToken(validJwt);
      expect(service.getJwt()).toBe(validJwt);
    });

    it('should parse groups array correctly', () => {
      service.setAccessToken(validJwt);
      const groups = service.getGroups();
      expect(groups).toEqual(['admin', 'users']);
    });

    it('should parse scope string correctly', () => {
      service.setAccessToken(validJwt);
      const token = service.getAccessToken();
      expect(token.scope).toBe('openid profile email');
    });

    it('should parse timestamps correctly', () => {
      service.setAccessToken(validJwt);
      const token = service.getAccessToken();
      expect(token.iat).toBe(1609459200);
      expect(token.exp).toBe(1609545600);
    });
  });

  describe('Authentication Status', () => {
    const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbImFkbWluIiwidXNlcnMiXSwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFVzZXIiLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk1NDU2MDAsImNsaWVudF9pZCI6InRlc3QtY2xpZW50IiwianRpIjoiand0LWlkLTEyMyIsInVwbiI6InRlc3RAZXhhbXBsZS5jb20ifQ.signature';

    it('should return false for anonymous user', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should return true after setting valid JWT', () => {
      service.setAccessToken(validJwt);
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should return false after signout', fakeAsync(() => {
      service.setAccessToken(validJwt);
      service.signout();
      tick(); // Process setTimeout
      expect(service.isAuthenticated()).toBe(false);
    }));

    it('should return false after refresh (current implementation)', () => {
      service.setAccessToken(validJwt);
      service.refreshToken();
      expect(service.isAuthenticated()).toBe(false);
    });
  });

  describe('Token Getters', () => {
    const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbImFkbWluIiwidXNlcnMiXSwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFVzZXIiLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk1NDU2MDAsImNsaWVudF9pZCI6InRlc3QtY2xpZW50IiwianRpIjoiand0LWlkLTEyMyIsInVwbiI6InRlc3RAZXhhbXBsZS5jb20ifQ.signature';

    beforeEach(() => {
      service.setAccessToken(validJwt);
    });

    it('should get email', () => {
      expect(service.getEmail()).toBe('test@example.com');
    });

    it('should get name', () => {
      expect(service.getName()).toBe('Test User');
    });

    it('should get groups', () => {
      expect(service.getGroups()).toEqual(['admin', 'users']);
    });

    it('should get access token object', () => {
      const token = service.getAccessToken();
      expect(token.sub).toBe('user-123');
      expect(token.email).toBe('test@example.com');
    });
  });

  describe('Scope Management', () => {
    const jwtWithScopes = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbXSwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFVzZXIiLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIGFkbWluIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk1NDU2MDAsImNsaWVudF9pZCI6InRlc3QtY2xpZW50IiwianRpIjoiand0LWlkLTEyMyIsInVwbiI6InRlc3RAZXhhbXBsZS5jb20ifQ.signature';

    beforeEach(() => {
      service.setAccessToken(jwtWithScopes);
    });

    it('should return true for existing scope', () => {
      expect(service.hasScope('openid')).toBe(true);
      expect(service.hasScope('profile')).toBe(true);
      expect(service.hasScope('email')).toBe(true);
      expect(service.hasScope('admin')).toBe(true);
    });

    it('should return false for non-existing scope', () => {
      expect(service.hasScope('write')).toBe(false);
      expect(service.hasScope('delete')).toBe(false);
    });

    it('should handle empty scope check', () => {
      expect(service.hasScope('')).toBe(false);
    });

    it('should be case-sensitive', () => {
      expect(service.hasScope('OpenID')).toBe(false);
      expect(service.hasScope('ADMIN')).toBe(false);
    });
  });

  describe('Token Expiration', () => {
    it('should detect expired token', () => {
      // JWT with exp in the past (1609459200 = 2021-01-01)
      const expiredJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbXSwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFVzZXIiLCJzY29wZSI6Im9wZW5pZCIsImlhdCI6MTYwOTQ1OTIwMCwiZXhwIjoxNjA5NDU5MjAwLCJjbGllbnRfaWQiOiJ0ZXN0LWNsaWVudCIsImp0aSI6Imp3dC1pZC0xMjMiLCJ1cG4iOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature';
      
      service.setAccessToken(expiredJwt);
      expect(service.isExpired()).toBe(true);
    });

    it('should detect token about to expire', () => {
      // JWT with exp in the past
      const soonToExpireJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbXSwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFVzZXIiLCJzY29wZSI6Im9wZW5pZCIsImlhdCI6MTYwOTQ1OTIwMCwiZXhwIjoxNjA5NDU5MjAwLCJjbGllbnRfaWQiOiJ0ZXN0LWNsaWVudCIsImp0aSI6Imp3dC1pZC0xMjMiLCJ1cG4iOiJ0ZXN0QGV4YW1wbGUuY29tIn0.signature';
      
      service.setAccessToken(soonToExpireJwt);
      expect(service.isAboutToExpire()).toBe(true);
    });
  });

  describe('Sign Out', () => {
    const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbImFkbWluIl0sImVtYWlsIjoidGVzdEBleGFtcGxlLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiVGVzdCBVc2VyIiwic2NvcGUiOiJvcGVuaWQiLCJpYXQiOjE2MDk0NTkyMDAsImV4cCI6MTYwOTU0NTYwMCwiY2xpZW50X2lkIjoidGVzdC1jbGllbnQiLCJqdGkiOiJqd3QtaWQtMTIzIiwidXBuIjoidGVzdEBleGFtcGxlLmNvbSJ9.signature';

    it('should reset to anonymous token on signout', fakeAsync(() => {
      service.setAccessToken(validJwt);
      service.signout();
      tick(); // Process setTimeout

      const token = service.getAccessToken();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    }));

    it('should update token$ signal on signout', fakeAsync(() => {
      service.setAccessToken(validJwt);
      service.signout();
      tick(); // Process setTimeout

      const token = service.token$();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    }));

    it('should not be authenticated after signout', fakeAsync(() => {
      service.setAccessToken(validJwt);
      service.signout();
      tick(); // Process setTimeout

      expect(service.isAuthenticated()).toBe(false);
    }));
  });

  describe('Token Refresh', () => {
    const validJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbImFkbWluIl0sImVtYWlsIjoidGVzdEBleGFtcGxlLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiVGVzdCBVc2VyIiwic2NvcGUiOiJvcGVuaWQiLCJpYXQiOjE2MDk0NTkyMDAsImV4cCI6MTYwOTU0NTYwMCwiY2xpZW50X2lkIjoidGVzdC1jbGllbnQiLCJqdGkiOiJqd3QtaWQtMTIzIiwidXBuIjoidGVzdEBleGFtcGxlLmNvbSJ9.signature';

    it('should reset to anonymous on refresh (current implementation)', () => {
      service.setAccessToken(validJwt);
      service.refreshToken();

      const token = service.getAccessToken();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should update token$ signal on refresh', () => {
      service.setAccessToken(validJwt);
      service.refreshToken();

      const token = service.token$();
      expect(token.isAuthenticated).toBe(false);
    });
  });

  describe('Edge Cases', () => {
    it('should handle getting email before authentication', () => {
      expect(service.getEmail()).toBe(ANONYMOUS.email);
    });

    it('should handle getting name before authentication', () => {
      expect(service.getName()).toBe(ANONYMOUS.name);
    });

    it('should handle getting groups before authentication', () => {
      expect(service.getGroups()).toEqual([]);
    });

    it('should handle hasScope before authentication', () => {
      expect(service.hasScope('openid')).toBe(false);
    });

    it('should handle multiple setAccessToken calls', () => {
      const jwt1 = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMSIsImdyb3VwcyI6W10sImVtYWlsIjoidXNlcjFAZXhhbXBsZS5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IlVzZXIgMSIsInNjb3BlIjoib3BlbmlkIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk1NDU2MDAsImNsaWVudF9pZCI6InRlc3QtY2xpZW50IiwianRpIjoiand0LWlkLTEiLCJ1cG4iOiJ1c2VyMUBleGFtcGxlLmNvbSJ9.signature';
      const jwt2 = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMiIsImdyb3VwcyI6W10sImVtYWlsIjoidXNlcjJAZXhhbXBsZS5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IlVzZXIgMiIsInNjb3BlIjoib3BlbmlkIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk1NDU2MDAsImNsaWVudF9pZCI6InRlc3QtY2xpZW50IiwianRpIjoiand0LWlkLTIiLCJ1cG4iOiJ1c2VyMkBleGFtcGxlLmNvbSJ9.signature';

      service.setAccessToken(jwt1);
      expect(service.getEmail()).toBe('user1@example.com');

      service.setAccessToken(jwt2);
      expect(service.getEmail()).toBe('user2@example.com');
    });
  });

  describe('Anonymous Token Constants', () => {
    it('should have correct anonymous token properties', () => {
      expect(ANONYMOUS.email).toBe('anon@abstratium.dev');
      expect(ANONYMOUS.isAuthenticated).toBe(false);
      expect(ANONYMOUS.name).toBe('Anonymous');
      expect(ANONYMOUS.groups).toEqual([]);
    });

    it('should use anonymous token initially', () => {
      const token = service.getAccessToken();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.name).toBe(ANONYMOUS.name);
    });
  });
});
