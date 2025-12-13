import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmDialogService } from './confirm-dialog.service';

@Component({
  selector: 'ux-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss'
})
export class ConfirmDialogComponent {
  private confirmService = inject(ConfirmDialogService);
  
  state = this.confirmService.state$;

  confirm(): void {
    this.confirmService.handleConfirm();
  }

  cancel(): void {
    this.confirmService.handleCancel();
  }
}
