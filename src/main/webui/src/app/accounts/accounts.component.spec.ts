import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { of, BehaviorSubject, EMPTY } from 'rxjs';
import { AccountsComponent } from './accounts.component';
import { AuthService, ROLE_ADMIN } from '../auth.service';

describe('AccountsComponent', () => {
  let component: AccountsComponent;
  let fixture: ComponentFixture<AccountsComponent>;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;
  let queryParamsSubject: BehaviorSubject<any>;

  const mockAccounts = [
    {
      id: '1',
      email: 'admin@example.com',
      name: 'Admin User',
      emailVerified: true,
      authProvider: 'native',
      picture: 'https://example.com/admin.jpg',
      createdAt: '2024-01-01T00:00:00Z',
      roles: [
        { clientId: 'abstratium-abstrauth', role: 'admin' },
        { clientId: 'client-2', role: 'user' }
      ]
    },
    {
      id: '2',
      email: 'user@example.com',
      name: 'Regular User',
      emailVerified: false,
      authProvider: 'google',
      createdAt: '2024-01-02T00:00:00Z',
      roles: [
        { clientId: 'client-1', role: 'user' }
      ]
    },
    {
      id: '3',
      email: 'test@example.com',
      name: 'Test User',
      emailVerified: true,
      authProvider: 'native',
      createdAt: '2024-01-03T00:00:00Z',
      roles: []
    }
  ];

  beforeEach(async () => {
    queryParamsSubject = new BehaviorSubject({});
    
    const routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    routerSpy.createUrlTree.and.returnValue({});
    routerSpy.serializeUrl.and.returnValue('');
    routerSpy.events = EMPTY;
    
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['hasRole', 'token$']);
    authServiceSpy.hasRole.and.returnValue(true); // Mock user as admin
    authServiceSpy.token$.and.returnValue({ sub: '1' }); // Mock current user ID
    
    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParamsSubject.asObservable()
          }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AccountsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
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

    it('should start with empty accounts array', () => {
      expect(component.accounts).toEqual([]);
    });

    it('should start with empty filteredAccounts array', () => {
      expect(component.filteredAccounts).toEqual([]);
    });

    it('should start with no error', () => {
      expect(component.error).toBeNull();
    });

    it('should call loadAccounts on init', () => {
      spyOn(component, 'loadAccounts');
      component.ngOnInit();
      expect(component.loadAccounts).toHaveBeenCalled();
    });
  });

  describe('Loading Accounts - Success Cases', () => {
    it('should load accounts successfully', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/accounts');
      expect(req.request.method).toBe('GET');
      req.flush(mockAccounts);
      fixture.detectChanges();

      expect(component.accounts).toEqual(mockAccounts);
      expect(component.filteredAccounts).toEqual(mockAccounts);
      expect(component.loading).toBe(false);
      expect(component.error).toBeNull();
    });

    it('should display loading message while fetching', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      const loadingDiv = compiled.querySelector('.loading');
      
      expect(loadingDiv).toBeTruthy();
      expect(loadingDiv.textContent).toContain('Loading accounts');
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
    });

    it('should display accounts in tiles after successful load', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush(mockAccounts);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const tiles = compiled.querySelectorAll('.tile');
      
      expect(tiles.length).toBe(3);
      expect(compiled.textContent).toContain('Admin User');
      expect(compiled.textContent).toContain('Regular User');
      expect(compiled.textContent).toContain('Test User');
    });

    it('should display account details correctly', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      
      expect(compiled.textContent).toContain('admin@example.com');
      expect(compiled.textContent).toContain('Admin User');
      expect(compiled.textContent).toContain('native');
    });

    it('should display account picture when available', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const picture = compiled.querySelector('.tile-picture');
      
      expect(picture).toBeTruthy();
      expect(picture.getAttribute('src')).toBe('https://example.com/admin.jpg');
    });

    it('should display correct badge for native provider', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const badge = compiled.querySelector('.badge-native');
      
      expect(badge).toBeTruthy();
      expect(badge.textContent).toContain('native');
    });

    it('should display correct badge for google provider', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[1]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const badge = compiled.querySelector('.badge-google');
      
      expect(badge).toBeTruthy();
      expect(badge.textContent).toContain('google');
    });

    it('should display verified badge for verified email', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const badge = compiled.querySelector('.badge-verified');
      
      expect(badge).toBeTruthy();
      expect(badge.textContent).toContain('Verified');
    });

    it('should display unverified badge for unverified email', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[1]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const badge = compiled.querySelector('.badge-unverified');
      
      expect(badge).toBeTruthy();
      expect(badge.textContent).toContain('Not Verified');
    });

    it('should display error message when no accounts exist after timeout', fakeAsync(() => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      fixture.detectChanges();

      // Wait for the 5-second timeout that sets error when accounts is empty
      tick(5000);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      // After timeout with empty accounts, error message is shown
      const errorBox = compiled.querySelector('.error-box');
      
      expect(errorBox).toBeTruthy();
      expect(errorBox.textContent).toContain('Failed to load accounts');
      expect(component.loading).toBe(false);
    }));
  });

  describe('Role Display', () => {
    it('should display roles in sub-tiles', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const roleTiles = compiled.querySelectorAll('.sub-tile');
      
      expect(roleTiles.length).toBe(2);
    });

    it('should display role name in role tile', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const roleNames = compiled.querySelectorAll('.sub-tile-title');
      
      expect(roleNames[0].textContent).toContain('admin');
      expect(roleNames[1].textContent).toContain('user');
    });

    it('should display client ID in role tile', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const roleClients = compiled.querySelectorAll('.sub-tile-content');
      
      expect(roleClients[0].textContent).toContain('abstratium-abstrauth');
      expect(roleClients[1].textContent).toContain('client-2');
    });

    it('should display "No roles assigned" when account has no roles', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[2]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const noRoles = compiled.querySelector('.sub-tiles-empty');
      
      expect(noRoles).toBeTruthy();
      expect(noRoles.textContent).toContain('No roles assigned');
    });
  });

  describe('Filter Functionality', () => {
    beforeEach(() => {
      fixture.detectChanges();
      const req = httpMock.expectOne('/api/accounts');
      req.flush(mockAccounts);
      fixture.detectChanges();
    });

    it('should display filter input', () => {
      const compiled = fixture.nativeElement;
      const filterInput = compiled.querySelector('.filter-input');
      
      expect(filterInput).toBeTruthy();
    });

    it('should filter accounts by email', () => {
      component.onFilterChange('admin@example.com');
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].email).toBe('admin@example.com');
    });

    it('should filter accounts by name', () => {
      component.onFilterChange('Regular');
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].name).toBe('Regular User');
    });

    it('should filter accounts by role', () => {
      component.onFilterChange('admin');
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].name).toBe('Admin User');
    });

    it('should filter accounts by client ID', () => {
      component.onFilterChange('client-2');
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].name).toBe('Admin User');
    });

    it('should filter accounts by provider', () => {
      component.onFilterChange('google');
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].authProvider).toBe('google');
    });

    it('should be case-insensitive', () => {
      component.onFilterChange('ADMIN');
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].email).toBe('admin@example.com');
    });

    it('should show all accounts when filter is empty', () => {
      component.onFilterChange('');
      
      expect(component.filteredAccounts.length).toBe(3);
    });

    it('should show no accounts when filter matches nothing', () => {
      component.onFilterChange('nonexistent');
      
      expect(component.filteredAccounts.length).toBe(0);
    });

    it('should display "No accounts match" message when filter has no results', () => {
      component.onFilterChange('nonexistent');
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const infoMessage = compiled.querySelector('.info-message');
      
      expect(infoMessage).toBeTruthy();
      expect(infoMessage.textContent).toContain('No accounts match your filter criteria');
    });

    it('should display filter count', () => {
      component.onFilterChange('native');
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const filterInfo = compiled.querySelector('.filter-info');
      
      expect(filterInfo).toBeTruthy();
      expect(filterInfo.textContent).toContain('Showing 2 of 3 accounts');
    });

    // URL parameter handling and clear button tests are now handled by UrlFilterComponent
  });

  // URL Filter Parameter and XSS Protection tests are now handled by UrlFilterComponent

  describe('Admin Count', () => {
    it('should count admin accounts correctly', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush(mockAccounts);
      fixture.detectChanges();

      expect(component.getAdminCount()).toBe(1);
    });

    it('should display success notice for single admin', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const noticeBox = compiled.querySelector('.notice-success');
      
      expect(noticeBox).toBeTruthy();
      expect(noticeBox.textContent).toContain('You are the first and only admin');
    });

    it('should display warning for multiple admins', () => {
      const multipleAdmins = [
        mockAccounts[0],
        {
          ...mockAccounts[1],
          roles: [{ clientId: 'abstratium-abstrauth', role: 'admin' }]
        }
      ];
      
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush(multipleAdmins);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const noticeBox = compiled.querySelector('.notice-box');
      
      expect(noticeBox).toBeTruthy();
      expect(noticeBox.textContent).toContain('Multiple admin accounts detected');
      expect(noticeBox.textContent).toContain('2 admins');
    });
  });

  describe('Loading Accounts - Error Cases', () => {
    it('should handle HTTP error gracefully', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/accounts');
      req.flush('Error loading accounts', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      // Wait for the 5-second timeout to trigger error handling
      tick(5000);
      fixture.detectChanges();

      expect(component.error).toBe('Failed to load accounts. Please try again.');
      expect(component.loading).toBe(false);
      expect(component.accounts).toEqual([]);
    }));

    it('should display error message on failure', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/accounts');
      req.flush('Error', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      // Wait for the 5-second timeout to trigger error handling
      tick(5000);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const errorBox = compiled.querySelector('.error-box');
      
      expect(errorBox).toBeTruthy();
      expect(errorBox.textContent).toContain('Failed to load accounts');
    }));

    it('should display retry button on error', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne('/api/accounts');
      req.flush('Error', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      // Wait for the 5-second timeout to trigger error handling
      tick(5000);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const retryButton = compiled.querySelector('.error-box button');
      
      expect(retryButton).toBeTruthy();
      expect(retryButton.textContent).toContain('Retry');
    }));
  });

  describe('Provider Badge Class', () => {
    it('should return correct class for google provider', () => {
      expect(component.getProviderBadgeClass('google')).toBe('badge-google');
    });

    it('should return correct class for native provider', () => {
      expect(component.getProviderBadgeClass('native')).toBe('badge-native');
    });

    it('should return empty string for unknown provider', () => {
      expect(component.getProviderBadgeClass('unknown')).toBe('');
    });
  });
});
