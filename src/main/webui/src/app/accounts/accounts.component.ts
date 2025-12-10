import { Component, effect, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Account, ModelService } from '../model.service';
import { Controller } from '../controller';
import { ROLE_ADMIN } from '../auth.service';

@Component({
  selector: 'app-accounts',
  imports: [CommonModule, FormsModule],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss'
})
export class AccountsComponent implements OnInit {
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  accounts: Account[] = [];
  filteredAccounts: Account[] = [];
  loading = true;
  error: string | null = null;
  filterText = '';

  constructor() {
    effect(() => {
      this.accounts = this.modelService.accounts$();
      if (this.accounts.length > 0 || this.error) {
        this.loading = false;
      }
      this.applyFilter();
    });
  }

  ngOnInit(): void {
    // Read filter from URL query parameter (protected against XSS by Angular)
    this.route.queryParams.subscribe(params => {
      const filterParam = params['filter'];
      // Angular's queryParams automatically sanitizes the value
      // Only accept string values, reject any objects or arrays.
      // The filter implementation protects against XSS (Cross-Site Scripting) attacks by:
      // - Validating that URL filter parameters are strings only
      // - Rejecting objects, arrays, or other complex types
      // - Using Angular's built-in query parameter sanitization
      // - Never executing or evaluating filter content as code
      if (filterParam && typeof filterParam === 'string') {
        this.filterText = filterParam;
      } else {
        this.filterText = '';
      }
      this.applyFilter();
    });
    
    this.loadAccounts();
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
      account.roles && account.roles.some(role => role.role === ROLE_ADMIN)
    ).length;
  }

  applyFilter(): void {
    if (!this.filterText || this.filterText.trim() === '') {
      this.filteredAccounts = this.accounts;
      return;
    }

    const searchTerm = this.filterText.toLowerCase().trim();
    this.filteredAccounts = this.accounts.filter(account => {
      // Search in email
      if (account.email.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in name
      if (account.name.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in roles
      if (account.roles && account.roles.some(role => 
        role.role.toLowerCase().includes(searchTerm) ||
        role.clientId.toLowerCase().includes(searchTerm)
      )) {
        return true;
      }
      // Search in auth provider
      if (account.authProvider.toLowerCase().includes(searchTerm)) {
        return true;
      }
      return false;
    });
  }

  onFilterChange(): void {
    // Update URL with filter parameter
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { filter: this.filterText || null },
      queryParamsHandling: 'merge'
    });
    this.applyFilter();
  }

  clearFilter(): void {
    this.filterText = '';
    this.onFilterChange();
  }
}
