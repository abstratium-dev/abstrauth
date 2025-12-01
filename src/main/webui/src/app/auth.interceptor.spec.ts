import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let authService: AuthService;

  const mockJwt = 'eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItMTIzIiwiZ3JvdXBzIjpbImFkbWluIl0sImVtYWlsIjoidGVzdEBleGFtcGxlLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiVGVzdCBVc2VyIiwic2NvcGUiOiJvcGVuaWQiLCJpYXQiOjE2MDk0NTkyMDAsImV4cCI6MTYwOTU0NTYwMCwiY2xpZW50X2lkIjoidGVzdC1jbGllbnQiLCJqdGkiOiJqd3QtaWQtMTIzIiwidXBuIjoidGVzdEBleGFtcGxlLmNvbSJ9.signature';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        AuthService
      ]
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('API requests with JWT', () => {
    it('should add Authorization header to /api/ requests when JWT is present', () => {
      // Set a JWT token
      authService.setAccessToken(mockJwt);

      httpClient.get('/api/clients').subscribe();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.headers.has('Authorization')).toBe(true);
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush([]);
    });

    it('should add Authorization header to /api/users requests', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/api/users/123').subscribe();

      const req = httpMock.expectOne('/api/users/123');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush({});
    });

    it('should add Authorization header to POST requests to /api/', () => {
      authService.setAccessToken(mockJwt);

      httpClient.post('/api/clients', { name: 'test' }).subscribe();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush({});
    });

    it('should add Authorization header to PUT requests to /api/', () => {
      authService.setAccessToken(mockJwt);

      httpClient.put('/api/clients/123', { name: 'updated' }).subscribe();

      const req = httpMock.expectOne('/api/clients/123');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush({});
    });

    it('should add Authorization header to DELETE requests to /api/', () => {
      authService.setAccessToken(mockJwt);

      httpClient.delete('/api/clients/123').subscribe();

      const req = httpMock.expectOne('/api/clients/123');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush({});
    });
  });

  describe('API requests without JWT', () => {
    it('should not add Authorization header when JWT is not present', () => {
      // Don't set any JWT token (authService starts with no token)

      httpClient.get('/api/clients').subscribe();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush([]);
    });

    it('should not add Authorization header after signout', () => {
      // Set token then sign out
      authService.setAccessToken(mockJwt);
      authService.signout();

      httpClient.get('/api/clients').subscribe();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush([]);
    });
  });

  describe('Non-API requests', () => {
    it('should not add Authorization header to non-/api/ requests', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/oauth2/authorize').subscribe();

      const req = httpMock.expectOne('/oauth2/authorize');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should not add Authorization header to external URLs', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('https://external-api.example.com/data').subscribe();

      const req = httpMock.expectOne('https://external-api.example.com/data');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should not add Authorization header to /.well-known/ requests', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/.well-known/openid-configuration').subscribe();

      const req = httpMock.expectOne('/.well-known/openid-configuration');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('should not add Authorization header to root path', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/').subscribe();

      const req = httpMock.expectOne('/');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush('');
    });

    it('should not add Authorization header to /assets/ requests', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/assets/config.json').subscribe();

      const req = httpMock.expectOne('/assets/config.json');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('Edge cases', () => {
    it('should handle multiple sequential API requests', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/api/clients').subscribe();
      httpClient.get('/api/users').subscribe();

      const req1 = httpMock.expectOne('/api/clients');
      const req2 = httpMock.expectOne('/api/users');
      
      expect(req1.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      expect(req2.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      
      req1.flush([]);
      req2.flush([]);
    });

    it('should handle token change between requests', () => {
      const newJwt = 'eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Fic3RyYXV0aC5hYnN0cmF0aXVtLmRldiIsInN1YiI6InVzZXItNDU2Iiwic2NvcGUiOiJvcGVuaWQiLCJpYXQiOjE2MDk0NTkyMDAsImV4cCI6MTYwOTU0NTYwMH0.newsignature';
      
      authService.setAccessToken(mockJwt);
      httpClient.get('/api/clients').subscribe();
      const req1 = httpMock.expectOne('/api/clients');
      expect(req1.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req1.flush([]);

      // Change token
      authService.setAccessToken(newJwt);
      httpClient.get('/api/users').subscribe();
      const req2 = httpMock.expectOne('/api/users');
      expect(req2.request.headers.get('Authorization')).toBe(`Bearer ${newJwt}`);
      req2.flush([]);
    });

    it('should not modify existing Authorization header for non-API requests', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/other/endpoint', {
        headers: { Authorization: 'Basic abc123' }
      }).subscribe();

      const req = httpMock.expectOne('/other/endpoint');
      expect(req.request.headers.get('Authorization')).toBe('Basic abc123');
      req.flush({});
    });

    it('should handle /api/ prefix with query parameters', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/api/clients?page=1&size=10').subscribe();

      const req = httpMock.expectOne('/api/clients?page=1&size=10');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush([]);
    });

    it('should handle /api/ prefix with hash fragments', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/api/clients#section').subscribe();

      const req = httpMock.expectOne('/api/clients#section');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush([]);
    });
  });

  describe('Request preservation', () => {
    it('should preserve other headers when adding Authorization', () => {
      authService.setAccessToken(mockJwt);

      httpClient.get('/api/clients', {
        headers: {
          'Content-Type': 'application/json',
          'X-Custom-Header': 'custom-value'
        }
      }).subscribe();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      expect(req.request.headers.get('Content-Type')).toBe('application/json');
      expect(req.request.headers.get('X-Custom-Header')).toBe('custom-value');
      req.flush([]);
    });

    it('should preserve request body', () => {
      authService.setAccessToken(mockJwt);
      const body = { name: 'Test Client', type: 'public' };

      httpClient.post('/api/clients', body).subscribe();

      const req = httpMock.expectOne('/api/clients');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('should preserve request method', () => {
      authService.setAccessToken(mockJwt);

      httpClient.patch('/api/clients/123', { name: 'Updated' }).subscribe();

      const req = httpMock.expectOne('/api/clients/123');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${mockJwt}`);
      req.flush({});
    });
  });
});
