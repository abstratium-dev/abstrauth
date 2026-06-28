import { Component, computed, inject, OnInit, Signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Controller } from '../controller';
import { ModelService, Organisation } from '../model.service';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-organisation-detail',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './organisation-detail.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './organisation-detail.component.scss'
})
export class OrganisationDetailComponent implements OnInit {
  private controller = inject(Controller);
  private modelService = inject(ModelService);
  private toastService = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  organisation: Signal<Organisation | null> = this.modelService.currentOrganisation$;
  isOwner: Signal<boolean> = computed(() => this.organisation()?.roles.includes('owner') ?? false);

  orgId = '';

  editMode = false;
  editName = '';
  formSubmitting = false;
  formError: string | null = null;

  ngOnInit(): void {
    this.orgId = this.route.snapshot.paramMap.get('orgId') ?? '';
    if (this.orgId) {
      this.controller.loadOrganisation(this.orgId);
    }
  }

  startEdit(): void {
    const org = this.organisation();
    if (org) {
      this.editName = org.name;
      this.editMode = true;
      this.formError = null;
    }
  }

  cancelEdit(): void {
    this.editMode = false;
    this.editName = '';
    this.formError = null;
  }

  async onSubmitEdit(): Promise<void> {
    if (!this.editName.trim()) {
      this.formError = 'Organisation name is required.';
      return;
    }

    this.formSubmitting = true;
    this.formError = null;

    try {
      const org = await this.controller.updateOrganisationName(this.orgId, { name: this.editName.trim() });
      this.toastService.success(`Organisation renamed to "${org.name}"`);
      this.editMode = false;
    } catch (err: any) {
      if (err.status === 400) {
        if (err.error?.violations && Array.isArray(err.error.violations)) {
          this.formError = err.error.violations.map((v: any) => v.message).join('; ');
        } else {
          this.formError = 'Invalid input. Please check your entries.';
        }
      } else if (err.status === 403) {
        this.formError = 'You do not have permission to rename this organisation.';
      } else if (err.status === 404) {
        this.formError = 'Organisation not found.';
      } else {
        this.formError = 'Failed to update organisation. Please try again.';
      }
    } finally {
      this.formSubmitting = false;
    }
  }
}
