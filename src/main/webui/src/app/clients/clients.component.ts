import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService, ROLE_MANAGE_CLIENTS } from '../auth.service';
import { Controller } from '../controller';
import { ModelService, OAuthClient } from '../model.service';
import { UrlFilterComponent } from '../shared/url-filter/url-filter.component';
import { ToastService } from '../shared/toast/toast.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'clients',
  imports: [CommonModule, RouterLink, UrlFilterComponent, FormsModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  private controller = inject(Controller);
  private modelService = inject(ModelService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);
  
  clients: OAuthClient[] = [];
  filteredClients: OAuthClient[] = [];
  loading = true;
  error: string | null = null;
  
  // Form state
  showForm = false;
  editingClientId: string | null = null;
  formData = {
    clientId: '',
    clientName: '',
    clientType: 'confidential',
    redirectUris: '',
    allowedScopes: '',
    requirePkce: true
  };
  formError: string | null = null;
  formSubmitting = false;

  // Client secret display state
  newClientSecret: string | null = null;
  newClientName: string | null = null;
  secretCopied = false;

  constructor() {
    effect(() => {
      this.clients = this.modelService.clients$();
      this.loading = this.modelService.clientsLoading$();
      this.error = this.modelService.clientsError$();
      this.applyFilter();
    });
  }

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.controller.loadClients();
  }

  parseJsonArray(jsonString: string): string[] {
    try {
      return JSON.parse(jsonString);
    } catch (e) {
      return [];
    }
  }

  onFilterChange(filterText: string): void {
    const searchTerm = filterText.toLowerCase().trim();
    
    if (!searchTerm) {
      this.filteredClients = this.clients;
      return;
    }

    this.filteredClients = this.clients.filter(client => {
      // Search in client name
      if (client.clientName.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in client ID
      if (client.clientId.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in client type
      if (client.clientType.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in redirect URIs
      const redirectUris = this.parseJsonArray(client.redirectUris);
      if (redirectUris.some(uri => uri.toLowerCase().includes(searchTerm))) {
        return true;
      }
      // Search in allowed scopes
      const scopes = this.parseJsonArray(client.allowedScopes);
      if (scopes.some(scope => scope.toLowerCase().includes(searchTerm))) {
        return true;
      }
      return false;
    });
  }

  private applyFilter(): void {
    // Called from effect when clients change
    this.filteredClients = this.clients;
  }

  hasManageClientsRole(): boolean {
    return this.authService.hasRole(ROLE_MANAGE_CLIENTS);
  }

  toggleForm(): void {
    this.showForm = !this.showForm;
    if (this.showForm) {
      this.resetForm();
    }
  }

  startEdit(client: OAuthClient): void {
    this.editingClientId = client.id;
    this.showForm = false;
    this.formData = {
      clientId: client.clientId,
      clientName: client.clientName,
      clientType: client.clientType,
      redirectUris: this.parseJsonArray(client.redirectUris).join('\n'),
      allowedScopes: this.parseJsonArray(client.allowedScopes).join(' '),
      requirePkce: client.requirePkce
    };
    this.formError = null;
  }

  cancelEdit(): void {
    this.editingClientId = null;
    this.resetForm();
  }

  async deleteClient(client: OAuthClient): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete Client',
      message: `Are you sure you want to delete the client "${client.clientName}"? This action cannot be undone.`,
      confirmText: 'Delete Client',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteClient(client.id);
      // If we were editing this client, cancel edit mode
      if (this.editingClientId === client.id) {
        this.cancelEdit();
      }
    } catch (err: any) {
      if (err.status === 404) {
        this.toastService.error('Client not found. It may have already been deleted.');
      } else if (err.status === 403) {
        this.toastService.error('You do not have permission to delete clients.');
      } else if (err.status === 400) {
        this.toastService.error(err.error?.error);
      } else {
        this.toastService.error('Failed to delete client. Please try again.');
      }
    }
  }

  resetForm(): void {
    this.editingClientId = null;
    this.formData = {
      clientId: '',
      clientName: '',
      clientType: 'confidential',
      redirectUris: '',
      allowedScopes: '',
      requirePkce: true
    };
    this.formError = null;
  }

  copySecret(): void {
    if (this.newClientSecret) {
      navigator.clipboard.writeText(this.newClientSecret).then(() => {
        this.secretCopied = true;
        this.toastService.success('Client secret copied to clipboard');
      }).catch(err => {
        console.error('Failed to copy secret:', err);
        this.toastService.error('Failed to copy secret to clipboard');
      });
    }
  }

  closeSecretDialog(): void {
    this.newClientSecret = null;
    this.newClientName = null;
    this.secretCopied = false;
  }

  async onSubmit(): Promise<void> {
    this.formError = null;
    this.formSubmitting = true;

    try {
      // Convert redirect URIs and scopes to JSON arrays
      const redirectUrisArray = this.formData.redirectUris
        .split('\n')
        .map(uri => uri.trim())
        .filter(uri => uri.length > 0);
      
      const allowedScopesArray = this.formData.allowedScopes
        .split(/[,\s]+/)
        .map(scope => scope.trim())
        .filter(scope => scope.length > 0);

      if (redirectUrisArray.length === 0) {
        this.formError = 'At least one redirect URI is required';
        this.formSubmitting = false;
        return;
      }

      if (allowedScopesArray.length === 0) {
        this.formError = 'At least one scope is required';
        this.formSubmitting = false;
        return;
      }

      const clientData = {
        clientId: this.formData.clientId,
        clientName: this.formData.clientName,
        clientType: this.formData.clientType,
        redirectUris: JSON.stringify(redirectUrisArray),
        allowedScopes: JSON.stringify(allowedScopesArray),
        requirePkce: this.formData.requirePkce
      };

      if (this.editingClientId) {
        // Update existing client
        const updateData = {
          clientName: this.formData.clientName,
          clientType: this.formData.clientType,
          redirectUris: JSON.stringify(redirectUrisArray),
          allowedScopes: JSON.stringify(allowedScopesArray),
          requirePkce: this.formData.requirePkce
        };
        await this.controller.updateClient(this.editingClientId, updateData);
        const clientName = this.formData.clientName;
        this.cancelEdit();
        this.toastService.success(`Client "${clientName}" updated successfully`);
      } else {
        // Create new client
        const response = await this.controller.createClient(clientData);
        const clientName = this.formData.clientName;
        this.showForm = false;
        this.resetForm();
        
        // Show the client secret if present
        if (response.clientSecret) {
          this.newClientSecret = response.clientSecret;
          this.newClientName = clientName;
          this.secretCopied = false;
        } else {
          this.toastService.success(`Client "${clientName}" created successfully`);
        }
      }
    } catch (err: any) {
      if (err.status === 400) {
        // Check for validation error structure
        if (err.error?.violations && Array.isArray(err.error.violations)) {
          const messages = err.error.violations.map((v: any) => v.message).join('; ');
          this.formError = messages;
        } else if (err.error && err.error.error) {
          this.formError = err.error.error;
        } else {
          this.formError = 'Invalid input. Please check your entries.';
        }
      } else if (err.status === 409) {
        this.formError = 'Client ID already exists';
      } else if (err.status === 403) {
        this.formError = 'You do not have permission to create clients';
      } else {
        this.formError = 'Failed to create client. Please try again.';
      }
    } finally {
      this.formSubmitting = false;
    }
  }
}
