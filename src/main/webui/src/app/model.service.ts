import { Injectable, signal, Signal } from '@angular/core';

export interface RoleInfo {
  clientId: string;
  role: string;
}

export interface Account {
  id: string;
  email: string;
  name: string;
  emailVerified: boolean;
  authProvider: string;
  picture?: string;
  createdAt: string;
  roles: RoleInfo[];
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
  clientSecret?: string;  // Only present on creation response
}

export interface ClientSecret {
  id: number;
  description: string;
  createdAt: string;
  expiresAt: string | null;
  active: boolean;
}

export interface CreateSecretRequest {
  description?: string;
  expiresInDays?: number;
}

export interface CreateSecretResponse {
  id: number;
  secret: string;  // Plain secret - only shown once!
  description: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface ServiceAccountRole {
  clientId: string;
  role: string;
  groupName: string;
}

export interface ServiceAccountRolesResponse {
  clientId: string;
  roles: string[];
}

export interface AddRoleRequest {
  role: string;
}

export interface CreateAccountResponse {
  account: Account;
  inviteToken: string;
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
  private allowNativeSignin = signal<boolean>(false);
  private sessionTimeoutSeconds = signal<number>(900);
  private insecureClientSecret = signal<boolean>(false);
  private warningMessage = signal<string>('');
  private clientsLoading = signal<boolean>(false);
  private clientsError = signal<string | null>(null);

  signUpUsername$: Signal<string> = this.signUpUsername.asReadonly();
  signUpPassword$: Signal<string> = this.signUpPassword.asReadonly();
  signInRequestId$: Signal<string> = this.signInRequestId.asReadonly();
  accounts$: Signal<Account[]> = this.accounts.asReadonly();
  clients$: Signal<OAuthClient[]> = this.clients.asReadonly();
  signupAllowed$: Signal<boolean> = this.signupAllowed.asReadonly();
  allowNativeSignin$: Signal<boolean> = this.allowNativeSignin.asReadonly();
  sessionTimeoutSeconds$: Signal<number> = this.sessionTimeoutSeconds.asReadonly();
  insecureClientSecret$: Signal<boolean> = this.insecureClientSecret.asReadonly();
  warningMessage$: Signal<string> = this.warningMessage.asReadonly();
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

  setAllowNativeSignin(allowNativeSignin: boolean) {
    this.allowNativeSignin.set(allowNativeSignin);
  }

  setSessionTimeoutSeconds(seconds: number) {
    this.sessionTimeoutSeconds.set(seconds);
  }

  setInsecureClientSecret(insecure: boolean) {
    this.insecureClientSecret.set(insecure);
  }

  setWarningMessage(message: string) {
    this.warningMessage.set(message);
  }
}
