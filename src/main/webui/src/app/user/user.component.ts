import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal, Signal, ChangeDetectionStrategy } from '@angular/core';
import { AuthService, Token } from '../auth.service';
import { Controller } from '../controller';
import { ModelService, PersonalData, PersonalDataOrganisationMembership } from '../model.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'user',
  imports: [CommonModule],
  templateUrl: './user.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './user.component.scss',
})
export class UserComponent implements OnInit {
  private authService = inject(AuthService);
  controller = inject(Controller);
  private modelService = inject(ModelService);
  private confirmService = inject(ConfirmDialogService);
  private toastService = inject(ToastService);

  showTokenClaims = signal(false);

  personalData$: Signal<PersonalData | null>;
  personalDataLoading$: Signal<boolean>;
  personalDataError$: Signal<string | null>;

  get token(): Token {
    return this.authService.token$();
  }

  get tokenClaims(): { key: string; value: any }[] {
    return this.extractClaims(this.token);
  }

  constructor() {
    this.personalData$ = this.modelService.personalData$;
    this.personalDataLoading$ = this.modelService.personalDataLoading$;
    this.personalDataError$ = this.modelService.personalDataError$;
  }

  ngOnInit(): void {
    this.controller.loadPersonalData();
  }

  toggleTokenClaims(): void {
    this.showTokenClaims.set(!this.showTokenClaims());
  }

  async downloadMyData(): Promise<void> {
    try {
      await this.controller.exportPersonalData();
      this.toastService.success('Your data export has started.');
    } catch (error) {
      this.toastService.error('Failed to download your data. Please try again.');
    }
  }

  async deleteMyAccount(): Promise<void> {
    const email = this.token?.email as string | undefined;
    const confirmed = await this.confirmService.confirm({
      title: 'Delete My Account',
      message: 'Are you sure you want to permanently delete your account and all personal data? This action cannot be undone.',
      confirmText: 'Delete My Account',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger',
      requiredPhrase: email
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteOwnAccount();
      this.toastService.success('Your account has been deleted successfully');
      this.authService.signout();
    } catch (err: any) {
      if (err.status === 400 && err.error?.error) {
        this.toastService.error(err.error.error);
      } else if (err.status === 404) {
        this.toastService.error('Account not found.');
      } else {
        this.toastService.error('Failed to delete your account. Please try again.');
      }
    }
  }

  private extractClaims(token: Token): { key: string; value: any }[] {
    return Object.entries(token).map(([key, value]) => ({
      key,
      value: this.formatValue(value)
    }));
  }

  private formatValue(value: any): any {
    if (Array.isArray(value)) {
      return value.length > 0 ? value : '[]';
    }
    if (typeof value === 'number') {
      // Check if it's a timestamp (iat, exp)
      if (value > 1000000000000) {
        return new Date(value).toISOString();
      }
      if (value > 1000000000) {
        return new Date(value * 1000).toISOString();
      }
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    return value;
  }

  getOrgName(orgId: string, memberships: PersonalDataOrganisationMembership[]): string {
    return memberships.find(m => m.orgId === orgId)?.organisationName || orgId;
  }

  isArray(value: any): boolean {
    return Array.isArray(value);
  }

  formatTimestamp(value: string | null): string {
    if (!value) {
      return '-';
    }
    try {
      return new Date(value).toLocaleString();
    } catch {
      return value;
    }
  }
}
