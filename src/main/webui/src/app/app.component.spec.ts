import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { ModelService } from './model.service';
import { DomainService } from './domain.service';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';

describe('AppComponent', () => {
  let mockModelService: jasmine.SpyObj<ModelService>;

  beforeEach(async () => {
    mockModelService = jasmine.createSpyObj('ModelService', ['setCurrentOrganisation'], {
      insecureClientSecret$: signal(false).asReadonly(),
      warningMessage$: signal('').asReadonly(),
      currentOrganisation$: signal(null).asReadonly()
    });

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: ModelService, useValue: mockModelService },
        { provide: DomainService, useValue: { isAbstratiumDomain: true } }
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
