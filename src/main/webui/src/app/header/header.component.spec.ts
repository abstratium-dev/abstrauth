import { vi, type Mock, type MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';

import { HeaderComponent } from './header.component';
import { AuthService, Token, ANONYMOUS } from '../auth.service';
import { Controller } from '../controller';
import { ThemeService, Theme } from '../theme.service';
import { ModelService } from '../model.service';
import { signal, WritableSignal } from '@angular/core';

describe('HeaderComponent', () => {
    let component: HeaderComponent;
    let fixture: ComponentFixture<HeaderComponent>;
    let httpMock: HttpTestingController;
    let authServiceSpy: MockedObject<AuthService>;
    let themeServiceMock: {
        theme$: WritableSignal<Theme>;
        toggleTheme: Mock;
    };
    let tokenSignal: WritableSignal<Token>;

    const mockTokenWithOrg: Token = {
        ...ANONYMOUS,
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        isAuthenticated: true,
        orgId: 'org-123'
    };

    beforeEach(async () => {
        // Create a writable signal that can be updated
        tokenSignal = signal<Token>(ANONYMOUS);

        authServiceSpy = createMock<AuthService>({
            signout: vi.fn().mockName("AuthService.signout"),
            getLastOrgId: vi.fn().mockName("AuthService.getLastOrgId"),
            setLastOrgId: vi.fn().mockName("AuthService.setLastOrgId"),
            token$: tokenSignal
        });

        themeServiceMock = {
            theme$: signal<Theme>('light'),
            toggleTheme: vi.fn().mockName('ThemeService.toggleTheme')
        };

        await TestBed.configureTestingModule({
            imports: [HeaderComponent],
            providers: [
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AuthService, useValue: authServiceSpy },
                { provide: ThemeService, useValue: themeServiceMock },
                { provide: ActivatedRoute, useValue: {} },
                Controller
            ]
        })
            .compileComponents();

        httpMock = TestBed.inject(HttpTestingController);
        TestBed.inject(ModelService).reset();
        fixture = TestBed.createComponent(HeaderComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();

        // Mock the /public/config request that's called in ngOnInit
        const configReq = httpMock.expectOne('/public/config');
        configReq.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 900 });

        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display sign in link when user is not signed in', () => {
        const compiled = fixture.nativeElement as HTMLElement;
        const signinLink = compiled.querySelector('#signin-link');
        const userLink = compiled.querySelector('#user-link');

        expect(signinLink).toBeTruthy();
        expect(signinLink?.textContent).toContain('Sign in');
        expect(userLink).toBeFalsy();
    });

    it('should display signed-in navigation links when user is authenticated', async () => {
        tokenSignal.set(mockTokenWithOrg);
        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();

        // Component effect triggers loadCurrentOrganisation
        const orgReq = httpMock.expectOne('/api/organisations/current');
        orgReq.flush({ id: 'org-123', name: 'Test Org', createdAt: '2024-01-01T00:00:00Z' });
        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.querySelector('#clients-link')).toBeTruthy();
        expect(compiled.querySelector('#accounts-link')).toBeTruthy();
        expect(compiled.querySelector('#organisations-link')).toBeTruthy();
        expect(compiled.querySelector('#user-link')).toBeTruthy();
        expect(compiled.querySelector('#signin-link')).toBeFalsy();
        expect(compiled.querySelector('#signout-link')).toBeTruthy();
    });

    it('should display current organisation link when signed in with an org', async () => {
        tokenSignal.set(mockTokenWithOrg);
        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();

        const orgReq = httpMock.expectOne('/api/organisations/current');
        orgReq.flush({ id: 'org-123', name: 'Test Org', createdAt: '2024-01-01T00:00:00Z' });
        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const currentOrgLink = compiled.querySelector('#current-org-link');
        expect(currentOrgLink).toBeTruthy();
        expect(currentOrgLink?.textContent).toContain('Test Org');
    });

    it('should render light mode toggle button', () => {
        const compiled = fixture.nativeElement as HTMLElement;
        const toggleButton = compiled.querySelector('.theme-toggle-btn') as HTMLButtonElement;
        expect(toggleButton).toBeTruthy();
        expect(toggleButton?.getAttribute('aria-label')).toContain('Switch to dark mode');
        expect(toggleButton?.textContent).toContain('🌙');
    });

    it('should render dark mode toggle button', () => {
        themeServiceMock.theme$.set('dark');
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const toggleButton = compiled.querySelector('.theme-toggle-btn') as HTMLButtonElement;
        expect(toggleButton?.getAttribute('aria-label')).toContain('Switch to light mode');
        expect(toggleButton?.textContent).toContain('☀️');
    });

    it('should call toggleTheme when theme button is clicked', () => {
        const compiled = fixture.nativeElement as HTMLElement;
        const toggleButton = compiled.querySelector('.theme-toggle-btn') as HTMLButtonElement;
        toggleButton.click();
        expect(themeServiceMock.toggleTheme).toHaveBeenCalled();
    });

    it('should navigate to authorize when sign in link is clicked', () => {
        const router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockResolvedValue(true);

        const compiled = fixture.nativeElement as HTMLElement;
        const signinLink = compiled.querySelector('#signin-link') as HTMLElement;
        signinLink.click();

        expect(router.navigate).toHaveBeenCalledWith(['/authorize']);
    });

    it('should call signout when sign out link is clicked', async () => {
        tokenSignal.set(mockTokenWithOrg);
        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();

        const orgReq = httpMock.expectOne('/api/organisations/current');
        orgReq.flush({ id: 'org-123', name: 'Test Org', createdAt: '2024-01-01T00:00:00Z' });
        await Promise.resolve(); TestBed.flushEffects();
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const signoutLink = compiled.querySelector('#signout-link') as HTMLElement;
        signoutLink.click();

        expect(authServiceSpy.signout).toHaveBeenCalled();
    });

    afterEach(() => {
        httpMock.verify();
        sessionStorage.clear();
        TestBed.resetTestingModule();
    });

    describe('Email mismatch warning', () => {
        beforeEach(async () => {
            TestBed.resetTestingModule();
            sessionStorage.setItem('emailMismatchWarning', 'The invite email does not match');

            tokenSignal = signal<Token>(ANONYMOUS);

            authServiceSpy = createMock<AuthService>({
                signout: vi.fn().mockName("AuthService.signout"),
                getLastOrgId: vi.fn().mockName("AuthService.getLastOrgId"),
                setLastOrgId: vi.fn().mockName("AuthService.setLastOrgId"),
                token$: tokenSignal
            });

            themeServiceMock = {
                theme$: signal<Theme>('light'),
                toggleTheme: vi.fn().mockName('ThemeService.toggleTheme')
            };

            await TestBed.configureTestingModule({
                imports: [HeaderComponent],
                providers: [
                    provideHttpClient(withXhr()),
                    provideHttpClientTesting(),
                    provideRouter([]),
                    { provide: AuthService, useValue: authServiceSpy },
                    { provide: ThemeService, useValue: themeServiceMock },
                    { provide: ActivatedRoute, useValue: {} },
                    Controller
                ]
            })
                .compileComponents();

            httpMock = TestBed.inject(HttpTestingController);
            TestBed.inject(ModelService).reset();
            fixture = TestBed.createComponent(HeaderComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();

            const configReq = httpMock.expectOne('/public/config');
            configReq.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 900 });

            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();
        });

        it('should display email mismatch warning', () => {
            const compiled = fixture.nativeElement as HTMLElement;
            const warning = compiled.querySelector('.warning-box');
            expect(warning).toBeTruthy();
            expect(warning?.textContent).toContain('The invite email does not match');
        });
    });
});