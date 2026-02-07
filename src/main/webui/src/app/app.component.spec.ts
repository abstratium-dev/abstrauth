import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { ModelService } from './model.service';
import { signal } from '@angular/core';

describe('AppComponent', () => {
  let mockModelService: jasmine.SpyObj<ModelService>;

  beforeEach(async () => {
    mockModelService = jasmine.createSpyObj('ModelService', [], {
      insecureClientSecret$: signal(false).asReadonly(),
      warningMessage$: signal('').asReadonly()
    });

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: ModelService, useValue: mockModelService }
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

  it('should dismiss environment warning when dismiss button is clicked', () => {
    const warningSignal = signal('You are in a development environment');
    Object.defineProperty(mockModelService, 'warningMessage$', {
      value: warningSignal.asReadonly()
    });

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    
    let banner = fixture.nativeElement.querySelector('.environment-warning');
    expect(banner).toBeTruthy();
    
    const dismissButton = banner?.querySelector('.btn-dismiss') as HTMLButtonElement;
    dismissButton.click();
    fixture.detectChanges();
    
    banner = fixture.nativeElement.querySelector('.environment-warning');
    expect(banner).toBeFalsy();
  });

  it('should have showEnvironmentWarning initially true', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.showEnvironmentWarning).toBe(true);
  });

  it('should set showEnvironmentWarning to false when dismissEnvironmentWarning is called', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    
    app.dismissEnvironmentWarning();
    
    expect(app.showEnvironmentWarning).toBe(false);
  });
});
