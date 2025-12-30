import { Component, inject, Signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './header/header.component';
import { ToastComponent } from './shared/toast/toast.component';
import { ConfirmDialogComponent } from './shared/confirm-dialog/confirm-dialog.component';
import { ModelService } from './model.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, HeaderComponent, ToastComponent, ConfirmDialogComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'abstrauth';
  private modelService = inject(ModelService);
  
  insecureClientSecret: Signal<boolean> = this.modelService.insecureClientSecret$;
  showSecurityWarning = true;
  
  dismissWarning() {
    this.showSecurityWarning = false;
  }
}
