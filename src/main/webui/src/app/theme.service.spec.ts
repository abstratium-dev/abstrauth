import { TestBed } from '@angular/core/testing';
import { ThemeService, Theme } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;
  let localStorageSpy: jasmine.SpyObj<Storage>;
  let originalLocalStorage: Storage;

  beforeEach(() => {
    // Preserve the original localStorage so it can be restored after the test
    originalLocalStorage = window.localStorage;

    // Create a spy for localStorage
    localStorageSpy = jasmine.createSpyObj('localStorage', ['getItem', 'setItem', 'removeItem']);
    localStorageSpy.getItem.and.returnValue(null); // Default to null
    
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
    localStorageSpy.getItem.and.returnValue(null);
    
    // Mock matchMedia to return false for dark mode
    const matchMediaSpy = jasmine.createSpy('matchMedia').and.returnValue({
      matches: false,
      media: '',
      onchange: null,
      addListener: jasmine.createSpy(),
      removeListener: jasmine.createSpy(),
      addEventListener: jasmine.createSpy(),
      removeEventListener: jasmine.createSpy(),
      dispatchEvent: jasmine.createSpy()
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
    localStorageSpy.getItem.and.returnValue('dark');
    
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

  it('should apply theme to document element', (done) => {
    service.setTheme('dark');
    
    // Wait for effect to run
    setTimeout(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      done();
    }, 100);
  });

  it('should save theme to localStorage when changed', (done) => {
    service.setTheme('dark');
    
    // Wait for effect to run
    setTimeout(() => {
      expect(localStorageSpy.setItem).toHaveBeenCalledWith('abstrauth-theme', 'dark');
      done();
    }, 100);
  });

  it('should respect system preference when no saved preference', () => {
    localStorageSpy.getItem.and.returnValue(null);
    
    // Mock matchMedia to return dark mode preference
    const matchMediaSpy = jasmine.createSpy('matchMedia').and.returnValue({
      matches: true,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: jasmine.createSpy(),
      removeListener: jasmine.createSpy(),
      addEventListener: jasmine.createSpy(),
      removeEventListener: jasmine.createSpy(),
      dispatchEvent: jasmine.createSpy()
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
