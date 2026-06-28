import { Component, inject, Signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './header/header.component';
import { ToastComponent } from './shared/toast/toast.component';
import { ConfirmDialogComponent } from './shared/confirm-dialog/confirm-dialog.component';
import { ModelService } from './model.service';
import { DomainService } from './domain.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, HeaderComponent, ToastComponent, ConfirmDialogComponent, RouterModule],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'abstrauth';
  private modelService = inject(ModelService);
  copyrightYear: any;

  insecureClientSecret: Signal<boolean> = this.modelService.insecureClientSecret$;
  warningMessage: Signal<string> = this.modelService.warningMessage$;
  showSecurityWarning = true;
  isCorrectDomain: boolean;
  legalContent$: Signal<string | null>;

  constructor() {
    this.copyrightYear = new Date().getFullYear();
    if(this.copyrightYear > 2026) {
      this.copyrightYear = '2026 - ' + this.copyrightYear;
    }

    this.isCorrectDomain = inject(DomainService).isAbstratiumDomain;
    this.legalContent$ = inject(ModelService).legalContent$;
  }
  
  dismissWarning() {
    this.showSecurityWarning = false;
  }
}
