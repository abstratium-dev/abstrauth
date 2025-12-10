import { Component, effect, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Account, ModelService } from '../model.service';
import { Controller } from '../controller';
import { AuthService, ROLE_ADMIN } from '../auth.service';
import { UrlFilterComponent } from '../shared/url-filter/url-filter.component';

@Component({
  selector: 'app-accounts',
  imports: [CommonModule, RouterLink, UrlFilterComponent],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss'
})
export class AccountsComponent implements OnInit {
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private authService = inject(AuthService);

  accounts: Account[] = [];
  filteredAccounts: Account[] = [];
  loading = true;
  error: string | null = null;
  private currentFilter: string = '';

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
  }

  ngOnInit(): void {
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

  onFilterChange(filterText: string): void {
    // Store the current filter so it can be reapplied when accounts change
    this.currentFilter = filterText;
    
    const searchTerm = filterText.toLowerCase().trim();
    
    if (!searchTerm) {
      this.filteredAccounts = this.accounts;
      return;
    }

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

  private applyFilter(): void {
    // Called from effect when accounts change
    this.filteredAccounts = this.accounts;
  }
}
