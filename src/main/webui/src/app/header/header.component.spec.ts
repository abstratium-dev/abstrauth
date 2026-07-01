import { vi, type Mock, type MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { HeaderComponent } from './header.component';
import { AuthService, Token, ANONYMOUS } from '../auth.service';
import { Controller } from '../controller';
import { ThemeService } from '../theme.service';
import { signal, WritableSignal } from '@angular/core';

describe('HeaderComponent', () => {
    let component: HeaderComponent;
    let fixture: ComponentFixture<HeaderComponent>;
    let httpMock: HttpTestingController;
    let authServiceSpy: MockedObject<AuthService>;
    let themeServiceMock: {
        theme$: Mock;
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

        themeServiceMock = createMock<ThemeService>({
            theme$: vi.fn().mockName('theme$').mockReturnValue('light'),
            toggleTheme: vi.fn().mockName('ThemeService.toggleTheme')
        });

        await TestBed.configureTestingModule({
            imports: [HeaderComponent],
            providers: [
                provideZonelessChangeDetection(),
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
        fixture = TestBed.createComponent(HeaderComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();

        // Mock the /public/config request that's called in ngOnInit
        const configReq = httpMock.expectOne('/public/config');
        configReq.flush({ signupAllowed: false, allowNativeSignin: false, sessionTimeoutSeconds: 900 });
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

});