import { TestBed } from '@angular/core/testing';
import { ConfirmDialogService } from './confirm-dialog.service';

describe('ConfirmDialogService', () => {
  let service: ConfirmDialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ConfirmDialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with dialog closed', () => {
    expect(service.state$().isOpen).toBe(false);
  });

  it('should open dialog with config', () => {
    service.confirm({
      title: 'Test Title',
      message: 'Test Message'
    });

    const state = service.state$();
    expect(state.isOpen).toBe(true);
    expect(state.config?.title).toBe('Test Title');
    expect(state.config?.message).toBe('Test Message');
  });

  it('should use default button texts', () => {
    service.confirm({
      title: 'Test',
      message: 'Test'
    });

    const state = service.state$();
    expect(state.config?.confirmText).toBe('Confirm');
    expect(state.config?.cancelText).toBe('Cancel');
  });

  it('should use custom button texts', () => {
    service.confirm({
      title: 'Test',
      message: 'Test',
      confirmText: 'Delete',
      cancelText: 'Go Back'
    });

    const state = service.state$();
    expect(state.config?.confirmText).toBe('Delete');
    expect(state.config?.cancelText).toBe('Go Back');
  });

  it('should resolve true when confirmed', async () => {
    const promise = service.confirm({
      title: 'Test',
      message: 'Test'
    });

    service.handleConfirm();

    const result = await promise;
    expect(result).toBe(true);
    expect(service.state$().isOpen).toBe(false);
  });

  it('should resolve false when cancelled', async () => {
    const promise = service.confirm({
      title: 'Test',
      message: 'Test'
    });

    service.handleCancel();

    const result = await promise;
    expect(result).toBe(false);
    expect(service.state$().isOpen).toBe(false);
  });

  it('should expose requiredPhrase in config', () => {
    service.confirm({
      title: 'Test',
      message: 'Test',
      requiredPhrase: 'delete-me'
    });

    expect(service.state$().config?.requiredPhrase).toBe('delete-me');
    expect(service.state$().typedPhrase).toBe('');
  });

  it('should update typedPhrase via updateTypedPhrase', () => {
    service.confirm({ title: 'Test', message: 'Test', requiredPhrase: 'delete-me' });

    service.updateTypedPhrase('delete');
    expect(service.state$().typedPhrase).toBe('delete');

    service.updateTypedPhrase('delete-me');
    expect(service.state$().typedPhrase).toBe('delete-me');
  });

  it('should reset typedPhrase on close', async () => {
    const promise = service.confirm({ title: 'Test', message: 'Test', requiredPhrase: 'delete-me' });
    service.updateTypedPhrase('delete-me');
    service.handleConfirm();
    await promise;

    // re-open
    service.confirm({ title: 'Test', message: 'Test', requiredPhrase: 'other' });
    expect(service.state$().typedPhrase).toBe('');
  });
});
