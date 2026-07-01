import type { MockedObject } from "vitest";
import { createMock } from '../../testing/vitest-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Router } from '@angular/router';
import { HomeComponent } from './home.component';
import { AuthService } from '../auth.service';

describe('HomeComponent', () => {
    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let mockRouter: MockedObject<Router>;
    let mockAuthService: MockedObject<AuthService>;

    beforeEach(async () => {
        mockRouter = createMock<Router>({
            navigate: vi.fn().mockName("Router.navigate")
        });
        mockAuthService = createMock<AuthService>({
            getAccessToken: vi.fn().mockName("AuthService.getAccessToken"),
            token$: vi.fn().mockName('token$').mockReturnValue({
                isAuthenticated: false,
                email: 'anon@abstratium.dev'
            } as any)
        });

        await TestBed.configureTestingModule({
            imports: [HomeComponent],
            providers: [
                provideZonelessChangeDetection(),
                { provide: Router, useValue: mockRouter },
                { provide: AuthService, useValue: mockAuthService }
            ]
        })
            .compileComponents();

        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});