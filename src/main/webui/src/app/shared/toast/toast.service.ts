import { Injectable, signal, Signal } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
  duration: number;
}

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private toasts = signal<Toast[]>([]);
  private nextId = 0;

  toasts$: Signal<Toast[]> = this.toasts.asReadonly();

  show(message: string, type: 'success' | 'error' | 'info' = 'info', duration: number = 5000): void {
    const id = this.nextId++;
    const toast: Toast = { id, message, type, duration };
    
    this.toasts.update(toasts => [...toasts, toast]);

    if (duration > 0) {
      setTimeout(() => {
        this.remove(id);
      }, duration);
    }
  }

  success(message: string, duration: number = 5000): void {
    this.show(message, 'success', duration);
  }

  error(message: string, duration: number = 7000): void {
    this.show(message, 'error', duration);
  }

  info(message: string, duration: number = 5000): void {
    this.show(message, 'info', duration);
  }

  remove(id: number): void {
    this.toasts.update(toasts => toasts.filter(t => t.id !== id));
  }

  clear(): void {
    this.toasts.set([]);
  }
}
