import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService, ROLE_MANAGE_CLIENTS } from '../auth.service';
import { Controller } from '../controller';
import { AllowedRole, ClientSecret, ModelService, OAuthClient } from '../model.service';
import { UrlFilterComponent } from '../shared/url-filter/url-filter.component';
import { ToastService } from '../shared/toast/toast.service';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'clients',
  imports: [CommonModule, RouterLink, UrlFilterComponent, FormsModule],
  templateUrl: './clients.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  private controller = inject(Controller);
  private modelService = inject(ModelService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);
  private route = inject(ActivatedRoute);
  
  clients: OAuthClient[] = [];
  filteredClients: OAuthClient[] = [];
  loading = true;
  error: string | null = null;
  
  // Deep-link state for opening allowed roles from another page
  private viewAllowedRolesClientId: string | null = null;

  // Deep-link state for opening secrets and highlighting a specific secret
  private viewSecretsClientId: string | null = null;
  highlightedSecretId: number | null = null;

  // Form state
  showForm = false;
  editingClientId: string | null = null;
  formData = {
    clientId: '',
    clientName: '',
    clientType: 'confidential',
    redirectUris: '',
    allowedScopes: '',
    requirePkce: true,
    autoSubscribe: false,
    publik: false
  };
  formError: string | null = null;
  formSubmitting = false;

  // Client secret display state
  newClientSecret: string | null = null;
  newClientId: string | null = null;
  newClientName: string | null = null;
  clientIdCopied = false;
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

  // Client-to-Client (M2M) Role management state
  viewingClientRolesFor: string | null = null;
  clientRoles: { targetClientId: string; role: string; createdAt: string }[] = [];
  clientRolesLoading = false;
  clientRolesError: string | null = null;
  showAddClientRoleForm = false;
  addClientRoleData = {
    targetClientId: '',
    role: ''
  };
  // Available clients for target selection (subscribed clients)
  availableTargetClients: OAuthClient[] = [];
  loadingTargetClients = false;
  // Allowed roles for selected target client
  availableClientRoleRoles: AllowedRole[] = [];
  loadingClientRoleRoles = false;

  // Allowed roles management state
  viewingAllowedRolesFor: string | null = null;
  allowedRoles: AllowedRole[] = [];
  allowedRolesLoading = false;
  allowedRolesError: string | null = null;
  showAddAllowedRoleForm = false;
  addAllowedRoleData = {
    role: '',
    isDefault: false,
    availableToForeignOrgs: false
  };
  editingAllowedRole: string | null = null;
  editAllowedRoleData = {
    isDefault: false,
    availableToForeignOrgs: false
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
    this.route.queryParams.subscribe(params => {
      this.viewAllowedRolesClientId = params['viewAllowedRoles'] || null;
      this.viewSecretsClientId = params['viewSecrets'] || null;
      this.highlightedSecretId = params['highlightSecret'] ? Number(params['highlightSecret']) : null;
    });
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
    } else {
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
    this.checkViewAllowedRoles();
  }

  private checkViewAllowedRoles(): void {
    if (!this.viewAllowedRolesClientId) {
      return;
    }
    const client = this.filteredClients.find(c => c.clientId === this.viewAllowedRolesClientId);
    if (!client) {
      return;
    }
    const clientId = this.viewAllowedRolesClientId;
    this.viewAllowedRolesClientId = null;
    setTimeout(async () => {
      await this.toggleAllowedRolesView(client);
      setTimeout(() => {
        const card = document.querySelector(`[data-client-id="${clientId}"]`);
        if (card) {
          card.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 100);
    }, 0);
  }

  private checkViewSecrets(): void {
    if (!this.viewSecretsClientId) {
      return;
    }
    const client = this.filteredClients.find(c => c.clientId === this.viewSecretsClientId);
    if (!client) {
      return;
    }
    const clientId = this.viewSecretsClientId;
    const highlightedSecretId = this.highlightedSecretId;
    this.viewSecretsClientId = null;
    this.highlightedSecretId = null;
    setTimeout(async () => {
      await this.toggleSecretsView(client);
      if (highlightedSecretId) {
        setTimeout(() => {
          const secretCard = document.querySelector(`[data-secret-id="${highlightedSecretId}"]`);
          if (secretCard) {
            secretCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 100);
      } else {
        setTimeout(() => {
          const card = document.querySelector(`[data-client-id="${clientId}"]`);
          if (card) {
            card.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        }, 100);
      }
    }, 0);
  }

  private applyFilter(): void {
    // Called from effect when clients change
    this.filteredClients = this.clients;
    this.checkViewAllowedRoles();
    this.checkViewSecrets();
  }

  hasManageClientsRole(): boolean {
    return this.authService.hasRole(ROLE_MANAGE_CLIENTS);
  }

  matchesTokenOrgId(orgId: string): boolean {
    return this.authService.token$().orgId === orgId;
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
      requirePkce: client.requirePkce,
      autoSubscribe: client.autoSubscribe,
      publik: client.publik
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
      await this.controller.deleteClient(client.clientId);
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

  onPublikChange(): void {
    if (!this.formData.publik) {
      this.formData.autoSubscribe = false;
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
      requirePkce: true,
      autoSubscribe: false,
      publik: false
    };
    this.formError = null;
  }

  copyClientId(): void {
    if (this.newClientId) {
      navigator.clipboard.writeText(this.newClientId).then(() => {
        this.clientIdCopied = true;
        this.toastService.success('Client ID copied to clipboard');
      }).catch(err => {
        console.error('Failed to copy client ID:', err);
        this.toastService.error('Failed to copy client ID to clipboard');
      });
    }
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
    this.newClientId = null;
    this.newClientName = null;
    this.clientIdCopied = false;
    this.secretCopied = false;
  }

  async onSubmit(): Promise<void> {
    this.formError = null;
    this.formSubmitting = true;

    // Validate client ID format: only letters, numbers, and underscores allowed
    const clientIdPattern = /^[a-zA-Z0-9_]+$/;
    if (!this.editingClientId && !clientIdPattern.test(this.formData.clientId)) {
      this.formError = 'Client ID must contain only letters, numbers, and underscores';
      this.formSubmitting = false;
      return;
    }

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

      // Validation: redirect URIs and scopes must both be present or both be absent
      // - If scopes are set, redirect URIs are required (authorization code flow)
      // - If redirect URIs are set, scopes are required
      // - Both can be empty for service/M2M clients (role-based authorization)
      const hasRedirectUris = redirectUrisArray.length > 0;
      const hasScopes = allowedScopesArray.length > 0;

      if (hasScopes && !hasRedirectUris) {
        this.formError = 'Redirect URIs are required when scopes are configured';
        this.formSubmitting = false;
        return;
      }

      if (hasRedirectUris && !hasScopes) {
        this.formError = 'Scopes are required when redirect URIs are configured';
        this.formSubmitting = false;
        return;
      }

      const clientData = {
        clientId: this.formData.clientId,
        clientName: this.formData.clientName,
        clientType: this.formData.clientType,
        redirectUris: JSON.stringify(redirectUrisArray),
        allowedScopes: JSON.stringify(allowedScopesArray),
        requirePkce: this.formData.requirePkce,
        autoSubscribe: this.formData.autoSubscribe,
        publik: this.formData.publik
      };

      if (this.editingClientId) {
        // Update existing client
        const updateData = {
          clientName: this.formData.clientName,
          clientType: this.formData.clientType,
          redirectUris: JSON.stringify(redirectUrisArray),
          allowedScopes: JSON.stringify(allowedScopesArray),
          requirePkce: this.formData.requirePkce,
          publik: this.formData.publik,
          autoSubscribe: this.formData.autoSubscribe
        };
        await this.controller.updateClient(this.editingClientId, updateData);
        const clientName = this.formData.clientName;
        this.cancelEdit();
        this.toastService.success(`Client "${clientName}" updated successfully`);
      } else {
        // Create new client
        const response = await this.controller.createClient(clientData);
        const clientName = response.clientName;
        const clientId = response.clientId;
        this.showForm = false;
        this.resetForm();

        // Show the client secret if present
        if (response.clientSecret) {
          this.newClientSecret = response.clientSecret;
          this.newClientId = clientId;
          this.newClientName = clientName;
          this.clientIdCopied = false;
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
      this.newClientId = `${this.clients.find(c => c.clientId === clientId)?.clientId}`;
      this.newClientName = `${this.clients.find(c => c.clientId === clientId)?.clientName} - New Secret`;
      this.clientIdCopied = false;
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
    
    return daysUntilExpiry <= 30 && daysUntilExpiry >= 0;
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

  canManageRoles(client: OAuthClient): boolean {
    // Roles can only be managed if no scopes are configured
    const scopes = this.parseJsonArray(client.allowedScopes);
    return scopes.length === 0;
  }

  /**
   * Strips a UUID + '__' prefix from a client ID when constructing the group name.
   * Matches the backend ClientIdUtil.stripOrgPrefix logic.
   */
  groupNameFor(clientId: string, role: string): string {
    const stripped = this.stripOrgPrefix(clientId);
    return stripped + '_' + role;
  }

  private stripOrgPrefix(clientId: string): string {
    if (!clientId || clientId.length <= 38) {
      return clientId;
    }
    const prefix = clientId.substring(0, 38);
    const uuidPart = prefix.substring(0, 36);
    if (!prefix.endsWith('__') || !this.isValidUuid(uuidPart)) {
      return clientId;
    }
    return clientId.substring(38);
  }

  private isValidUuid(s: string): boolean {
    if (s.length !== 36) {
      return false;
    }
    const pattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
    return pattern.test(s);
  }

  // Client-to-Client (M2M) Role Management Methods

  async toggleClientRolesView(client: OAuthClient): Promise<void> {
    if (this.viewingClientRolesFor === client.clientId) {
      this.viewingClientRolesFor = null;
      this.clientRoles = [];
      this.showAddClientRoleForm = false;
    } else {
      this.viewingClientRolesFor = client.clientId;
      await this.loadClientRoles(client.clientId);
    }
  }

  async loadClientRoles(srcClientId: string): Promise<void> {
    this.clientRolesLoading = true;
    this.clientRolesError = null;
    try {
      const response = await this.controller.listClientRoles(srcClientId);
      this.clientRoles = response.roles;
    } catch (err: any) {
      console.error('Error loading client roles:', err);
      this.clientRolesError = 'Failed to load client roles';
      this.clientRoles = [];
    } finally {
      this.clientRolesLoading = false;
    }
  }

  async toggleAddClientRoleForm(): Promise<void> {
    this.showAddClientRoleForm = !this.showAddClientRoleForm;
    if (this.showAddClientRoleForm) {
      this.addClientRoleData = { targetClientId: '', role: '' };
      this.availableClientRoleRoles = [];
      await this.loadAvailableTargetClients();
    }
  }

  loadAvailableTargetClients(): void {
    // Use the already loaded clients from the model service
    // Filter out the current source client if viewing roles for a specific client
    this.availableTargetClients = this.clients.filter(
      c => c.clientId !== this.viewingClientRolesFor
    );
  }

  async onTargetClientSelected(targetClientId: string): Promise<void> {
    this.addClientRoleData.role = ''; // Reset role when target changes
    this.availableClientRoleRoles = [];

    if (!targetClientId) {
      return;
    }

    this.loadingClientRoleRoles = true;
    try {
      // Load allowed roles for the selected target client
      // This returns roles that can be assigned to users in the current org
      this.availableClientRoleRoles = await this.controller.listAllowedRoles(targetClientId);
    } catch (err: any) {
      console.error('Error loading allowed roles for target client:', err);
      this.toastService.error('Failed to load available roles for selected client');
      this.availableClientRoleRoles = [];
    } finally {
      this.loadingClientRoleRoles = false;
    }
  }

  async addClientRole(srcClientId: string): Promise<void> {
    if (!this.addClientRoleData.targetClientId || !this.addClientRoleData.targetClientId.trim()) {
      this.toastService.error('Target client is required');
      return;
    }

    if (!this.addClientRoleData.role || !this.addClientRoleData.role.trim()) {
      this.toastService.error('Role is required');
      return;
    }

    try {
      await this.controller.addClientRole(srcClientId, {
        targetClientId: this.addClientRoleData.targetClientId,
        role: this.addClientRoleData.role
      });
      this.toastService.success(`Role "${this.addClientRoleData.role}" assigned for target client`);
      this.showAddClientRoleForm = false;
      this.addClientRoleData = { targetClientId: '', role: '' };
      this.availableClientRoleRoles = [];
      await this.loadClientRoles(srcClientId);
    } catch (err: any) {
      console.error('Error adding client role:', err);
      if (err.status === 400) {
        this.toastService.error(err.error?.error || 'Role is not in the target client\'s allowed roles catalog');
      } else if (err.status === 409) {
        this.toastService.error('Role already assigned for this target client');
      } else if (err.status === 403) {
        this.toastService.error('You do not have permission to assign client roles');
      } else if (err.status === 404) {
        this.toastService.error('Source or target client not found');
      } else {
        this.toastService.error('Failed to assign client role. Please try again.');
      }
    }
  }

  async removeClientRole(srcClientId: string, targetClientId: string, role: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Remove Client Role',
      message: `Are you sure you want to remove the role "${role}" for target client "${targetClientId}"?`,
      confirmText: 'Remove Role',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.removeClientRole(srcClientId, targetClientId, role);
      this.toastService.success(`Role "${role}" removed successfully`);
      await this.loadClientRoles(srcClientId);
    } catch (err: any) {
      console.error('Error removing client role:', err);
      if (err.status === 403) {
        this.toastService.error('You do not have permission to remove client roles');
      } else if (err.status === 404) {
        this.toastService.error('Role assignment not found');
      } else {
        this.toastService.error('Failed to remove client role. Please try again.');
      }
    }
  }

  // Allowed Roles Management Methods

  async toggleAllowedRolesView(client: OAuthClient): Promise<void> {
    if (this.viewingAllowedRolesFor === client.clientId) {
      this.viewingAllowedRolesFor = null;
      this.allowedRoles = [];
      this.showAddAllowedRoleForm = false;
      this.editingAllowedRole = null;
    } else {
      this.viewingAllowedRolesFor = client.clientId;
      this.showAddAllowedRoleForm = false;
      this.editingAllowedRole = null;
      await this.loadAllowedRoles(client.clientId);
    }
  }

  async loadAllowedRoles(clientId: string): Promise<void> {
    this.allowedRolesLoading = true;
    this.allowedRolesError = null;
    try {
      this.allowedRoles = await this.controller.listAllowedRolesForUsersInClientsOrg(clientId);
    } catch (err: any) {
      console.error('Error loading allowed roles:', err);
      this.allowedRolesError = 'Failed to load allowed roles';
      this.allowedRoles = [];
    } finally {
      this.allowedRolesLoading = false;
    }
  }

  toggleAddAllowedRoleForm(): void {
    this.showAddAllowedRoleForm = !this.showAddAllowedRoleForm;
    if (this.showAddAllowedRoleForm) {
      this.addAllowedRoleData = { role: '', isDefault: false, availableToForeignOrgs: false };
      this.editingAllowedRole = null;
    }
  }

  async addAllowedRole(clientId: string): Promise<void> {
    if (!this.addAllowedRoleData.role || !this.addAllowedRoleData.role.trim()) {
      this.toastService.error('Role name is required');
      return;
    }

    const rolePattern = /^[a-z0-9-]+$/;
    if (!rolePattern.test(this.addAllowedRoleData.role)) {
      this.toastService.error('Role must contain only lowercase letters, numbers, and hyphens');
      return;
    }

    try {
      await this.controller.addAllowedRole(clientId, {
        role: this.addAllowedRoleData.role,
        isDefault: this.addAllowedRoleData.isDefault,
        availableToForeignOrgs: this.addAllowedRoleData.availableToForeignOrgs
      });
      this.toastService.success(`Role "${this.addAllowedRoleData.role}" added to allowlist`);
      this.showAddAllowedRoleForm = false;
      this.addAllowedRoleData = { role: '', isDefault: false, availableToForeignOrgs: false };
      await this.loadAllowedRoles(clientId);
    } catch (err: any) {
      console.error('Error adding allowed role:', err);
      if (err.status === 409) {
        this.toastService.error('Role already exists in allowlist');
      } else if (err.status === 403) {
        this.toastService.error('You do not have permission to manage allowed roles');
      } else if (err.status === 404) {
        this.toastService.error('Client not found');
      } else {
        this.toastService.error('Failed to add allowed role. Please try again.');
      }
    }
  }

  startEditAllowedRole(role: string, isDefault: boolean, availableToForeignOrgs: boolean): void {
    this.editingAllowedRole = role;
    this.editAllowedRoleData = { isDefault, availableToForeignOrgs };
    this.showAddAllowedRoleForm = false;
  }

  cancelEditAllowedRole(): void {
    this.editingAllowedRole = null;
    this.editAllowedRoleData = { isDefault: false, availableToForeignOrgs: false };
  }

  async updateAllowedRole(clientId: string, role: string, wasAvailableToForeignOrgs: boolean, wasIsDefault: boolean): Promise<void> {
    const isRetracting = wasAvailableToForeignOrgs && !this.editAllowedRoleData.availableToForeignOrgs;
    if (isRetracting) {
      const confirmed = await this.confirmService.confirm({
        title: 'Retract Role from Foreign Organisations',
        message: `Marking "${role}" as unavailable to foreign organisations will remove it from ALL users and ALL clients outside your organisation. This action cannot be undone.`,
        confirmText: 'Retract Role',
        cancelText: 'Cancel',
        confirmClass: 'btn-danger'
      });
      if (!confirmed) {
        return;
      }
    }

    const isRemovingDefault = wasIsDefault && !this.editAllowedRoleData.isDefault;
    if (isRemovingDefault) {
      this.toastService.info(`Role "${role}" is no longer default. Existing users and clients with this role will keep it. To remove the role from existing assignments, delete and recreate the role.`, 90000);
    }

    try {
      await this.controller.updateAllowedRole(clientId, role, {
        isDefault: this.editAllowedRoleData.isDefault,
        availableToForeignOrgs: this.editAllowedRoleData.availableToForeignOrgs
      });
      this.toastService.success(`Role "${role}" updated successfully`);
      this.editingAllowedRole = null;
      await this.loadAllowedRoles(clientId);
    } catch (err: any) {
      console.error('Error updating allowed role:', err);
      if (err.status === 403) {
        this.toastService.error('You do not have permission to manage allowed roles');
      } else if (err.status === 404) {
        this.toastService.error('Role or client not found');
      } else {
        this.toastService.error('Failed to update allowed role. Please try again.');
      }
    }
  }

  async removeAllowedRole(clientId: string, role: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Remove Role from Catalog',
      message: `Removing "${role}" will delete it from ALL users and ALL clients that use it, across ALL organisations. This action cannot be undone.`,
      confirmText: 'Remove Role',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.removeAllowedRole(clientId, role);
      this.toastService.success(`Role "${role}" removed from allowlist`);
      await this.loadAllowedRoles(clientId);
    } catch (err: any) {
      console.error('Error removing allowed role:', err);
      if (err.status === 403) {
        this.toastService.error('You do not have permission to manage allowed roles');
      } else if (err.status === 404) {
        this.toastService.error('Role or client not found');
      } else {
        this.toastService.error('Failed to remove allowed role. Please try again.');
      }
    }
  }
}
