import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with no toasts', () => {
    expect(service.toasts$().length).toBe(0);
  });

  it('should add a success toast', () => {
    service.success('Success message');
    const toasts = service.toasts$();
    
    expect(toasts.length).toBe(1);
    expect(toasts[0].message).toBe('Success message');
    expect(toasts[0].type).toBe('success');
  });

  it('should add an error toast', () => {
    service.error('Error message');
    const toasts = service.toasts$();
    
    expect(toasts.length).toBe(1);
    expect(toasts[0].message).toBe('Error message');
    expect(toasts[0].type).toBe('error');
  });

  it('should add an info toast', () => {
    service.info('Info message');
    const toasts = service.toasts$();
    
    expect(toasts.length).toBe(1);
    expect(toasts[0].message).toBe('Info message');
    expect(toasts[0].type).toBe('info');
  });

  it('should add multiple toasts', () => {
    service.success('First');
    service.error('Second');
    service.info('Third');
    
    const toasts = service.toasts$();
    expect(toasts.length).toBe(3);
  });

  it('should assign unique IDs to toasts', () => {
    service.success('First');
    service.success('Second');
    service.success('Third');
    
    const toasts = service.toasts$();
    const ids = toasts.map(t => t.id);
    const uniqueIds = new Set(ids);
    
    expect(uniqueIds.size).toBe(3);
  });

  it('should assign sequential IDs', () => {
    service.success('First');
    service.success('Second');
    
    const toasts = service.toasts$();
    expect(toasts[1].id).toBe(toasts[0].id + 1);
  });

  it('should remove toast by ID', () => {
    service.success('First');
    service.success('Second');
    
    const toasts = service.toasts$();
    const idToRemove = toasts[0].id;
    
    service.remove(idToRemove);
    
    const remainingToasts = service.toasts$();
    expect(remainingToasts.length).toBe(1);
    expect(remainingToasts[0].message).toBe('Second');
  });

  it('should clear all toasts', () => {
    service.success('First');
    service.error('Second');
    service.info('Third');
    
    expect(service.toasts$().length).toBe(3);
    
    service.clear();
    
    expect(service.toasts$().length).toBe(0);
  });

  it('should auto-remove toast after duration', fakeAsync(() => {
    service.success('Message', 1000);
    
    expect(service.toasts$().length).toBe(1);
    
    tick(999);
    expect(service.toasts$().length).toBe(1);
    
    tick(1);
    expect(service.toasts$().length).toBe(0);
  }));

  it('should not auto-remove toast when duration is 0', fakeAsync(() => {
    service.show('Message', 'info', 0);
    
    expect(service.toasts$().length).toBe(1);
    
    tick(10000);
    expect(service.toasts$().length).toBe(1);
  }));

  it('should use default duration of 5000ms for success', fakeAsync(() => {
    service.success('Message');
    
    expect(service.toasts$().length).toBe(1);
    
    tick(4999);
    expect(service.toasts$().length).toBe(1);
    
    tick(1);
    expect(service.toasts$().length).toBe(0);
  }));

  it('should use default duration of 7000ms for error', fakeAsync(() => {
    service.error('Message');
    
    expect(service.toasts$().length).toBe(1);
    
    tick(6999);
    expect(service.toasts$().length).toBe(1);
    
    tick(1);
    expect(service.toasts$().length).toBe(0);
  }));

  it('should use default duration of 5000ms for info', fakeAsync(() => {
    service.info('Message');
    
    expect(service.toasts$().length).toBe(1);
    
    tick(4999);
    expect(service.toasts$().length).toBe(1);
    
    tick(1);
    expect(service.toasts$().length).toBe(0);
  }));

  it('should accept custom duration', fakeAsync(() => {
    service.success('Message', 2000);
    
    expect(service.toasts$().length).toBe(1);
    
    tick(1999);
    expect(service.toasts$().length).toBe(1);
    
    tick(1);
    expect(service.toasts$().length).toBe(0);
  }));

  it('should handle removing non-existent toast gracefully', () => {
    service.success('Message');
    
    expect(service.toasts$().length).toBe(1);
    
    service.remove(999);
    
    expect(service.toasts$().length).toBe(1);
  });

  it('should maintain toast order', () => {
    service.success('First');
    service.error('Second');
    service.info('Third');
    
    const toasts = service.toasts$();
    expect(toasts[0].message).toBe('First');
    expect(toasts[1].message).toBe('Second');
    expect(toasts[2].message).toBe('Third');
  });

  it('should store duration in toast object', () => {
    service.success('Message', 3000);
    
    const toasts = service.toasts$();
    expect(toasts[0].duration).toBe(3000);
  });
});
