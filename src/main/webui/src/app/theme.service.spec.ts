import type { MockedObject } from "vitest";
import { createMock } from '../testing/vitest-mocks';
import { TestBed } from '@angular/core/testing';
import { ThemeService, Theme } from './theme.service';

describe('ThemeService', () => {
    let service: ThemeService;
    let localStorageSpy: MockedObject<Storage>;
    let originalLocalStorage: Storage;

    beforeEach(() => {
        // Preserve the original localStorage so it can be restored after the test
        originalLocalStorage = window.localStorage;

        // Create a spy for localStorage
        localStorageSpy = createMock<Storage>({
            getItem: vi.fn().mockName("localStorage.getItem"),
            setItem: vi.fn().mockName("localStorage.setItem"),
            removeItem: vi.fn().mockName("localStorage.removeItem")
        });
        (localStorageSpy.getItem as any).mockReturnValue(null); // Default to null

        // Replace the global localStorage with our spy
        Object.defineProperty(window, 'localStorage', {
            value: localStorageSpy,
            writable: true
        });

        TestBed.configureTestingModule({});
        service = TestBed.inject(ThemeService);
    });

    afterEach(() => {
        // Clean up DOM state
        document.documentElement.removeAttribute('data-theme');

        // Restore the original localStorage implementation
        Object.defineProperty(window, 'localStorage', {
            value: originalLocalStorage,
            writable: true
        });
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should initialize with light theme by default when no saved preference and no system preference', () => {
        (localStorageSpy.getItem as any).mockReturnValue(null);

        // Mock matchMedia to return false for dark mode
        const matchMediaSpy = vi.fn().mockName('matchMedia').mockReturnValue({
            matches: false,
            media: '',
            onchange: null,
            addListener: vi.fn(),
            removeListener: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn()
        });
        Object.defineProperty(window, 'matchMedia', {
            writable: true,
            value: matchMediaSpy
        });

        // Create a new instance to test initialization
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({});
        const newService = TestBed.inject(ThemeService);

        expect(newService.theme$()).toBe('light');
    });

    it('should initialize with saved theme preference from localStorage', () => {
        (localStorageSpy.getItem as any).mockReturnValue('dark');

        TestBed.resetTestingModule();
        TestBed.configureTestingModule({});
        const newService = TestBed.inject(ThemeService);

        expect(newService.theme$()).toBe('dark');
    });

    it('should toggle theme from light to dark', () => {
        service.theme$.set('light');
        service.toggleTheme();

        expect(service.theme$()).toBe('dark');
    });

    it('should toggle theme from dark to light', () => {
        service.theme$.set('dark');
        service.toggleTheme();

        expect(service.theme$()).toBe('light');
    });

    it('should set specific theme', () => {
        service.setTheme('dark');
        expect(service.theme$()).toBe('dark');

        service.setTheme('light');
        expect(service.theme$()).toBe('light');
    });

    it('should apply theme to document element', () => {
        service.setTheme('dark');

        TestBed.flushEffects();
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('should save theme to localStorage when changed', () => {
        service.setTheme('dark');

        TestBed.flushEffects();
        expect(localStorageSpy.setItem).toHaveBeenCalledWith('abstrauth-theme', 'dark');
    });

    it('should respect system preference when no saved preference', () => {
        (localStorageSpy.getItem as any).mockReturnValue(null);

        // Mock matchMedia to return dark mode preference
        const matchMediaSpy = vi.fn().mockName('matchMedia').mockReturnValue({
            matches: true,
            media: '(prefers-color-scheme: dark)',
            onchange: null,
            addListener: vi.fn(),
            removeListener: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn()
        });

        Object.defineProperty(window, 'matchMedia', {
            value: matchMediaSpy,
            writable: true
        });

        TestBed.resetTestingModule();
        TestBed.configureTestingModule({});
        const newService = TestBed.inject(ThemeService);

        expect(newService.theme$()).toBe('dark');
    });
});
