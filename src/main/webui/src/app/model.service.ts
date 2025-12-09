import { Injectable, signal, Signal } from '@angular/core';

export interface Account {
  id: string;
  email: string;
  name: string;
  emailVerified: boolean;
  authProvider: string;
  picture?: string;
  createdAt: string;
}

export interface OAuthClient {
  id: string;
  clientId: string;
  clientName: string;
  clientType: string;
  redirectUris: string;
  allowedScopes: string;
  requirePkce: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class ModelService {

  private signUpUsername = signal('');
  private signUpPassword = signal('');
  private signInRequestId = signal('');
  private accounts = signal<Account[]>([]);
  private clients = signal<OAuthClient[]>([]);
  private signupAllowed = signal<boolean>(false);
  private clientsLoading = signal<boolean>(false);
  private clientsError = signal<string | null>(null);

  signUpUsername$: Signal<string> = this.signUpUsername.asReadonly();
  signUpPassword$: Signal<string> = this.signUpPassword.asReadonly();
  signInRequestId$: Signal<string> = this.signInRequestId.asReadonly();
  accounts$: Signal<Account[]> = this.accounts.asReadonly();
  clients$: Signal<OAuthClient[]> = this.clients.asReadonly();
  signupAllowed$: Signal<boolean> = this.signupAllowed.asReadonly();
  clientsLoading$: Signal<boolean> = this.clientsLoading.asReadonly();
  clientsError$: Signal<string | null> = this.clientsError.asReadonly();

  setSignUpUsername(username: string) {
    this.signUpUsername.set(username);
  }

  setSignUpPassword(password: string) {
    this.signUpPassword.set(password);
  }

  setSignInRequestId(requestId: string) {
    this.signInRequestId.set(requestId);
  }

  setAccounts(accounts: Account[]) {
    this.accounts.set(accounts);
  }

  setClients(clients: OAuthClient[]) {
    this.clients.set(clients);
  }

  setClientsLoading(loading: boolean) {
    this.clientsLoading.set(loading);
  }

  setClientsError(error: string | null) {
    this.clientsError.set(error);
  }

  setSignupAllowed(allowed: boolean) {
    this.signupAllowed.set(allowed);
  }
}
