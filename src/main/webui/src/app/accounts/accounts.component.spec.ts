import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { of, BehaviorSubject } from 'rxjs';
import { AccountsComponent } from './accounts.component';
import { ROLE_ADMIN } from '../auth.service';

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
        { clientId: 'client-1', role: ROLE_ADMIN },
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
    
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    
    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
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

    it('should start with empty filter text', () => {
      expect(component.filterText).toBe('');
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
      
      expect(roleNames[0].textContent).toContain(ROLE_ADMIN);
      expect(roleNames[1].textContent).toContain('user');
    });

    it('should display client ID in role tile', () => {
      fixture.detectChanges();
      
      const req = httpMock.expectOne('/api/accounts');
      req.flush([mockAccounts[0]]);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const roleClients = compiled.querySelectorAll('.sub-tile-content');
      
      expect(roleClients[0].textContent).toContain('client-1');
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
      component.filterText = 'admin@example.com';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].email).toBe('admin@example.com');
    });

    it('should filter accounts by name', () => {
      component.filterText = 'Regular';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].name).toBe('Regular User');
    });

    it('should filter accounts by role', () => {
      component.filterText = ROLE_ADMIN;
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].name).toBe('Admin User');
    });

    it('should filter accounts by client ID', () => {
      component.filterText = 'client-2';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].name).toBe('Admin User');
    });

    it('should filter accounts by provider', () => {
      component.filterText = 'google';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].authProvider).toBe('google');
    });

    it('should be case-insensitive', () => {
      component.filterText = 'ADMIN';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].email).toBe('admin@example.com');
    });

    it('should show all accounts when filter is empty', () => {
      component.filterText = '';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(3);
    });

    it('should show no accounts when filter matches nothing', () => {
      component.filterText = 'nonexistent';
      component.applyFilter();
      
      expect(component.filteredAccounts.length).toBe(0);
    });

    it('should display "No accounts match" message when filter has no results', () => {
      component.filterText = 'nonexistent';
      component.applyFilter();
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const infoMessage = compiled.querySelector('.info-message');
      
      expect(infoMessage).toBeTruthy();
      expect(infoMessage.textContent).toContain('No accounts match your filter criteria');
    });

    it('should display filter count', () => {
      component.filterText = 'native';
      component.applyFilter();
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const filterInfo = compiled.querySelector('.filter-info');
      
      expect(filterInfo).toBeTruthy();
      expect(filterInfo.textContent).toContain('Showing 2 of 3 accounts');
    });

    it('should update URL when filter changes', () => {
      component.filterText = 'admin';
      component.onFilterChange();
      
      expect(router.navigate).toHaveBeenCalledWith(
        [],
        jasmine.objectContaining({
          queryParams: { filter: 'admin' }
        })
      );
    });

    it('should clear filter parameter from URL when filter is empty', () => {
      component.filterText = '';
      component.onFilterChange();
      
      expect(router.navigate).toHaveBeenCalledWith(
        [],
        jasmine.objectContaining({
          queryParams: { filter: null }
        })
      );
    });

    it('should display inline clear button when filter has text', () => {
      component.filterText = 'admin';
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const clearButton = compiled.querySelector('.filter-clear-button');
      
      expect(clearButton).toBeTruthy();
      expect(clearButton.textContent).toBe('×');
    });

    it('should not display clear button when filter is empty', () => {
      component.filterText = '';
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const clearButton = compiled.querySelector('.filter-clear-button');
      
      expect(clearButton).toBeFalsy();
    });

    it('should clear filter when clear button is clicked', () => {
      component.filterText = 'admin';
      component.clearFilter();
      
      expect(component.filterText).toBe('');
      expect(router.navigate).toHaveBeenCalled();
    });
  });

  describe('URL Filter Parameter - XSS Protection', () => {
    it('should read filter from URL query parameter', () => {
      queryParamsSubject.next({ filter: 'admin' });
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('admin');
    });

    it('should only accept string filter values', () => {
      queryParamsSubject.next({ filter: 'validstring' });
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('validstring');
    });

    it('should reject object filter values (XSS protection)', () => {
      queryParamsSubject.next({ filter: { malicious: 'object' } });
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('');
    });

    it('should reject array filter values (XSS protection)', () => {
      queryParamsSubject.next({ filter: ['malicious', 'array'] });
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('');
    });

    it('should handle missing filter parameter', () => {
      queryParamsSubject.next({});
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('');
    });

    it('should handle null filter parameter', () => {
      queryParamsSubject.next({ filter: null });
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('');
    });

    it('should handle undefined filter parameter', () => {
      queryParamsSubject.next({ filter: undefined });
      component.ngOnInit();
      
      // Flush the HTTP request triggered by loadAccounts()
      const req = httpMock.expectOne('/api/accounts');
      req.flush([]);
      
      expect(component.filterText).toBe('');
    });

    it('should apply filter after reading from URL', () => {
      fixture.detectChanges();
      const req = httpMock.expectOne('/api/accounts');
      req.flush(mockAccounts);
      
      queryParamsSubject.next({ filter: 'admin' });
      fixture.detectChanges();
      
      expect(component.filteredAccounts.length).toBe(1);
      expect(component.filteredAccounts[0].email).toBe('admin@example.com');
    });
  });

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
          roles: [{ clientId: 'client-1', role: ROLE_ADMIN }]
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
