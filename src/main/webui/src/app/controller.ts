import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Account, ModelService, OAuthClient } from './model.service';

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

  loadSignupAllowed() {
    this.http.get<{ allowed: boolean }>('/api/signup/allowed').subscribe({
      next: (response) => {
        this.modelService.setSignupAllowed(response.allowed);
      },
      error: (err) => {
        console.error('Error loading signup allowed flag:', err);
        this.modelService.setSignupAllowed(false);
      }
    });
  }
}
