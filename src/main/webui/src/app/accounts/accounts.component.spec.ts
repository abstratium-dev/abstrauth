import type { Mock, MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { vi } from 'vitest';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { AuthService, ROLE_MANAGE_ACCOUNTS } from '../auth.service';
import { Controller } from '../controller';
import { AccountsComponent } from './accounts.component';
import { ModelService } from '../model.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../shared/toast/toast.service';

describe('AccountsComponent', () => {
    let component: AccountsComponent;
    let fixture: ComponentFixture<AccountsComponent>;
    let httpMock: HttpTestingController;
    let router: MockedObject<Router>;
    let queryParamsSubject: BehaviorSubject<any>;
    let confirmService: MockedObject<ConfirmDialogService>;
    let toastService: MockedObject<ToastService>;
    let authService!: MockedObject<AuthService>;

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
        vi.useFakeTimers();
        queryParamsSubject = new BehaviorSubject({});

        const routerSpy = createMock<Router>({
            navigate: vi.fn().mockName("Router.navigate"),
            createUrlTree: vi.fn().mockName("Router.createUrlTree"),
            serializeUrl: vi.fn().mockName("Router.serializeUrl"),
            events: EMPTY
        });
        routerSpy.createUrlTree.mockReturnValue({} as any);
        routerSpy.serializeUrl.mockReturnValue('');

        const authServiceSpy = createMock<AuthService>({
            hasRole: vi.fn().mockName("AuthService.hasRole"),
            token$: vi.fn().mockName("AuthService.token$"),
            getOrgId: vi.fn().mockName("AuthService.getOrgId"),
            getEmail: vi.fn().mockName("AuthService.getEmail"),
            signout: vi.fn().mockName("AuthService.signout")
        });
        authServiceSpy.hasRole.mockReturnValue(true); // Mock user as admin
        authServiceSpy.token$.mockReturnValue({ sub: '1' } as any); // Mock current user ID
        authServiceSpy.getOrgId.mockReturnValue('test-org');
        authServiceSpy.getEmail.mockReturnValue('admin@example.com');
        authServiceSpy.signout.mockImplementation(() => {
        });

        const confirmServiceSpy = createMock<ConfirmDialogService>({
            confirm: vi.fn().mockName("ConfirmDialogService.confirm")
        });
        confirmServiceSpy.confirm.mockResolvedValue(true); // Default to confirming

        const toastServiceSpy = createMock<ToastService>({
            success: vi.fn().mockName("ToastService.success"),
            error: vi.fn().mockName("ToastService.error"),
            info: vi.fn().mockName("ToastService.info")
        });

        await TestBed.configureTestingModule({
            imports: [AccountsComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                { provide: Router, useValue: routerSpy },
                { provide: AuthService, useValue: authServiceSpy },
                { provide: ConfirmDialogService, useValue: confirmServiceSpy },
                { provide: ToastService, useValue: toastServiceSpy },
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
        TestBed.inject(ModelService).reset();
        httpMock = TestBed.inject(HttpTestingController);
        router = TestBed.inject(Router) as MockedObject<Router>;
        confirmService = TestBed.inject(ConfirmDialogService) as MockedObject<ConfirmDialogService>;
        toastService = TestBed.inject(ToastService) as MockedObject<ToastService>;
        authService = TestBed.inject(AuthService) as MockedObject<AuthService>;
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
    });

    // Helper functions to flush the requests that happen on init
    function flushOwnersRequest(ownerIds: string[] = []) {
        const ownersReq = httpMock.expectOne('/api/organisations/test-org/owners');
        ownersReq.flush(ownerIds);
    }

    function flushClientsRequest() {
        flushOwnersRequest();
        const clientsReq = httpMock.expectOne('/api/clients');
        clientsReq.flush([]);
    }

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
            vi.spyOn(component, 'loadAccounts').mockReturnValue(undefined);
            vi.spyOn(component, 'loadOwners').mockResolvedValue(undefined);
            const controller = TestBed.inject(Controller);
            vi.spyOn(controller, 'loadClients').mockReturnValue(undefined);
            component.ngOnInit();
            expect(component.loadAccounts).toHaveBeenCalled();
            expect(controller.loadClients).toHaveBeenCalled();
        });
    });

    describe('Loading Accounts - Success Cases', () => {
        it('should load accounts successfully', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            expect(req.request.method).toBe('GET');
            req.flush(mockAccounts);
            flushClientsRequest();
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
            flushClientsRequest();
        });

        it('should display accounts in tiles after successful load', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush(mockAccounts);
            flushClientsRequest();
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
            flushClientsRequest();
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
            flushClientsRequest();
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
            flushClientsRequest();
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
            flushClientsRequest();
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
            flushClientsRequest();
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
            flushClientsRequest();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const badge = compiled.querySelector('.badge-unverified');

            expect(badge).toBeTruthy();
            expect(badge.textContent).toContain('Not Verified');
        });

        it('should display error message when no accounts exist after timeout', async () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush([]);
            flushClientsRequest();
            fixture.detectChanges();

            // Wait for the 5-second timeout that sets error when accounts is empty
            vi.advanceTimersByTime(5000);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            // After timeout with empty accounts, error message is shown
            const errorBox = compiled.querySelector('.error-box');

            expect(errorBox).toBeTruthy();
            expect(errorBox.textContent).toContain('Failed to load accounts');
            expect(component.loading).toBe(false);
        });
    });

    describe('Role Display', () => {
        it('should display roles in sub-tiles', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush([mockAccounts[0]]);
            flushClientsRequest();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const roleTiles = compiled.querySelectorAll('.sub-tile');

            expect(roleTiles.length).toBe(2);
        });

        it('should display role name in role tile', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush([mockAccounts[0]]);
            flushClientsRequest();
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
            flushClientsRequest();
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
            flushClientsRequest();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const noRoles = compiled.querySelector('.sub-tiles-empty');

            expect(noRoles).toBeTruthy();
            expect(noRoles.textContent).toContain('No roles assigned');
        });
    });

    describe('Filter Functionality', () => {
        beforeEach(async () => {
            vi.useRealTimers();
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/accounts');
            req.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();
            flushClientsRequest();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();
        });

        it('should display filter input', () => {
            const compiled = fixture.nativeElement;
            const filterInput = compiled.querySelector('.filter-input');

            expect(filterInput).toBeTruthy();
        });

        it('should filter accounts by email', async () => {
            component.onFilterChange('admin@example.com');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(1);
            expect(component.filteredAccounts[0].email).toBe('admin@example.com');
        });

        it('should filter accounts by name', async () => {
            component.onFilterChange('Regular');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(1);
            expect(component.filteredAccounts[0].name).toBe('Regular User');
        });

        it('should filter accounts by role', async () => {
            component.onFilterChange('admin');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(1);
            expect(component.filteredAccounts[0].name).toBe('Admin User');
        });

        it('should filter accounts by client ID', async () => {
            component.onFilterChange('client-2');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(1);
            expect(component.filteredAccounts[0].name).toBe('Admin User');
        });

        it('should filter accounts by provider', async () => {
            component.onFilterChange('google');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(1);
            expect(component.filteredAccounts[0].authProvider).toBe('google');
        });

        it('should be case-insensitive', async () => {
            component.onFilterChange('ADMIN');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(1);
            expect(component.filteredAccounts[0].email).toBe('admin@example.com');
        });

        it('should show all accounts when filter is empty', async () => {
            component.onFilterChange('');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(3);
        });

        it('should show no accounts when filter matches nothing', async () => {
            component.onFilterChange('nonexistent');
            await Promise.resolve(); // flush microtask in onFilterChange

            expect(component.filteredAccounts.length).toBe(0);
        });

        it('should display "No accounts match" message when filter has no results', async () => {
            component.onFilterChange('nonexistent');
            await Promise.resolve(); // flush microtask in onFilterChange
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const infoMessage = compiled.querySelector('.info-message');

            expect(infoMessage).toBeTruthy();
            expect(infoMessage.textContent).toContain('No accounts match your filter criteria');
        });

        it('should display filter count', async () => {
            component.onFilterChange('native');
            await Promise.resolve(); // flush microtask in onFilterChange
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
            flushClientsRequest();
            fixture.detectChanges();

            expect(component.getAdminCount()).toBe(1);
        });

        it('should display success notice for single admin', () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush([mockAccounts[0]]);
            flushClientsRequest();
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
            flushClientsRequest();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const noticeBox = compiled.querySelector('.notice-box');

            expect(noticeBox).toBeTruthy();
            expect(noticeBox.textContent).toContain('Multiple admin accounts detected');
            expect(noticeBox.textContent).toContain('2 admins');
        });
    });

    describe('Loading Accounts - Error Cases', () => {
        it('should handle HTTP error gracefully', async () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush('Error loading accounts', { status: 500, statusText: 'Server Error' });
            flushClientsRequest();
            fixture.detectChanges();

            // Wait for the 5-second timeout to trigger error handling
            vi.advanceTimersByTime(5000);
            fixture.detectChanges();

            expect(component.error).toBe('Failed to load accounts. Please try again.');
            expect(component.loading).toBe(false);
            expect(component.accounts).toEqual([]);
        });

        it('should display error message on failure', async () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush('Error', { status: 500, statusText: 'Server Error' });
            flushClientsRequest();
            fixture.detectChanges();

            // Wait for the 5-second timeout to trigger error handling
            vi.advanceTimersByTime(5000);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const errorBox = compiled.querySelector('.error-box');

            expect(errorBox).toBeTruthy();
            expect(errorBox.textContent).toContain('Failed to load accounts');
        });

        it('should display retry button on error', async () => {
            fixture.detectChanges();

            const req = httpMock.expectOne('/api/accounts');
            req.flush('Error', { status: 500, statusText: 'Server Error' });
            flushClientsRequest();
            fixture.detectChanges();

            // Wait for the 5-second timeout to trigger error handling
            vi.advanceTimersByTime(5000);
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const retryButton = compiled.querySelector('.error-box button');

            expect(retryButton).toBeTruthy();
            expect(retryButton.textContent).toContain('Retry');
        });
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

    describe('Add Role Functionality', () => {
        const mockClients = [
            {
                id: '1',
                orgId: 'test-org',
                clientId: 'client-1',
                clientName: 'Client 1',
                clientType: 'confidential',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid profile',
                requirePkce: false,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-01T00:00:00Z'
            },
            {
                id: '2',
                orgId: 'test-org',
                clientId: 'client-2',
                clientName: 'Client 2',
                clientType: 'public',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid',
                requirePkce: true,
                autoSubscribe: true,
                publik: true,
                createdAt: '2024-01-02T00:00:00Z'
            }
        ];

        it('should check if user has manage accounts role', () => {
            const authService = TestBed.inject(AuthService);
            (authService.hasRole as Mock).mockReturnValue(true);

            expect(component.hasManageAccountsRole()).toBe(true);
            expect(authService.hasRole).toHaveBeenCalledWith(ROLE_MANAGE_ACCOUNTS);
        });

        it('should start add role form for an account', () => {
            component.startAddRole('account-123');

            expect(component.addingRoleForAccountId).toBe('account-123');
            expect(component.roleFormData).toEqual({ clientId: '', role: '' });
            expect(component.roleFormError).toBeNull();
        });

        it('should cancel add role form', () => {
            component.addingRoleForAccountId = 'account-123';
            component.roleFormData = { clientId: 'client-1', role: 'admin' };
            component.roleFormError = 'Some error';

            component.cancelAddRole();

            expect(component.addingRoleForAccountId).toBeNull();
            expect(component.roleFormData).toEqual({ clientId: '', role: '' });
            expect(component.roleFormError).toBeNull();
        });

        it('should submit role successfully', async () => {
            fixture.detectChanges();

            // Load accounts first
            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            // Load clients
            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.roleFormData = { clientId: 'client-1', role: 'admin' };
            const promise = component.onSubmitRole('account-123');

            const roleReq = httpMock.expectOne('/api/accounts/role');
            expect(roleReq.request.method).toBe('POST');
            expect(roleReq.request.body).toEqual({
                accountId: 'account-123',
                clientId: 'client-1',
                role: 'admin'
            });
            roleReq.flush({ clientId: 'client-1', role: 'admin' });
            await Promise.resolve(); TestBed.flushEffects();

            // Expect accounts to be reloaded
            const reloadReq = httpMock.expectOne('/api/accounts');
            reloadReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            await promise;
            expect(component.addingRoleForAccountId).toBeNull();
            expect(component.roleFormError).toBeNull();
        });

        it('should handle 400 validation error when submitting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.roleFormData = { clientId: 'client-1', role: 'invalid@role' };
            const promise = component.onSubmitRole('account-123');

            const roleReq = httpMock.expectOne('/api/accounts/role');
            roleReq.flush({ error: 'Invalid role format' }, { status: 400, statusText: 'Bad Request' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.roleFormError).toBe('Invalid role format');
            expect(component.roleFormSubmitting).toBe(false);
        });

        it('should handle 403 permission error when submitting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.roleFormData = { clientId: 'client-1', role: 'admin' };
            const promise = component.onSubmitRole('account-123');

            const roleReq = httpMock.expectOne('/api/accounts/role');
            roleReq.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.roleFormError).toBe('You do not have permission to add roles.');
            expect(component.roleFormSubmitting).toBe(false);
        });

        it('should handle 404 account not found error when submitting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.roleFormData = { clientId: 'client-1', role: 'admin' };
            const promise = component.onSubmitRole('non-existent');

            const roleReq = httpMock.expectOne('/api/accounts/role');
            roleReq.flush({ error: 'Account not found' }, { status: 404, statusText: 'Not Found' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.roleFormError).toBe('Account not found.');
            expect(component.roleFormSubmitting).toBe(false);
        });

        it('should handle generic error when submitting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.roleFormData = { clientId: 'client-1', role: 'admin' };
            const promise = component.onSubmitRole('account-123');

            const roleReq = httpMock.expectOne('/api/accounts/role');
            roleReq.error(new ProgressEvent('error'));

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.roleFormError).toBe('Failed to add role. Please try again.');
            expect(component.roleFormSubmitting).toBe(false);
        });

        it('should load clients on init', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            expect(component.clients.length).toBe(2);
            expect(component.clients).toEqual(mockClients);
        });
    });

    describe('Delete Role Functionality', () => {
        const mockClients = [
            {
                id: '1',
                orgId: 'test-org',
                clientId: 'client-1',
                clientName: 'Client 1',
                clientType: 'confidential',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid profile',
                requirePkce: false,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-01T00:00:00Z'
            }
        ];

        it('should delete role successfully', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const promise = component.deleteRole('account-123', 'client-1', 'admin');
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/role');
            expect(deleteReq.request.method).toBe('DELETE');
            expect(deleteReq.request.body).toEqual({
                accountId: 'account-123',
                clientId: 'client-1',
                role: 'admin'
            });
            deleteReq.flush(null, { status: 204, statusText: 'No Content' });
            await Promise.resolve(); TestBed.flushEffects();

            // Expect accounts to be reloaded
            const reloadReq = httpMock.expectOne('/api/accounts');
            reloadReq.flush(mockAccounts);

            await Promise.resolve(); TestBed.flushEffects();
            expect(confirmService.confirm).toHaveBeenCalledWith(expect.objectContaining({ requiredPhrase: 'admin' }));
        });

        it('should not delete role if user cancels confirmation', async () => {
            confirmService.confirm.mockResolvedValue(false);

            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.deleteRole('account-123', 'client-1', 'admin');
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            httpMock.expectNone('/api/accounts/role');
            expect(confirmService.confirm).toHaveBeenCalled();
        });

        it('should handle 403 permission error when deleting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const promise = component.deleteRole('account-123', 'client-1', 'admin');
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/role');
            deleteReq.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

            await Promise.resolve(); TestBed.flushEffects();
            // Error is handled by component - just verify the request was made
            expect(deleteReq.request.method).toBe('DELETE');
        });

        it('should handle 404 error when deleting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const promise = component.deleteRole('non-existent', 'client-1', 'admin');
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/role');
            deleteReq.flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });

            await Promise.resolve(); TestBed.flushEffects();
            // Error is handled by component - just verify the request was made
            expect(deleteReq.request.method).toBe('DELETE');
        });

        it('should handle generic error when deleting role', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const promise = component.deleteRole('account-123', 'client-1', 'admin');
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/role');
            deleteReq.error(new ProgressEvent('error'));

            await Promise.resolve(); TestBed.flushEffects();
            // Error is handled by component - just verify the request was made
            expect(deleteReq.request.method).toBe('DELETE');
        });
    });

    describe('Add Account Functionality', () => {
        const mockClients = [
            {
                id: '1',
                orgId: 'test-org',
                clientId: 'client-1',
                clientName: 'Client 1',
                clientType: 'confidential',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid profile',
                requirePkce: false,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-01T00:00:00Z'
            }
        ];

        it('should toggle add account form', () => {
            expect(component.showAddAccountForm).toBe(false);

            component.toggleAddAccountForm();
            expect(component.showAddAccountForm).toBe(true);
            expect(component.accountFormData).toEqual({ email: '', name: '', authProvider: '' });
            expect(component.formError).toBeNull();

            component.toggleAddAccountForm();
            expect(component.showAddAccountForm).toBe(false);
        });

        it('should reset form when opening add account form', () => {
            component.accountFormData = { email: 'test@example.com', name: 'Test User', authProvider: 'google' };
            component.formError = 'Some error';

            component.toggleAddAccountForm();

            expect(component.accountFormData).toEqual({ email: '', name: '', authProvider: '' });
            expect(component.formError).toBeNull();
        });

        it('should create account successfully', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'newuser@example.com', name: 'New User', authProvider: 'google' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            expect(createReq.request.method).toBe('POST');
            expect(createReq.request.body).toEqual({
                email: 'newuser@example.com',
                name: 'New User',
                authProvider: 'google'
            });

            const newAccount = {
                id: '4',
                email: 'newuser@example.com',
                name: 'NOT YET SIGNED IN',
                emailVerified: false,
                authProvider: 'google',
                picture: null,
                createdAt: '2024-01-04T00:00:00Z',
                roles: []
            };
            createReq.flush({ account: newAccount, inviteToken: 'test-invite-token' });
            await Promise.resolve(); TestBed.flushEffects();

            // Expect accounts to be reloaded
            const reloadReq = httpMock.expectOne('/api/accounts');
            reloadReq.flush([...mockAccounts, newAccount]);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();

            await promise;
            await Promise.resolve(); TestBed.flushEffects(); // Ensure all async operations complete
            fixture.detectChanges();

            // New account: invite link is shown
            expect(component.showInviteLink).toBe(true);
            expect(component.showAddedToOrg).toBe(false);
            expect(component.inviteLink).toContain('test-invite-token');
            expect(component.formError).toBeNull();
        });

        it('should show added-to-org message when account already exists', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'existing@example.com', name: '', authProvider: 'google' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            const existingAccount = {
                id: '1',
                email: 'existing@example.com',
                name: 'Existing User',
                emailVerified: true,
                authProvider: 'google',
                picture: null,
                createdAt: '2024-01-01T00:00:00Z',
                roles: []
            };
            // Backend returns 200 with no inviteToken when adding existing account to org
            createReq.flush({ account: existingAccount });
            await Promise.resolve(); TestBed.flushEffects();

            const reloadReq = httpMock.expectOne('/api/accounts');
            reloadReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();

            await promise;
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            // Existing account added to org: show success message, no invite link
            expect(component.showAddedToOrg).toBe(true);
            expect(component.showInviteLink).toBe(false);
            expect(component.inviteLink).toBeNull();
            expect(component.formError).toBeNull();
        });

        it('should handle 400 validation error with violations array', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'invalid', name: '', authProvider: 'invalid' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            createReq.flush({
                title: 'Constraint Violation',
                status: 400,
                violations: [
                    { field: 'createAccount.request.email', message: 'Email must be valid' },
                    { field: 'createAccount.request.authProvider', message: 'Auth provider must be either \'google\' or \'microsoft\'' }
                ]
            }, { status: 400, statusText: 'Bad Request' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.formError).toBe('Email must be valid; Auth provider must be either \'google\' or \'microsoft\'');
            expect(component.formSubmitting).toBe(false);
        });

        it('should handle 400 error without violations array', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'test@example.com', name: 'Test User', authProvider: 'google' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            createReq.flush({ error: 'Bad request' }, { status: 400, statusText: 'Bad Request' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.formError).toBe('Invalid input. Please check your entries.');
            expect(component.formSubmitting).toBe(false);
        });

        it('should handle 403 permission error', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'test@example.com', name: 'Test User', authProvider: 'google' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            createReq.flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.formError).toBe('You do not have permission to create accounts.');
            expect(component.formSubmitting).toBe(false);
        });

        it('should handle 409 duplicate email error', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'admin@example.com', name: 'Admin User', authProvider: 'google' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            createReq.flush({ error: 'Email already exists' }, { status: 409, statusText: 'Conflict' });

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.formError).toBe('An account with this email already exists.');
            expect(component.formSubmitting).toBe(false);
        });

        it('should handle generic error', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            component.accountFormData = { email: 'test@example.com', name: 'Test User', authProvider: 'google' };
            const promise = component.onSubmitAddAccount();

            const createReq = httpMock.expectOne('/api/accounts');
            createReq.error(new ProgressEvent('error'));

            await Promise.resolve(); TestBed.flushEffects();
            await promise;
            expect(component.formError).toBe('Failed to create account. Please try again.');
            expect(component.formSubmitting).toBe(false);
        });
    });

    describe('Delete Account Functionality', () => {
        const mockClients = [
            {
                id: '1',
                orgId: 'test-org',
                clientId: 'client-1',
                clientName: 'Client 1',
                clientType: 'confidential',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid profile',
                requirePkce: false,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-01T00:00:00Z'
            }
        ];

        it('should delete account when confirmed', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const accountToDelete = mockAccounts[0];
            const promise = component.deleteAccount(accountToDelete);

            // Simulate user confirming the dialog
            await Promise.resolve(); TestBed.flushEffects();

            expect(confirmService.confirm).toHaveBeenCalledWith(expect.objectContaining({ requiredPhrase: accountToDelete.email }));

            const deleteReq = httpMock.expectOne(`/api/accounts/${accountToDelete.id}`);
            expect(deleteReq.request.method).toBe('DELETE');
            deleteReq.flush(null, { status: 204, statusText: 'No Content' });
            await Promise.resolve(); TestBed.flushEffects();

            // Expect accounts to be reloaded
            const reloadReq = httpMock.expectOne('/api/accounts');
            reloadReq.flush(mockAccounts.slice(1));
            await Promise.resolve(); TestBed.flushEffects();

            await promise;
        });

        it('should not delete account when cancelled', async () => {
            confirmService.confirm.mockResolvedValue(false);

            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const accountToDelete = mockAccounts[0];
            component.deleteAccount(accountToDelete);

            await Promise.resolve(); TestBed.flushEffects();

            // No DELETE request should be made if user cancels
            httpMock.expectNone(`/api/accounts/${accountToDelete.id}`);
        });
    });

    describe('Delete Own Account Functionality', () => {
        const mockClients = [
            {
                id: '1',
                orgId: 'test-org',
                clientId: 'client-1',
                clientName: 'Client 1',
                clientType: 'confidential',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid profile',
                requirePkce: false,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-01T00:00:00Z'
            }
        ];

        it('should delete own account when confirmed', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const promise = component.deleteOwnAccount();
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const ownAccount = mockAccounts.find(a => a.id === '1')!;
            expect(confirmService.confirm).toHaveBeenCalledWith(expect.objectContaining({ requiredPhrase: ownAccount.email }));

            const deleteReq = httpMock.expectOne('/api/accounts/me');
            expect(deleteReq.request.method).toBe('DELETE');
            deleteReq.flush(null, { status: 204, statusText: 'No Content' });
            await Promise.resolve(); TestBed.flushEffects();

            await promise;

            expect(toastService.success).toHaveBeenCalledWith('Your account has been deleted successfully');
            expect(authService.signout).toHaveBeenCalled();
        });

        it('should not delete own account when cancelled', async () => {
            confirmService.confirm.mockResolvedValue(false);

            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            component.deleteOwnAccount();
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            httpMock.expectNone('/api/accounts/me');
            expect(authService.signout).not.toHaveBeenCalled();
        });

        it('should show error when own account is not in the list', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts.slice(1)); // Exclude account with id '1'
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            component.deleteOwnAccount();
            await Promise.resolve(); TestBed.flushEffects();

            httpMock.expectNone('/api/accounts/me');
            expect(toastService.error).toHaveBeenCalledWith('Could not find your account in the current list.');
            expect(authService.signout).not.toHaveBeenCalled();
        });

        it('should handle 400 error when deleting own account', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const promise = component.deleteOwnAccount();
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/me');
            deleteReq.flush({ error: 'Cannot delete the account with the only admin role' }, { status: 400, statusText: 'Bad Request' });
            await Promise.resolve(); TestBed.flushEffects();

            await promise;

            expect(toastService.error).toHaveBeenCalledWith('Cannot delete the account with the only admin role');
            expect(authService.signout).not.toHaveBeenCalled();
        });

        it('should handle 404 error when deleting own account', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const promise = component.deleteOwnAccount();
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/me');
            deleteReq.flush({ error: 'Account not found' }, { status: 404, statusText: 'Not Found' });
            await Promise.resolve(); TestBed.flushEffects();

            await promise;

            expect(toastService.error).toHaveBeenCalledWith('Account not found.');
            expect(authService.signout).not.toHaveBeenCalled();
        });

        it('should handle generic error when deleting own account', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const promise = component.deleteOwnAccount();
            await Promise.resolve(); TestBed.flushEffects(); // Wait for confirmation Promise to resolve

            const deleteReq = httpMock.expectOne('/api/accounts/me');
            deleteReq.error(new ProgressEvent('error'));
            await Promise.resolve(); TestBed.flushEffects();

            // Ensure the component promise has settled
            await promise;

            expect(toastService.error).toHaveBeenCalledWith('Failed to delete your account. Please try again.');
            expect(authService.signout).not.toHaveBeenCalled();
        });

        it('should only show delete-my-account button for the current user', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const ownAccountButtons = compiled.querySelectorAll('[title="Delete my account"]');
            const otherAccountButtons = compiled.querySelectorAll('[title="Delete account"]');

            expect(ownAccountButtons.length).toBe(1);
            expect(otherAccountButtons.length).toBe(2);
        });

        it('should show delete-my-account button without manage accounts role', async () => {
            authService.hasRole.mockReturnValue(false);

            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest();
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const ownAccountButtons = compiled.querySelectorAll('[title="Delete my account"]');
            const otherAccountButtons = compiled.querySelectorAll('[title="Delete account"]');

            expect(ownAccountButtons.length).toBe(1);
            expect(otherAccountButtons.length).toBe(0);
        });
    });

    describe('Owner Functionality', () => {
        const mockClients = [
            {
                id: '1',
                orgId: 'test-org',
                clientId: 'client-1',
                clientName: 'Client 1',
                clientType: 'confidential',
                redirectUris: 'http://localhost',
                allowedScopes: 'openid profile',
                requirePkce: false,
                autoSubscribe: true,
                publik: false,
                createdAt: '2024-01-01T00:00:00Z'
            }
        ];

        it('should load owners on init', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            const ownersReq = httpMock.expectOne('/api/organisations/test-org/owners');
            ownersReq.flush(['1']); // Account with id '1' is owner
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.ownerIds).toEqual(['1']);
        });

        it('should identify account as owner when in ownerIds list', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            const ownersReq = httpMock.expectOne('/api/organisations/test-org/owners');
            ownersReq.flush(['1', '2']); // Both account 1 and 2 are owners
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.isAccountOwner(mockAccounts[0])).toBe(true); // id: '1'
            expect(component.isAccountOwner(mockAccounts[1])).toBe(true); // id: '2'
            expect(component.isAccountOwner(mockAccounts[2])).toBe(false); // id: '3'
        });

        it('should reload owners after making someone owner', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            const ownersReq = httpMock.expectOne('/api/organisations/test-org/owners');
            ownersReq.flush(['1']); // Only account 1 is owner initially
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            // Make account 2 an owner
            const promise = component.makeOwner(mockAccounts[1]);

            await Promise.resolve(); TestBed.flushEffects();

            const makeOwnerReq = httpMock.expectOne('/api/organisations/test-org/members/2/owner');
            expect(makeOwnerReq.request.method).toBe('POST');
            makeOwnerReq.flush(null, { status: 204, statusText: 'No Content' });
            await Promise.resolve(); // flush controller.makeOwner resolution
            await Promise.resolve(); // flush component continuation and loadOwners request
            TestBed.flushEffects();

            // Should reload owners after success
            const reloadOwnersReq = httpMock.expectOne('/api/organisations/test-org/owners');
            reloadOwnersReq.flush(['1', '2']); // Now both are owners
            await Promise.resolve(); TestBed.flushEffects();

            await promise;

            expect(component.isAccountOwner(mockAccounts[1])).toBe(true);
        });

        it('should handle error when making owner', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            const ownersReq = httpMock.expectOne('/api/organisations/test-org/owners');
            ownersReq.flush(['1']);
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            const promise = component.makeOwner(mockAccounts[1]);

            await Promise.resolve(); TestBed.flushEffects();

            const makeOwnerReq = httpMock.expectOne('/api/organisations/test-org/members/2/owner');
            makeOwnerReq.flush({ error: 'Already an owner' }, { status: 400, statusText: 'Bad Request' });
            await Promise.resolve(); TestBed.flushEffects();

            await promise;

            // Owners list should not be reloaded on error
            httpMock.expectNone('/api/organisations/test-org/owners');
        });

        it('should not show make owner button for current user', async () => {
            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            const ownersReq = httpMock.expectOne('/api/organisations/test-org/owners');
            ownersReq.flush(['1']);
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();

            // Current user is mocked with sub: '1'
            expect(component.isCurrentUser('1')).toBe(true);
            expect(component.isCurrentUser('2')).toBe(false);
        });

        it('should show a golden owner badge for the current user', async () => {
            const modelService = TestBed.inject(ModelService);
            modelService.setCurrentOrganisation({
                id: 'test-org',
                name: 'Test Org',
                createdAt: '2024-01-01T00:00:00Z',
                roles: ['owner']
            });

            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest(['1']);
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const currentUserTile = compiled.querySelector('.tile.highlighted-tile');
            const ownerBadge = currentUserTile.querySelector('.badge-owner');
            const removeButton = currentUserTile.querySelector('[data-testid="remove-owner-button"]');

            expect(ownerBadge).toBeTruthy();
            expect(removeButton).toBeFalsy();
        });

        it('should show a golden crown for a co-owner when the caller can manage owners', async () => {
            const modelService = TestBed.inject(ModelService);
            modelService.setCurrentOrganisation({
                id: 'test-org',
                name: 'Test Org',
                createdAt: '2024-01-01T00:00:00Z',
                roles: ['owner']
            });

            fixture.detectChanges();

            const accountsReq = httpMock.expectOne('/api/accounts');
            accountsReq.flush(mockAccounts);
            await Promise.resolve(); TestBed.flushEffects();

            flushOwnersRequest(['1', '2']);
            await Promise.resolve(); TestBed.flushEffects();

            const clientsReq = httpMock.expectOne('/api/clients');
            clientsReq.flush(mockClients);
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const tiles = compiled.querySelectorAll('.tile');
            const coOwnerTile = tiles[1]; // account with id '2'

            const ownerBadge = coOwnerTile.querySelector('.badge-owner');
            const makeOwnerButton = coOwnerTile.querySelector('button[title="Make owner"]');
            const removeOwnerButton = coOwnerTile.querySelector('[data-testid="remove-owner-button"]');

            // The badge is replaced by the management action for manageable co-owners,
            // but the action button itself must be styled as a golden crown.
            expect(ownerBadge).toBeFalsy();
            expect(makeOwnerButton).toBeFalsy();
            expect(removeOwnerButton).toBeTruthy();
            expect(removeOwnerButton.classList.contains('btn-icon-owner-active')).toBe(true);
        });
    });
});
