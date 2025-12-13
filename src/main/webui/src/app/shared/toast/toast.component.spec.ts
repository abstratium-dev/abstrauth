import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ToastComponent } from './toast.component';
import { ToastService } from './toast.service';

describe('ToastComponent', () => {
  let component: ToastComponent;
  let fixture: ComponentFixture<ToastComponent>;
  let toastService: ToastService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ToastComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ToastComponent);
    component = fixture.componentInstance;
    toastService = TestBed.inject(ToastService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display no toasts initially', () => {
    const compiled = fixture.nativeElement;
    const toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(0);
  });

  it('should display a success toast', () => {
    toastService.success('Operation successful');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const toast = compiled.querySelector('.toast-success');
    
    expect(toast).toBeTruthy();
    expect(toast.textContent).toContain('Operation successful');
  });

  it('should display an error toast', () => {
    toastService.error('Operation failed');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const toast = compiled.querySelector('.toast-error');
    
    expect(toast).toBeTruthy();
    expect(toast.textContent).toContain('Operation failed');
  });

  it('should display an info toast', () => {
    toastService.info('Information message');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const toast = compiled.querySelector('.toast-info');
    
    expect(toast).toBeTruthy();
    expect(toast.textContent).toContain('Information message');
  });

  it('should display multiple toasts', () => {
    toastService.success('First message');
    toastService.error('Second message');
    toastService.info('Third message');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const toasts = compiled.querySelectorAll('.toast');
    
    expect(toasts.length).toBe(3);
  });

  it('should show success icon for success toast', () => {
    toastService.success('Success');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const icon = compiled.querySelector('.toast-success .toast-icon');
    
    expect(icon).toBeTruthy();
    expect(icon.textContent).toContain('✓');
  });

  it('should show error icon for error toast', () => {
    toastService.error('Error');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const icon = compiled.querySelector('.toast-error .toast-icon');
    
    expect(icon).toBeTruthy();
    expect(icon.textContent).toContain('✗');
  });

  it('should show info icon for info toast', () => {
    toastService.info('Info');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const icon = compiled.querySelector('.toast-info .toast-icon');
    
    expect(icon).toBeTruthy();
    expect(icon.textContent).toContain('ℹ');
  });

  it('should have a close button', () => {
    toastService.success('Message');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const closeButton = compiled.querySelector('.toast-close');
    
    expect(closeButton).toBeTruthy();
    expect(closeButton.textContent).toContain('×');
  });

  it('should remove toast when close button is clicked', () => {
    toastService.success('Message');
    fixture.detectChanges();

    let compiled = fixture.nativeElement;
    let toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    const closeButton = compiled.querySelector('.toast-close') as HTMLButtonElement;
    closeButton.click();
    fixture.detectChanges();

    compiled = fixture.nativeElement;
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(0);
  });

  it('should auto-dismiss toast after duration', fakeAsync(() => {
    toastService.success('Message', 1000);
    fixture.detectChanges();

    let compiled = fixture.nativeElement;
    let toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    tick(1000);
    fixture.detectChanges();

    compiled = fixture.nativeElement;
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(0);
  }));

  it('should not auto-dismiss when duration is 0', fakeAsync(() => {
    toastService.show('Message', 'info', 0);
    fixture.detectChanges();

    let compiled = fixture.nativeElement;
    let toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    tick(10000);
    fixture.detectChanges();

    compiled = fixture.nativeElement;
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);
  }));

  it('should use default duration for success toast', fakeAsync(() => {
    toastService.success('Message');
    fixture.detectChanges();

    let compiled = fixture.nativeElement;
    let toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    tick(4999);
    fixture.detectChanges();
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    tick(1);
    fixture.detectChanges();
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(0);
  }));

  it('should use longer duration for error toast', fakeAsync(() => {
    toastService.error('Error message');
    fixture.detectChanges();

    let compiled = fixture.nativeElement;
    let toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    tick(6999);
    fixture.detectChanges();
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(1);

    tick(1);
    fixture.detectChanges();
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(0);
  }));

  it('should clear all toasts', () => {
    toastService.success('First');
    toastService.error('Second');
    toastService.info('Third');
    fixture.detectChanges();

    let compiled = fixture.nativeElement;
    let toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(3);

    toastService.clear();
    fixture.detectChanges();

    compiled = fixture.nativeElement;
    toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(0);
  });

  it('should handle rapid toast additions', () => {
    for (let i = 0; i < 5; i++) {
      toastService.success(`Message ${i}`);
    }
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(5);
  });

  it('should maintain toast order', () => {
    toastService.success('First');
    toastService.error('Second');
    toastService.info('Third');
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const messages = compiled.querySelectorAll('.toast-message');
    
    expect(messages[0].textContent).toContain('First');
    expect(messages[1].textContent).toContain('Second');
    expect(messages[2].textContent).toContain('Third');
  });
});
