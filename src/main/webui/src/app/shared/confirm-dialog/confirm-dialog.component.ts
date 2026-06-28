import { Component, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfirmDialogService } from './confirm-dialog.service';

@Component({
  selector: 'ux-confirm-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './confirm-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './confirm-dialog.component.scss'
})
export class ConfirmDialogComponent {
  private confirmService = inject(ConfirmDialogService);

  state = this.confirmService.state$;

  isConfirmDisabled = computed(() => {
    const s = this.state();
    const required = s.config?.requiredPhrase;
    if (!required) {
      return false;
    }
    return s.typedPhrase !== required;
  });

  confirm(): void {
    if (this.isConfirmDisabled()) {
      return;
    }
    this.confirmService.handleConfirm();
  }

  cancel(): void {
    this.confirmService.handleCancel();
  }

  updateTypedPhrase(phrase: string): void {
    this.confirmService.updateTypedPhrase(phrase);
  }
}
