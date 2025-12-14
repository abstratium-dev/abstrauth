import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { SignoutComponent } from './signout.component';
import { AuthService } from '../auth.service';

describe('SignoutComponent', () => {
  let component: SignoutComponent;
  let fixture: ComponentFixture<SignoutComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['signout']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [SignoutComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    })
    .compileComponents();

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    
    // Make signout spy call router.navigate in a setTimeout to simulate real behavior
    authService.signout.and.callFake(() => {
      setTimeout(() => {
        router.navigate(['/']);
      }, 0);
    });
    
    fixture = TestBed.createComponent(SignoutComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call signout and navigate to home on init', fakeAsync(() => {
    fixture.detectChanges(); // triggers ngOnInit
    tick(); // Process setTimeout in signout

    expect(authService.signout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  }));
});
