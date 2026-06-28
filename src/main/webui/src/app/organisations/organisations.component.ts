import { Component, effect, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Controller } from '../controller';
import { ModelService, Organisation } from '../model.service';
import { AuthService } from '../auth.service';
import { ToastService } from '../shared/toast/toast.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-organisations',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './organisations.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './organisations.component.scss'
})
export class OrganisationsComponent implements OnInit {
  private controller = inject(Controller);
  private modelService = inject(ModelService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  organisations: Organisation[] = [];
  loading = true;
  error: string | null = null;

  showCreateForm = false;
  newOrgName = '';
  formSubmitting = false;
  formError: string | null = null;
  deletingOrgId: string | null = null;
  deleteError: string | null = null;

  constructor() {
    effect(() => {
      this.organisations = this.modelService.organisations$();
    });

    effect(() => {
      this.loading = this.modelService.organisationsLoading$();
    });

    effect(() => {
      this.error = this.modelService.organisationsError$();
    });
  }

  ngOnInit(): void {
    this.controller.loadOrganisations();
  }

  getCurrentOrgId(): string | undefined {
    return this.authService.getOrgId();
  }

  isCurrentOrg(orgId: string): boolean {
    return this.authService.getOrgId() === orgId;
  }

  isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  async onDeleteOrg(orgId: string, orgName: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete Organisation',
      message: `Delete organisation "${orgName}" including all user memberships related to it? This cannot be undone.`,
      confirmText: 'Delete Organisation',
      confirmClass: 'btn-danger'
    });
    if (!confirmed) {
      return;
    }
    this.deletingOrgId = orgId;
    this.deleteError = null;
    try {
      await this.controller.deleteOrganisation(orgId);
      this.toastService.success(`Organisation "${orgName}" deleted`);
    } catch (err: any) {
      this.deleteError = err?.error?.error || 'Failed to delete organisation.';
    } finally {
      this.deletingOrgId = null;
    }
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
    if (!this.showCreateForm) {
      this.newOrgName = '';
      this.formError = null;
    }
  }

  async onSubmitCreate(): Promise<void> {
    if (!this.newOrgName.trim()) {
      this.formError = 'Organisation name is required.';
      return;
    }

    this.formSubmitting = true;
    this.formError = null;

    try {
      const org = await this.controller.createOrganisation({ name: this.newOrgName.trim() });
      this.toastService.success(`Organisation "${org.name}" created successfully`);
      this.showCreateForm = false;
      this.newOrgName = '';
    } catch (err: any) {
      if (err.status === 400) {
        if (err.error?.violations && Array.isArray(err.error.violations)) {
          this.formError = err.error.violations.map((v: any) => v.message).join('; ');
        } else {
          this.formError = 'Invalid input. Please check your entries.';
        }
      } else if (err.status === 403) {
        this.formError = 'You do not have permission to create organisations.';
      } else {
        this.formError = 'Failed to create organisation. Please try again.';
      }
    } finally {
      this.formSubmitting = false;
    }
  }

  getRoleBadgeClass(role: string): string {
    switch (role) {
      case 'owner': return 'badge-primary';
      case 'member': return 'badge-native';
      default: return '';
    }
  }

  retry(): void {
    this.controller.loadOrganisations();
  }
}
