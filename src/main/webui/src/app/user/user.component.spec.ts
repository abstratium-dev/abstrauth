import type { MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { signal } from '@angular/core';
import { UserComponent } from './user.component';
import { AuthService, Token, ANONYMOUS, ISSUER } from '../auth.service';
import { Controller } from '../controller';
import { ModelService, PersonalData } from '../model.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../shared/toast/toast.service';

describe('UserComponent', () => {
    let component: UserComponent;
    let fixture: ComponentFixture<UserComponent>;
    let authService: any;
    let tokenSignal: any;
    let controllerSpy: MockedObject<Controller>;
    let modelService: Partial<ModelService>;
    let confirmServiceSpy: MockedObject<ConfirmDialogService>;
    let toastServiceSpy: MockedObject<ToastService>;
    let personalDataSignal: any;
    let personalDataLoadingSignal: any;
    let personalDataErrorSignal: any;

    const mockToken: Token = {
        iss: ISSUER,
        sub: 'user-123',
        groups: ['admin', 'users'],
        email: 'test@example.com',
        email_verified: true,
        name: 'Test User',
        iat: 1609459200,
        exp: 1609545600,
        isAuthenticated: true,
        client_id: 'test-client',
        jti: 'jwt-id-123',
        upn: 'test@example.com',
        auth_method: 'native'
    };

    const mockPersonalData: PersonalData = {
        account: {
            id: 'user-123',
            email: 'test@example.com',
            name: 'Test User',
            emailVerified: true,
            authProvider: 'native',
            picture: null,
            createdAt: '2021-01-01T00:00:00'
        },
        credentials: [{
                id: 'cred-1',
                username: 'test@example.com',
                failedLoginAttempts: 0,
                lockedUntil: null,
                createdAt: '2021-01-01T00:00:00'
            }],
        federatedIdentities: [{
                id: 'fed-1',
                provider: 'google',
                providerUserId: 'google-123',
                email: 'google@example.com',
                connectedAt: '2021-01-01T00:00:00'
            }],
        organisationMemberships: [{
                orgId: 'org-1',
                organisationName: 'Test Organisation',
                role: 'owner',
                addedAt: '2021-01-01T00:00:00'
            }],
        roles: [{
                id: 'role-1',
                clientId: 'abstratium-abstrauth',
                role: 'user',
                orgId: 'org-1',
                createdAt: '2021-01-01T00:00:00'
            }],
        exportTimestamp: '2021-01-01T00:00:00'
    };

    beforeEach(async () => {
        tokenSignal = signal<Token>(mockToken);
        personalDataSignal = signal<PersonalData | null>(mockPersonalData);
        personalDataLoadingSignal = signal(false);
        personalDataErrorSignal = signal<string | null>(null);

        authService = {
            token$: tokenSignal,
            signout: vi.fn().mockName('signout')
        };

        controllerSpy = createMock<Controller>({
            loadPersonalData: vi.fn().mockName("Controller.loadPersonalData"),
            exportPersonalData: vi.fn().mockName("Controller.exportPersonalData"),
            deleteOwnAccount: vi.fn().mockName("Controller.deleteOwnAccount")
        });
        controllerSpy.loadPersonalData.mockResolvedValue(undefined);

        modelService = {
            personalData$: personalDataSignal.asReadonly(),
            personalDataLoading$: personalDataLoadingSignal.asReadonly(),
            personalDataError$: personalDataErrorSignal.asReadonly()
        };

        confirmServiceSpy = createMock<ConfirmDialogService>({
            confirm: vi.fn().mockName("ConfirmDialogService.confirm")
        });
        confirmServiceSpy.confirm.mockResolvedValue(true);

        toastServiceSpy = createMock<ToastService>({
            success: vi.fn().mockName("ToastService.success"),
            error: vi.fn().mockName("ToastService.error")
        });

        await TestBed.configureTestingModule({
            imports: [UserComponent],
            providers: [
                provideZonelessChangeDetection(),
                { provide: AuthService, useValue: authService },
                { provide: Controller, useValue: controllerSpy },
                { provide: ModelService, useValue: modelService },
                { provide: ConfirmDialogService, useValue: confirmServiceSpy },
                { provide: ToastService, useValue: toastServiceSpy }
            ]
        })
            .compileComponents();

        authService = TestBed.inject(AuthService);
        fixture = TestBed.createComponent(UserComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load personal data on init', () => {
        fixture.detectChanges();
        expect(controllerSpy.loadPersonalData).toHaveBeenCalled();
    });

    it('should display account details', () => {
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('test@example.com');
        expect(compiled.textContent).toContain('Test User');
    });

    it('should display organisation memberships', () => {
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('Test Organisation');
        expect(compiled.textContent).toContain('owner');
    });

    it('should display credentials without password hash', () => {
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('test@example.com');
        // Password hash is never included in the personal data response
        expect(compiled.textContent).not.toContain('passwordHash');
    });

    it('should display federated identities', () => {
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('google');
        expect(compiled.textContent).toContain('google-123');
    });

    it('should display roles', () => {
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('abstratium-abstrauth');
        expect(compiled.textContent).toContain('user');
    });

    it('should show loading state', () => {
        personalDataLoadingSignal.set(true);
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('Loading your personal data');
    });

    it('should show error state', () => {
        personalDataErrorSignal.set('Failed to load data');
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.textContent).toContain('Failed to load data');
    });

    it('should download personal data on button click', async () => {
        controllerSpy.exportPersonalData.mockResolvedValue();
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const downloadButton = compiled.querySelector('button.btn-secondary');
        expect(downloadButton).toBeTruthy();
        downloadButton.click();
        await Promise.resolve(); TestBed.flushEffects();

        expect(controllerSpy.exportPersonalData).toHaveBeenCalled();
        expect(toastServiceSpy.success).toHaveBeenCalledWith('Your data export has started.');
    });

    it('should handle download error', async () => {
        controllerSpy.exportPersonalData.mockRejectedValue(new Error('Export failed'));
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const downloadButton = compiled.querySelector('button.btn-secondary');
        downloadButton.click();
        await Promise.resolve(); TestBed.flushEffects();

        expect(toastServiceSpy.error).toHaveBeenCalledWith('Failed to download your data. Please try again.');
    });

    it('should delete account after confirmation', async () => {
        controllerSpy.deleteOwnAccount.mockResolvedValue();
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const deleteButton = compiled.querySelector('button.btn-danger');
        expect(deleteButton).toBeTruthy();
        deleteButton.click();
        await Promise.resolve(); TestBed.flushEffects();
        await Promise.resolve(); TestBed.flushEffects();

        expect(confirmServiceSpy.confirm).toHaveBeenCalledWith(expect.objectContaining({ requiredPhrase: mockToken.email }));
        expect(controllerSpy.deleteOwnAccount).toHaveBeenCalled();
        expect(toastServiceSpy.success).toHaveBeenCalledWith('Your account has been deleted successfully');
        expect(authService.signout).toHaveBeenCalled();
    });

    it('should not delete account when cancelled', async () => {
        confirmServiceSpy.confirm.mockResolvedValue(false);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const deleteButton = compiled.querySelector('button.btn-danger');
        deleteButton.click();
        await Promise.resolve(); TestBed.flushEffects();

        expect(controllerSpy.deleteOwnAccount).not.toHaveBeenCalled();
    });

    it('should handle delete error with server message', async () => {
        const error = { status: 400, error: { error: 'Cannot delete the only admin' } };
        controllerSpy.deleteOwnAccount.mockRejectedValue(error);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const deleteButton = compiled.querySelector('button.btn-danger');
        deleteButton.click();
        await Promise.resolve(); TestBed.flushEffects();
        await Promise.resolve(); TestBed.flushEffects();

        expect(toastServiceSpy.error).toHaveBeenCalledWith('Cannot delete the only admin');
        expect(authService.signout).not.toHaveBeenCalled();
    });

    describe('Token Claims', () => {
        it('should extract token claims', () => {
            fixture.detectChanges();
            const claimKeys = component.tokenClaims.map(c => c.key);
            expect(claimKeys).toContain('sub');
            expect(claimKeys).toContain('email');
            expect(claimKeys).toContain('name');
        });

        it('should hide token claims by default', () => {
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const tokenCard = compiled.querySelector('.token-claims-card');
            expect(tokenCard).toBeTruthy();
            const dataTable = tokenCard.querySelector('.data-table');
            expect(dataTable).toBeFalsy();
        });

        it('should show token claims after toggling', () => {
            fixture.detectChanges();
            component.toggleTokenClaims();
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const tokenCard = compiled.querySelector('.token-claims-card');
            const dataTable = tokenCard.querySelector('.data-table');
            expect(dataTable).toBeTruthy();
        });

        it('should format timestamp claims as ISO date string', () => {
            fixture.detectChanges();
            const iatClaim = component.tokenClaims.find(c => c.key === 'iat');
            expect(iatClaim?.value).toContain('2021-01-01');
        });

        it('should format boolean as string', () => {
            const falseToken = { ...mockToken, email_verified: false };
            tokenSignal.set(falseToken);
            fixture.detectChanges();
            const emailVerifiedClaim = component.tokenClaims.find(c => c.key === 'email_verified');
            expect(emailVerifiedClaim?.value).toBe('false');
        });
    });

    describe('isArray method', () => {
        it('should return true for arrays', () => {
            expect(component.isArray(['item1', 'item2'])).toBe(true);
        });

        it('should return false for strings', () => {
            expect(component.isArray('not an array')).toBe(false);
        });

        it('should return false for null', () => {
            expect(component.isArray(null)).toBe(false);
        });
    });

    describe('formatTimestamp', () => {
        it('should format a valid timestamp', () => {
            expect(component.formatTimestamp('2021-01-01T00:00:00')).not.toBe('-');
        });

        it('should return dash for null', () => {
            expect(component.formatTimestamp(null)).toBe('-');
        });
    });

    describe('UI Layout', () => {
        it('should display page title', () => {
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const title = compiled.querySelector('h1');
            expect(title?.textContent).toContain('User Profile');
        });

        it('should render personal data cards', () => {
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            const cards = compiled.querySelectorAll('.card');
            expect(cards.length).toBeGreaterThan(1);
        });
    });
});