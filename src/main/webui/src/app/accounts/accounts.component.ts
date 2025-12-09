import { Component, effect, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Account, ModelService } from '../model.service';
import { Controller } from '../controller';

@Component({
  selector: 'app-accounts',
  imports: [CommonModule],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss'
})
export class AccountsComponent implements OnInit {
  private modelService = inject(ModelService);
  private controller = inject(Controller);

  accounts: Account[] = [];
  loading = true;
  error: string | null = null;

  constructor() {
    effect(() => {
      this.accounts = this.modelService.accounts$();
      if (this.accounts.length > 0 || this.error) {
        this.loading = false;
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
}
