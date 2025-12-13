import { Component, effect, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Account, ModelService, OAuthClient } from '../model.service';
import { Controller } from '../controller';
import { AuthService, ROLE_ADMIN, ROLE_MANAGE_ACCOUNTS } from '../auth.service';
import { UrlFilterComponent } from '../shared/url-filter/url-filter.component';
import { ToastService } from '../shared/toast/toast.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-accounts',
  imports: [CommonModule, FormsModule, RouterLink, UrlFilterComponent],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss'
})
export class AccountsComponent implements OnInit {
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  accounts: Account[] = [];
  filteredAccounts: Account[] = [];
  clients: OAuthClient[] = [];
  loading = true;
  error: string | null = null;
  private currentFilter: string = '';

  // Add Account Form state
  showAddAccountForm = false;
  accountFormData = {
    email: '',
    authProvider: ''
  };
  formSubmitting = false;
  formError: string | null = null;

  // Role Form state
  addingRoleForAccountId: string | null = null;
  roleFormData = {
    clientId: '',
    role: ''
  };
  roleFormSubmitting = false;
  roleFormError: string | null = null;

  constructor() {
    effect(() => {
      this.accounts = this.modelService.accounts$();
      if (this.accounts.length > 0 || this.error) {
        this.loading = false;
      }
      // Reapply the current filter when accounts change
      if (this.currentFilter) {
        this.onFilterChange(this.currentFilter);
      } else {
        this.applyFilter();
      }
    });

    effect(() => {
      this.clients = this.modelService.clients$();
    });
  }

  ngOnInit(): void {
    this.loadAccounts();
    this.controller.loadClients();
  }

  loadAccounts(): void {
    this.loading = true;
    this.error = null;
    this.controller.loadAccounts();
    
    // Set a timeout to handle errors
    setTimeout(() => {
      if (this.loading && this.accounts.length === 0) {
        this.error = 'Failed to load accounts. Please try again.';
        this.loading = false;
      }
    }, 5000);
  }

  getProviderBadgeClass(provider: string): string {
    switch (provider) {
      case 'google':
        return 'badge-google';
      case 'native':
        return 'badge-native';
      default:
        return '';
    }
  }

  getAdminCount(): number {
    return this.accounts.filter(account => 
      account.roles && account.roles.some(role => role.clientId + "_" + role.role === ROLE_ADMIN)
    ).length;
  }

  hasAdminRole(): boolean {
    return this.authService.hasRole(ROLE_ADMIN);
  }

  isCurrentUser(accountId: string): boolean {
    const token = this.authService.token$();
    return token.sub === accountId;
  }

  onFilterChange(filter: string): void {
    this.currentFilter = filter;
    this.applyFilter();
  }

  filterByRole(roleName: string): void {
    // Update URL query parameter which will trigger the filter via url-filter component
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { filter: roleName },
      queryParamsHandling: 'merge'
    });
  }

  private applyFilter(): void {
    if (!this.currentFilter) {
      this.filteredAccounts = this.accounts;
      return;
    }

    const lowerFilter = this.currentFilter.toLowerCase();
    this.filteredAccounts = this.accounts.filter(account => {
      // Check basic account fields
      if (account.name.toLowerCase().includes(lowerFilter) ||
          account.email.toLowerCase().includes(lowerFilter) ||
          account.authProvider.toLowerCase().includes(lowerFilter)) {
        return true;
      }

      // Check roles
      if (account.roles && account.roles.length > 0) {
        return account.roles.some(role => 
          role.role.toLowerCase().includes(lowerFilter) ||
          role.clientId.toLowerCase().includes(lowerFilter)
        );
      }

      return false;
    });
  }

  hasManageAccountsRole(): boolean {
    return this.authService.hasRole(ROLE_MANAGE_ACCOUNTS);
  }

  toggleAddAccountForm(): void {
    this.showAddAccountForm = !this.showAddAccountForm;
    if (this.showAddAccountForm) {
      // Reset form when opening
      this.accountFormData = {
        email: '',
        authProvider: ''
      };
      this.formError = null;
    }
  }

  async onSubmitAddAccount(): Promise<void> {
    this.formSubmitting = true;
    this.formError = null;

    try {
      await this.controller.createAccount(
        this.accountFormData.email,
        this.accountFormData.authProvider
      );
      // Success - close form and show toast
      this.showAddAccountForm = false;
      const email = this.accountFormData.email;
      this.accountFormData = {
        email: '',
        authProvider: ''
      };
      this.toastService.success(`Account created successfully for ${email}`);
    } catch (err: any) {
      if (err.status === 400) {
        // Check for validation error structure
        if (err.error?.violations && Array.isArray(err.error.violations)) {
          const messages = err.error.violations.map((v: any) => v.message).join('; ');
          this.formError = messages;
        } else {
          this.formError = 'Invalid input. Please check your entries.';
        }
      } else if (err.status === 403) {
        this.formError = 'You do not have permission to create accounts.';
      } else if (err.status === 409) {
        this.formError = 'An account with this email already exists.';
      } else {
        this.formError = 'Failed to create account. Please try again.';
      }
    } finally {
      this.formSubmitting = false;
    }
  }

  startAddRole(accountId: string): void {
    this.addingRoleForAccountId = accountId;
    this.roleFormData = {
      clientId: '',
      role: ''
    };
    this.roleFormError = null;
  }

  cancelAddRole(): void {
    this.addingRoleForAccountId = null;
    this.roleFormData = {
      clientId: '',
      role: ''
    };
    this.roleFormError = null;
  }

  async onSubmitRole(accountId: string): Promise<void> {
    this.roleFormSubmitting = true;
    this.roleFormError = null;

    try {
      await this.controller.addAccountRole(
        accountId,
        this.roleFormData.clientId,
        this.roleFormData.role
      );
      // Success - reset form and show toast
      const role = this.roleFormData.role;
      const clientId = this.roleFormData.clientId;
      this.cancelAddRole();
      this.toastService.success(`Role "${role}" added successfully for client "${clientId}"`);
    } catch (err: any) {
      if (err.status === 400) {
        // Check for validation error structure (Hibernate Validator)
        if (err.error?.violations && Array.isArray(err.error.violations)) {
          const messages = err.error.violations.map((v: any) => v.message).join('; ');
          this.roleFormError = messages;
        } else if (err.error?.error && typeof err.error.error === 'string') {
          // Simple error message from IllegalArgumentExceptionMapper
          this.roleFormError = err.error.error;
        } else {
          this.roleFormError = 'Invalid input. Please check your entries.';
        }
      } else if (err.status === 403) {
        this.roleFormError = 'You do not have permission to add roles.';
      } else if (err.status === 404) {
        this.roleFormError = 'Account not found.';
      } else if (err.status === 409) {
        this.roleFormError = 'Role already exists.';
      } else {
        this.roleFormError = 'Failed to add role. Please try again.';
      }
    } finally {
      this.roleFormSubmitting = false;
    }
  }

  async deleteRole(accountId: string, clientId: string, role: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete Role',
      message: `Are you sure you want to delete the role "${role}"? This action cannot be undone.`,
      confirmText: 'Delete Role',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.removeAccountRole(accountId, clientId, role);
    } catch (err: any) {
      if (err.status === 403) {
        alert('You do not have permission to remove roles.');
      } else if (err.status === 404) {
        alert('Account or role not found.');
      } else {
        alert('Failed to remove role. Please try again.');
      }
    }
  }

  async deleteAccount(account: Account): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete Account',
      message: `Are you sure you want to delete the account for "${account.email}"? This action cannot be undone and will remove all associated data including roles, credentials, and authorization codes.`,
      confirmText: 'Delete Account',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteAccount(account.id);
      this.toastService.success(`Account for ${account.email} deleted successfully`);
    } catch (err: any) {
      if (err.status === 403) {
        this.toastService.error('You do not have permission to delete accounts.');
      } else if (err.status === 404) {
        this.toastService.error('Account not found.');
      } else {
        this.toastService.error('Failed to delete account. Please try again.');
      }
    }
  }
}
