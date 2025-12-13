import { Injectable, signal, Signal } from '@angular/core';

export interface ConfirmDialogConfig {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  confirmClass?: string;
}

interface ConfirmDialogState {
  isOpen: boolean;
  config: ConfirmDialogConfig | null;
  resolve: ((value: boolean) => void) | null;
}

@Injectable({
  providedIn: 'root',
})
export class ConfirmDialogService {
  private state = signal<ConfirmDialogState>({
    isOpen: false,
    config: null,
    resolve: null,
  });

  state$: Signal<ConfirmDialogState> = this.state.asReadonly();

  confirm(config: ConfirmDialogConfig): Promise<boolean> {
    return new Promise((resolve) => {
      this.state.set({
        isOpen: true,
        config: {
          ...config,
          confirmText: config.confirmText || 'Confirm',
          cancelText: config.cancelText || 'Cancel',
          confirmClass: config.confirmClass || 'btn-danger',
        },
        resolve,
      });
    });
  }

  handleConfirm(): void {
    const currentState = this.state();
    if (currentState.resolve) {
      currentState.resolve(true);
    }
    this.close();
  }

  handleCancel(): void {
    const currentState = this.state();
    if (currentState.resolve) {
      currentState.resolve(false);
    }
    this.close();
  }

  private close(): void {
    this.state.set({
      isOpen: false,
      config: null,
      resolve: null,
    });
  }
}
