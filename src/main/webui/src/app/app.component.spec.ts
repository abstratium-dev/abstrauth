import type { MockedObject } from "vitest";
import { createMock } from '../testing/vitest-mocks';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { AppComponent } from './app.component';
import { ModelService } from './model.service';
import { DomainService } from './domain.service';
import { Controller } from './controller';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';

describe('AppComponent', () => {
    let mockModelService: MockedObject<ModelService>;

    beforeEach(async () => {
        mockModelService = createMock<ModelService>({
            setCurrentOrganisation: vi.fn().mockName("ModelService.setCurrentOrganisation"),
            insecureClientSecret$: signal(false).asReadonly(),
            warningMessage$: signal('').asReadonly(),
            currentOrganisation$: signal(null).asReadonly(),
            legalContent$: signal(null).asReadonly(),
            brandLogoUrl$: signal('https://abstratium.dev/abstratium-logo-small.png').asReadonly(),
            brandLogoAlt$: signal('Abstratium Logo').asReadonly(),
            brandName$: signal('ABSTRATIUM').asReadonly()
        });

        const mockController = createMock<Controller>({
            loadConfig: vi.fn().mockName("Controller.loadConfig"),
            loadCurrentOrganisation: vi.fn().mockName("Controller.loadCurrentOrganisation")
        });

        await TestBed.configureTestingModule({
            imports: [AppComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideRouter([]),
                { provide: ModelService, useValue: mockModelService },
                { provide: DomainService, useValue: { isAbstratiumDomain: true } },
                { provide: Controller, useValue: mockController }
            ]
        }).compileComponents();
    });

    it('should create the app', () => {
        const fixture = TestBed.createComponent(AppComponent);
        const app = fixture.componentInstance;
        expect(app).toBeTruthy();
    });

    it(`should have the 'abstrauth' title`, () => {
        const fixture = TestBed.createComponent(AppComponent);
        const app = fixture.componentInstance;
        expect(app.title).toEqual('abstrauth');
    });

    it('should render router outlet', () => {
        const fixture = TestBed.createComponent(AppComponent);
        fixture.detectChanges();
        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.querySelector('router-outlet')).toBeTruthy();
    });

    it('should display environment warning banner when warningMessage is set', () => {
        const warningSignal = signal('You are in a development environment');
        Object.defineProperty(mockModelService, 'warningMessage$', {
            value: warningSignal.asReadonly()
        });

        const fixture = TestBed.createComponent(AppComponent);
        fixture.detectChanges();
        const compiled = fixture.nativeElement as HTMLElement;
        const banner = compiled.querySelector('.environment-warning');

        expect(banner).toBeTruthy();
        expect(banner?.textContent).toContain('You are in a development environment');
    });

    it('should not display environment warning banner when warningMessage is empty', () => {
        const warningSignal = signal('');
        Object.defineProperty(mockModelService, 'warningMessage$', {
            value: warningSignal.asReadonly()
        });

        const fixture = TestBed.createComponent(AppComponent);
        fixture.detectChanges();
        const compiled = fixture.nativeElement as HTMLElement;
        const banner = compiled.querySelector('.environment-warning');

        expect(banner).toBeFalsy();
    });

    it('should not have a dismiss button on the environment warning banner', () => {
        const warningSignal = signal('You are in a development environment');
        Object.defineProperty(mockModelService, 'warningMessage$', {
            value: warningSignal.asReadonly()
        });

        const fixture = TestBed.createComponent(AppComponent);
        fixture.detectChanges();
        const banner = fixture.nativeElement.querySelector('.environment-warning');
        expect(banner).toBeTruthy();
        expect(banner?.querySelector('.btn-dismiss')).toBeFalsy();
    });
});