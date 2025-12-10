import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { ClientsComponent } from './clients.component';

describe('ClientsComponent', () => {
  let component: ClientsComponent;
  let fixture: ComponentFixture<ClientsComponent>;
  let httpMock: HttpTestingController;
  let queryParamsSubject: BehaviorSubject<any>;

  const mockClients = [
    {
      id: '1',
      clientId: 'test-client-1',
      clientName: 'Test Client 1',
      clientType: 'public',
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
      requirePkce: false,
      createdAt: '2024-01-02T00:00:00Z'
    }
  ];

  beforeEach(async () => {
    queryParamsSubject = new BehaviorSubject({});
    
    const routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    routerSpy.createUrlTree.and.returnValue({});
    routerSpy.serializeUrl.and.returnValue('');
    routerSpy.events = EMPTY;
    
    await TestBed.configureTestingModule({
      imports: [ClientsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
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

    it('should display correct badge for public client', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/clients');
      req.flush([mockClients[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const badge = compiled.querySelector('.badge-primary');
      
      expect(badge).toBeTruthy();
      expect(badge.textContent).toContain('public');
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
});
