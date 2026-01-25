import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Account, ClientSecret, CreateAccountResponse, CreateSecretRequest, CreateSecretResponse, ModelService, OAuthClient } from './model.service';

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

  loadConfig() {
    this.http.get<{ signupAllowed: boolean, allowNativeSignin: boolean, sessionTimeoutSeconds: number, insecureClientSecret: boolean }>('/public/config').subscribe({
      next: (response) => {
        this.modelService.setSignupAllowed(response.signupAllowed);
        this.modelService.setAllowNativeSignin(response.allowNativeSignin);
        this.modelService.setSessionTimeoutSeconds(response.sessionTimeoutSeconds);
        this.modelService.setInsecureClientSecret(response.insecureClientSecret);
      },
      error: (err) => {
        console.error('Error loading config:', err);
        this.modelService.setSignupAllowed(false);
        this.modelService.setAllowNativeSignin(false);
        this.modelService.setSessionTimeoutSeconds(900); // Default to 15 minutes
        this.modelService.setInsecureClientSecret(false);
      }
    });
  }

  // Deprecated: Use loadConfig() instead
  loadSignupAllowed() {
    this.loadConfig();
  }

  async createClient(clientData: {
    clientId: string;
    clientName: string;
    clientType: string;
    redirectUris: string;
    allowedScopes: string;
    requirePkce: boolean;
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
}
