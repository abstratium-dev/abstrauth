import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Account, AddClientRoleRequest, AllowedRole, AuditEntry, ClientRole, ClientRolesResponse, ClientSecret, ConfigResponse, CreateAccountResponse, CreateOrganisationRequest, CreateSecretRequest, CreateSecretResponse, ModelService, OAuthClient, Organisation, UpdateOrganisationRequest } from './model.service';

@Injectable({
  providedIn: 'root',
})
export class Controller {

  private modelService = inject(ModelService);
  private http = inject(HttpClient);

  setSignUpUsername(username: string) {
    this.modelService.setSignUpUsername(username);
  }

  setSignUpPassword(password: string) {
    this.modelService.setSignUpPassword(password);
  }

  setSignInRequestId(requestId: string) {
    this.modelService.setSignInRequestId(requestId);
  }

  loadAccounts() {
    this.http.get<Account[]>('/api/accounts').subscribe({
      next: (accounts) => {
        this.modelService.setAccounts(accounts);
      },
      error: (err) => {
        console.error('Error loading accounts:', err);
        this.modelService.setAccounts([]);
      }
    });
  }

  loadClients() {
    this.modelService.setClientsLoading(true);
    this.modelService.setClientsError(null);
    
    this.http.get<OAuthClient[]>('/api/clients').subscribe({
      next: (clients) => {
        this.modelService.setClients(clients);
        this.modelService.setClientsLoading(false);
      },
      error: (err) => {
        console.error('Error loading clients:', err);
        this.modelService.setClients([]);
        this.modelService.setClientsError('Failed to load clients');
        this.modelService.setClientsLoading(false);
      }
    });
  }

  loadConfig(): Promise<void> {
    return firstValueFrom(
      this.http.get<ConfigResponse>('/public/config')
    ).then(response => {
      this.modelService.setSignupAllowed(response.signupAllowed);
      this.modelService.setAllowNativeSignin(response.allowNativeSignin);
      this.modelService.setAllowGoogleSignin(response.allowGoogleSignin ?? false);
      this.modelService.setAllowMicrosoftSignin(response.allowMicrosoftSignin ?? false);
      this.modelService.setSessionTimeoutSeconds(response.sessionTimeoutSeconds);
      this.modelService.setInsecureClientSecret(response.insecureClientSecret);
      this.modelService.setWarningMessage(response.warningMessage || '');
      this.modelService.setLegalContent(response.legalContent || null);
      this.modelService.setBrandLogoUrl(response.brandLogoUrl || '');
      this.modelService.setBrandLogoAlt(response.brandLogoAlt || '');
      this.modelService.setBrandName(response.brandName || '');
    }).catch(err => {
      console.error('Error loading config:', err);
      this.modelService.setSignupAllowed(false);
      this.modelService.setAllowNativeSignin(false);
      this.modelService.setSessionTimeoutSeconds(900); // Default to 15 minutes
      this.modelService.setInsecureClientSecret(false);
      this.modelService.setWarningMessage('');
      this.modelService.setLegalContent(null);
    });
  }

  async createClient(clientData: {
    clientId: string;
    clientName: string;
    clientType: string;
    redirectUris: string;
    allowedScopes: string;
    requirePkce: boolean;
    autoSubscribe: boolean;
    publik: boolean;
  }): Promise<OAuthClient> {
    try {
      const response = await firstValueFrom(
        this.http.post<OAuthClient>('/api/clients', clientData)
      );
      // Reload clients list after successful creation
      this.loadClients();
      return response;
    } catch (error) {
      console.error('Error creating client:', error);
      throw error;
    }
  }

  async updateClient(clientId: string, clientData: any): Promise<any> {
    try {
      const response = await firstValueFrom(
        this.http.put<any>(`/api/clients/${clientId}`, clientData)
      );
      // Reload clients list after successful update
      this.loadClients();
      return response;
    } catch (error) {
      console.error('Error updating client:', error);
      throw error;
    }
  }

  async deleteClient(clientId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/clients/${clientId}`)
      );
      // Reload clients list after successful deletion
      this.loadClients();
    } catch (error) {
      console.error('Error deleting client:', error);
      throw error;
    }
  }

  async addAccountRole(accountId: string, clientId: string, role: string): Promise<any> {
    try {
      const response = await firstValueFrom(
        this.http.post<any>('/api/accounts/role', {
          accountId,
          clientId,
          role
        })
      );
      // Reload accounts list after successful role addition
      this.loadAccounts();
      return response;
    } catch (error) {
      console.error('Error adding account role:', error);
      throw error;
    }
  }

  async removeAccountRole(accountId: string, clientId: string, role: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.request('delete', '/api/accounts/role', {
          body: {
            accountId,
            clientId,
            role
          }
        })
      );
      // Reload accounts list after successful role removal
      this.loadAccounts();
    } catch (error) {
      console.error('Error removing account role:', error);
      throw error;
    }
  }

  async createAccount(email: string, name: string, authProvider: string): Promise<CreateAccountResponse> {
    try {
      const response = await firstValueFrom(
        this.http.post<CreateAccountResponse>('/api/accounts', {
          email,
          name,
          authProvider
        })
      );
      // Reload accounts list after successful creation
      this.loadAccounts();
      return response;
    } catch (error) {
      console.error('Error creating account:', error);
      throw error;
    }
  }

  async resetPassword(oldPassword: string, newPassword: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.post('/api/accounts/reset-password', {
          oldPassword,
          newPassword
        })
      );
    } catch (error) {
      console.error('Error resetting password:', error);
      throw error;
    }
  }

  async deleteAccount(accountId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete(`/api/accounts/${accountId}`)
      );
      // Reload accounts list after successful deletion
      this.loadAccounts();
    } catch (error) {
      console.error('Error deleting account:', error);
      throw error;
    }
  }

  async deleteOwnAccount(): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete('/api/accounts/me')
      );
    } catch (error) {
      console.error('Error deleting own account:', error);
      throw error;
    }
  }

  // Client Secret Management

  async listClientSecrets(clientId: string): Promise<ClientSecret[]> {
    try {
      return await firstValueFrom(
        this.http.get<ClientSecret[]>(`/api/clients/${clientId}/secrets`)
      );
    } catch (error) {
      console.error('Error listing client secrets:', error);
      throw error;
    }
  }

  async createClientSecret(clientId: string, request: CreateSecretRequest): Promise<CreateSecretResponse> {
    try {
      return await firstValueFrom(
        this.http.post<CreateSecretResponse>(`/api/clients/${clientId}/secrets`, request)
      );
    } catch (error) {
      console.error('Error creating client secret:', error);
      throw error;
    }
  }

  async revokeClientSecret(clientId: string, secretId: number): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/clients/${clientId}/secrets/${secretId}`)
      );
    } catch (error) {
      console.error('Error revoking client secret:', error);
      throw error;
    }
  }

  async deleteClientSecret(clientId: string, secretId: number): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/clients/${clientId}/secrets/${secretId}/permanent`)
      );
    } catch (error) {
      console.error('Error deleting client secret:', error);
      throw error;
    }
  }

  // Client-to-Client Role Management (M2M)

  async listClientRoles(srcClientId: string): Promise<ClientRolesResponse> {
    try {
      return await firstValueFrom(
        this.http.get<ClientRolesResponse>(`/api/clients/${srcClientId}/client-roles`)
      );
    } catch (error) {
      console.error('Error listing client roles:', error);
      throw error;
    }
  }

  async addClientRole(srcClientId: string, request: AddClientRoleRequest): Promise<ClientRole> {
    try {
      return await firstValueFrom(
        this.http.post<ClientRole>(`/api/clients/${srcClientId}/client-roles`, request)
      );
    } catch (error) {
      console.error('Error adding client role:', error);
      throw error;
    }
  }

  async removeClientRole(srcClientId: string, targetClientId: string, role: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/clients/${srcClientId}/client-roles/${targetClientId}/${role}`)
      );
    } catch (error) {
      console.error('Error removing client role:', error);
      throw error;
    }
  }

  loadOrganisation(orgId: string): void {
    this.http.get<Organisation>(`/api/organisations/${orgId}`).subscribe({
      next: (org) => {
        this.modelService.setCurrentOrganisation(org);
      },
      error: (err) => {
        console.error('Error loading organisation:', err);
        this.modelService.setCurrentOrganisation(null);
      }
    });
  }

  async updateOrganisationName(orgId: string, request: UpdateOrganisationRequest): Promise<Organisation> {
    try {
      const org = await firstValueFrom(
        this.http.put<Organisation>(`/api/organisations/${orgId}`, request)
      );
      this.modelService.setCurrentOrganisation(org);
      this.loadOrganisations();
      this.loadCurrentOrganisation();
      return org;
    } catch (error) {
      console.error('Error updating organisation:', error);
      throw error;
    }
  }

  loadCurrentOrganisation(): void {
    this.http.get<Organisation>('/api/organisations/current').subscribe({
      next: (org) => {
        this.modelService.setCurrentOrganisation(org);
      },
      error: (err) => {
        console.error('Error loading current organisation:', err);
        this.modelService.setCurrentOrganisation(null);
      }
    });
  }

  loadOrganisations(): void {
    this.modelService.setOrganisationsLoading(true);
    this.modelService.setOrganisationsError(null);
    this.http.get<Organisation[]>('/api/organisations').subscribe({
      next: (orgs) => {
        this.modelService.setOrganisations(orgs);
        this.modelService.setOrganisationsLoading(false);
      },
      error: (err) => {
        console.error('Error loading organisations:', err);
        this.modelService.setOrganisations([]);
        this.modelService.setOrganisationsError('Failed to load organisations');
        this.modelService.setOrganisationsLoading(false);
      }
    });
  }

  async createOrganisation(request: CreateOrganisationRequest): Promise<Organisation> {
    try {
      const response = await firstValueFrom(
        this.http.post<Organisation>('/api/organisations', request)
      );
      this.loadOrganisations();
      return response;
    } catch (error) {
      console.error('Error creating organisation:', error);
      throw error;
    }
  }

  async deleteOrganisation(orgId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete(`/api/organisations/${orgId}`)
      );
      this.loadOrganisations();
    } catch (error) {
      console.error('Error deleting organisation:', error);
      throw error;
    }
  }

  async listAllowedRoles(clientId: string): Promise<AllowedRole[]> {
    try {
      return await firstValueFrom(
        this.http.get<AllowedRole[]>(`/api/clients/${clientId}/allowed-roles`)
      );
    } catch (error) {
      console.error('Error listing allowed roles:', error);
      throw error;
    }
  }

  async listAllowedRolesForUsersInClientsOrg(clientId: string): Promise<AllowedRole[]> {
    try {
      return await firstValueFrom(
        this.http.get<AllowedRole[]>(`/api/clients/${clientId}/allowed-roles-for-users-in-clients-org`)
      );
    } catch (error) {
      console.error('Error listing allowed roles:', error);
      throw error;
    }
  }

  async addAllowedRole(clientId: string, request: { role: string; isDefault: boolean; availableToForeignOrgs: boolean }): Promise<AllowedRole> {
    try {
      const response = await firstValueFrom(
        this.http.post<AllowedRole>(`/api/clients/${clientId}/allowed-roles`, request)
      );
      return response;
    } catch (error) {
      console.error('Error adding allowed role:', error);
      throw error;
    }
  }

  async updateAllowedRole(clientId: string, role: string, request: { isDefault: boolean; availableToForeignOrgs: boolean }): Promise<AllowedRole> {
    try {
      const response = await firstValueFrom(
        this.http.put<AllowedRole>(`/api/clients/${clientId}/allowed-roles/${role}`, request)
      );
      return response;
    } catch (error) {
      console.error('Error updating allowed role:', error);
      throw error;
    }
  }

  async removeAllowedRole(clientId: string, role: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/clients/${clientId}/allowed-roles/${role}`)
      );
    } catch (error) {
      console.error('Error removing allowed role:', error);
      throw error;
    }
  }

  async makeOwner(orgId: string, accountId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.post<void>(`/api/organisations/${orgId}/members/${accountId}/owner`, {})
      );
    } catch (error) {
      console.error('Error making owner:', error);
      throw error;
    }
  }

  async removeOwner(orgId: string, accountId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/organisations/${orgId}/members/${accountId}/owner`)
      );
    } catch (error) {
      console.error('Error removing owner:', error);
      throw error;
    }
  }

  async getOrganisationOwners(orgId: string): Promise<string[]> {
    try {
      return await firstValueFrom(
        this.http.get<string[]>(`/api/organisations/${orgId}/owners`)
      );
    } catch (error) {
      console.error('Error getting organisation owners:', error);
      throw error;
    }
  }

  async getAuditHistory(entityType: string, primaryKey: string): Promise<AuditEntry[]> {
    try {
      return await firstValueFrom(
        this.http.get<AuditEntry[]>(`/api/audit/${entityType}/${primaryKey}`)
      );
    } catch (error) {
      console.error('Error loading audit history:', error);
      throw error;
    }
  }

  async getRelatedAuditHistory(relatedEntityType: string, parentEntityType: string, parentKey: string): Promise<AuditEntry[]> {
    try {
      return await firstValueFrom(
        this.http.get<AuditEntry[]>(`/api/audit/related/${relatedEntityType}/by-${parentEntityType}/${parentKey}`)
      );
    } catch (error) {
      console.error('Error loading related audit history:', error);
      throw error;
    }
  }

  async getAuditTypes(): Promise<string[]> {
    try {
      return await firstValueFrom(
        this.http.get<string[]>('/api/audit/types')
      );
    } catch (error) {
      console.error('Error loading audit types:', error);
      throw error;
    }
  }
}
