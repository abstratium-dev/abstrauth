import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ChangePasswordComponent } from './change-password.component';
import { Controller } from '../controller';
import { ToastService } from '../shared/toast/toast.service';

describe('ChangePasswordComponent', () => {
  let component: ChangePasswordComponent;
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let router: Router;
  let httpMock: HttpTestingController;
  let toastService: ToastService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent, ReactiveFormsModule],
      providers: [
        Controller, 
        ToastService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChangePasswordComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    httpMock = TestBed.inject(HttpTestingController);
    toastService = TestBed.inject(ToastService);
    
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should redirect to home if requirePasswordChange flag is not set', () => {
    sessionStorage.removeItem('requirePasswordChange');
    
    component.ngOnInit();
    
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should prefill old password from invite data', () => {
    sessionStorage.setItem('requirePasswordChange', 'true');
    sessionStorage.setItem('inviteData', JSON.stringify({
      authProvider: 'native',
      email: 'test@example.com',
      password: 'tempPassword123'
    }));
    
    component.ngOnInit();
    
    expect(component.passwordForm.get('oldPassword')?.value).toBe('tempPassword123');
  });

  it('should validate that new passwords match', () => {
    sessionStorage.setItem('requirePasswordChange', 'true');
    
    component.ngOnInit();
    fixture.detectChanges();
    
    component.passwordForm.patchValue({
      oldPassword: 'oldPass123',
      newPassword: 'newPass123',
      confirmPassword: 'differentPass123'
    });
    component.passwordForm.get('confirmPassword')?.markAsTouched();
    
    expect(component.confirmPasswordInvalid).toBe(true);
    
    component.passwordForm.patchValue({
      confirmPassword: 'newPass123'
    });
    
    expect(component.confirmPasswordInvalid).toBe(false);
  });

  it('should validate password length', () => {
    sessionStorage.setItem('requirePasswordChange', 'true');
    
    component.ngOnInit();
    fixture.detectChanges();
    
    component.passwordForm.patchValue({
      newPassword: 'short'
    });
    
    expect(component.passwordForm.get('newPassword')?.hasError('minlength')).toBe(true);
    
    component.passwordForm.patchValue({
      newPassword: 'longEnoughPassword123'
    });
    
    expect(component.passwordForm.get('newPassword')?.hasError('minlength')).toBe(false);
  });

  it('should successfully change password and redirect', async () => {
    sessionStorage.setItem('requirePasswordChange', 'true');
    sessionStorage.setItem('inviteData', JSON.stringify({
      authProvider: 'native',
      email: 'test@example.com',
      password: 'tempPassword123'
    }));
    
    component.ngOnInit();
    fixture.detectChanges();
    
    component.passwordForm.patchValue({
      oldPassword: 'tempPassword123',
      newPassword: 'newSecurePass123',
      confirmPassword: 'newSecurePass123'
    });
    
    const promise = component.onSubmit();
    
    const req = httpMock.expectOne('/api/accounts/reset-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      oldPassword: 'tempPassword123',
      newPassword: 'newSecurePass123'
    });
    
    req.flush({ message: 'Password updated successfully' });
    
    await promise;
    
    expect(sessionStorage.getItem('inviteData')).toBeNull();
    expect(sessionStorage.getItem('requirePasswordChange')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should handle password change error', fakeAsync(() => {
    sessionStorage.setItem('inviteData', JSON.stringify({ email: 'test@example.com' }));
    sessionStorage.setItem('requirePasswordChange', 'true');
    
    component.ngOnInit();
    fixture.detectChanges();
    
    component.passwordForm.patchValue({
      oldPassword: 'wrongOldPass',
      newPassword: 'newSecurePass123',
      confirmPassword: 'newSecurePass123'
    });
    
    component.onSubmit();
    tick();
    
    const req = httpMock.expectOne('/api/accounts/reset-password');
    req.flush({ error: 'Old password is incorrect' }, { status: 400, statusText: 'Bad Request' });
    tick();
    
    fixture.detectChanges();
    
    expect(component.errorMessage).toBe('Old password is incorrect');
    expect(sessionStorage.getItem('inviteData')).not.toBeNull();
    expect(sessionStorage.getItem('requirePasswordChange')).not.toBeNull();
  }));

  it('should not submit if form is invalid', async () => {
    sessionStorage.setItem('requirePasswordChange', 'true');
    
    component.ngOnInit();
    fixture.detectChanges();
    
    component.passwordForm.patchValue({
      oldPassword: '',
      newPassword: 'short',
      confirmPassword: 'different'
    });
    
    await component.onSubmit();
    
    httpMock.expectNone('/api/accounts/reset-password');
    expect(component.passwordForm.get('oldPassword')?.touched).toBe(true);
    expect(component.passwordForm.get('newPassword')?.touched).toBe(true);
    expect(component.passwordForm.get('confirmPassword')?.touched).toBe(true);
  });
});
