import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService, ROLE_MANAGE_CLIENTS } from '../auth.service';
import { Controller } from '../controller';
import { ClientSecret, ModelService, OAuthClient } from '../model.service';
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

  // Secret management state
  viewingSecretsFor: string | null = null;
  clientSecrets: ClientSecret[] = [];
  secretsLoading = false;
  secretsError: string | null = null;
  showCreateSecretForm = false;
  createSecretData = {
    description: '',
    expiresInDays: null as number | null
  };

  // Role management state
  viewingRolesFor: string | null = null;
  serviceAccountRoles: string[] = [];
  rolesLoading = false;
  rolesError: string | null = null;
  showAddRoleForm = false;
  addRoleData = {
    role: ''
  };

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

  // Secret Management Methods

  async toggleSecretsView(client: OAuthClient): Promise<void> {
    if (this.viewingSecretsFor === client.clientId) {
      this.viewingSecretsFor = null;
      this.clientSecrets = [];
      this.showCreateSecretForm = false;
    } else {
      this.viewingSecretsFor = client.clientId;
      this.showCreateSecretForm = false;
      await this.loadClientSecrets(client.clientId);
    }
  }

  async loadClientSecrets(clientId: string): Promise<void> {
    this.secretsLoading = true;
    this.secretsError = null;
    
    try {
      this.clientSecrets = await this.controller.listClientSecrets(clientId);
    } catch (err: any) {
      console.error('Error loading secrets:', err);
      this.secretsError = 'Failed to load secrets';
      this.clientSecrets = [];
    } finally {
      this.secretsLoading = false;
    }
  }

  toggleCreateSecretForm(): void {
    this.showCreateSecretForm = !this.showCreateSecretForm;
    if (this.showCreateSecretForm) {
      this.createSecretData = {
        description: '',
        expiresInDays: null
      };
    }
  }

  async createSecret(clientId: string): Promise<void> {
    if (!this.createSecretData.description.trim()) {
      this.toastService.error('Please enter a description for the secret');
      return;
    }

    try {
      const request = {
        description: this.createSecretData.description,
        expiresInDays: this.createSecretData.expiresInDays || undefined
      };
      
      const response = await this.controller.createClientSecret(clientId, request);
      
      // Show the new secret in a dialog
      this.newClientSecret = response.secret;
      this.newClientName = `${this.clients.find(c => c.clientId === clientId)?.clientName} - New Secret`;
      this.secretCopied = false;
      
      // Reload secrets list
      await this.loadClientSecrets(clientId);
      
      // Reset form
      this.showCreateSecretForm = false;
      this.createSecretData = {
        description: '',
        expiresInDays: null
      };
    } catch (err: any) {
      console.error('Error creating secret:', err);
      if (err.status === 403) {
        this.toastService.error('You do not have permission to create secrets');
      } else {
        this.toastService.error('Failed to create secret. Please try again.');
      }
    }
  }

  async revokeSecret(clientId: string, secret: ClientSecret): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Revoke Secret',
      message: `Are you sure you want to revoke the secret "${secret.description}"? This action cannot be undone and may break applications using this secret.`,
      confirmText: 'Revoke Secret',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.revokeClientSecret(clientId, secret.id);
      this.toastService.success('Secret revoked successfully');
      await this.loadClientSecrets(clientId);
    } catch (err: any) {
      console.error('Error revoking secret:', err);
      if (err.status === 400) {
        this.toastService.error('Cannot revoke the last active secret');
      } else if (err.status === 403) {
        this.toastService.error('You do not have permission to revoke secrets');
      } else if (err.status === 404) {
        this.toastService.error('Secret not found');
      } else {
        this.toastService.error('Failed to revoke secret. Please try again.');
      }
    }
  }

  isSecretExpiringSoon(secret: ClientSecret): boolean {
    if (!secret.expiresAt) return false;
    
    const expiryDate = new Date(secret.expiresAt);
    const now = new Date();
    const daysUntilExpiry = Math.floor((expiryDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    
    return daysUntilExpiry <= 30 && daysUntilExpiry > 0;
  }

  isSecretExpired(secret: ClientSecret): boolean {
    if (!secret.expiresAt) return false;
    
    const expiryDate = new Date(secret.expiresAt);
    const now = new Date();
    
    return expiryDate < now;
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }

  async deleteSecret(clientId: string, secret: ClientSecret): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete Secret',
      message: `Are you sure you want to permanently delete the secret "${secret.description}"? This action cannot be undone.`,
      confirmText: 'Delete Permanently',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteClientSecret(clientId, secret.id);
      this.toastService.success('Secret deleted successfully');
      await this.loadClientSecrets(clientId);
    } catch (err: any) {
      console.error('Error deleting secret:', err);
      if (err.status === 400) {
        this.toastService.error('Cannot delete an active secret. Revoke it first.');
      } else if (err.status === 403) {
        this.toastService.error('You do not have permission to delete secrets');
      } else if (err.status === 404) {
        this.toastService.error('Secret not found');
      } else {
        this.toastService.error('Failed to delete secret. Please try again.');
      }
    }
  }

  // Role Management Methods

  async toggleRolesView(client: OAuthClient): Promise<void> {
    if (this.viewingRolesFor === client.clientId) {
      this.viewingRolesFor = null;
      this.serviceAccountRoles = [];
      this.showAddRoleForm = false;
    } else {
      this.viewingRolesFor = client.clientId;
      await this.loadServiceAccountRoles(client.clientId);
    }
  }

  async loadServiceAccountRoles(clientId: string): Promise<void> {
    this.rolesLoading = true;
    this.rolesError = null;
    try {
      const response = await this.controller.listServiceAccountRoles(clientId);
      this.serviceAccountRoles = response.roles;
    } catch (err: any) {
      console.error('Error loading roles:', err);
      this.rolesError = 'Failed to load roles';
    } finally {
      this.rolesLoading = false;
    }
  }

  toggleAddRoleForm(): void {
    this.showAddRoleForm = !this.showAddRoleForm;
    if (this.showAddRoleForm) {
      this.addRoleData.role = '';
    }
  }

  async addRole(clientId: string): Promise<void> {
    if (!this.addRoleData.role || !this.addRoleData.role.trim()) {
      this.toastService.error('Role name is required');
      return;
    }

    // Validate role format
    const rolePattern = /^[a-z0-9-]+$/;
    if (!rolePattern.test(this.addRoleData.role)) {
      this.toastService.error('Role must contain only lowercase letters, numbers, and hyphens');
      return;
    }

    try {
      await this.controller.addServiceAccountRole(clientId, { role: this.addRoleData.role });
      this.toastService.success(`Role "${this.addRoleData.role}" added successfully`);
      this.showAddRoleForm = false;
      this.addRoleData.role = '';
      await this.loadServiceAccountRoles(clientId);
    } catch (err: any) {
      console.error('Error adding role:', err);
      if (err.status === 400) {
        this.toastService.error('Role already exists or invalid role name');
      } else if (err.status === 403) {
        this.toastService.error('You do not have permission to add roles');
      } else if (err.status === 404) {
        this.toastService.error('Client not found');
      } else {
        this.toastService.error('Failed to add role. Please try again.');
      }
    }
  }

  async removeRole(clientId: string, role: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Remove Role',
      message: `Are you sure you want to remove the role "${role}"? This will affect JWT tokens issued for this service client.`,
      confirmText: 'Remove Role',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.removeServiceAccountRole(clientId, role);
      this.toastService.success(`Role "${role}" removed successfully`);
      await this.loadServiceAccountRoles(clientId);
    } catch (err: any) {
      console.error('Error removing role:', err);
      if (err.status === 403) {
        this.toastService.error('You do not have permission to remove roles');
      } else if (err.status === 404) {
        this.toastService.error('Role not found');
      } else {
        this.toastService.error('Failed to remove role. Please try again.');
      }
    }
  }
}
