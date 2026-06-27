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
  orgId: string;
  clientId: string;
  clientName: string;
  clientType: string;
  redirectUris: string;
  allowedScopes: string;
  requirePkce: boolean;
  autoSubscribe: boolean;
  publik: boolean;
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

export interface AddRoleRequest {
  role: string;
}

// Client-to-Client Role Management (M2M)

export interface ClientRole {
  targetClientId: string;
  role: string;
  createdAt: string;
}

export interface ClientRolesResponse {
  srcClientId: string;
  roles: ClientRole[];
}

export interface AddClientRoleRequest {
  targetClientId: string;
  role: string;
}

export interface AllowedRole {
  clientId: string;
  role: string;
  isDefault: boolean;
  availableToForeignOrgs: boolean;
}

export interface Organisation {
  id: string;
  name: string;
  createdAt: string;
  roles: string[];
}

export interface CreateOrganisationRequest {
  name: string;
}

export interface UpdateOrganisationRequest {
  name: string;
}

export interface CreateAccountResponse {
  account: Account;
  inviteToken?: string;
}

export interface AuditEntry {
  rev: number;
  revType: number;
  revTimestamp: number;
  username: string | null;
  correlationId: string | null;
  changeNote: string | null;
  [key: string]: any;
}

export interface ConfigResponse {
  signupAllowed: boolean;
  allowNativeSignin: boolean;
  allowGoogleSignin: boolean;
  allowMicrosoftSignin: boolean;
  sessionTimeoutSeconds: number;
  insecureClientSecret: boolean;
  warningMessage: string;
  legalContent: string | null;
  brandLogoUrl: string;
  brandLogoAlt: string;
  brandName: string;
  auditRetentionDays: number;
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
  private allowGoogleSignin = signal<boolean>(false);
  private allowMicrosoftSignin = signal<boolean>(false);
  private sessionTimeoutSeconds = signal<number>(900);
  private insecureClientSecret = signal<boolean>(false);
  private warningMessage = signal<string>('');
  private clientsLoading = signal<boolean>(false);
  private clientsError = signal<string | null>(null);
  private organisations = signal<Organisation[]>([]);
  private organisationsLoading = signal<boolean>(false);
  private organisationsError = signal<string | null>(null);
  private currentOrganisation = signal<Organisation | null>(null);
  private legalContent = signal<string | null>(null);
  private readonly defaultBrandLogoUrl = 'https://abstratium.dev/abstratium-logo-small.png';
  private readonly defaultBrandLogoAlt = 'Abstratium Logo';
  private readonly defaultBrandName = 'ABSTRATIUM';
  private brandLogoUrl = signal<string>(this.defaultBrandLogoUrl);
  private brandLogoAlt = signal<string>(this.defaultBrandLogoAlt);
  private brandName = signal<string>(this.defaultBrandName);
  private readonly defaultAuditRetentionDays = 90;
  private auditRetentionDays = signal<number>(this.defaultAuditRetentionDays);

  legalContent$: Signal<string | null> = this.legalContent.asReadonly();
  brandLogoUrl$: Signal<string> = this.brandLogoUrl.asReadonly();
  brandLogoAlt$: Signal<string> = this.brandLogoAlt.asReadonly();
  brandName$: Signal<string> = this.brandName.asReadonly();
  signUpUsername$: Signal<string> = this.signUpUsername.asReadonly();
  signUpPassword$: Signal<string> = this.signUpPassword.asReadonly();
  signInRequestId$: Signal<string> = this.signInRequestId.asReadonly();
  accounts$: Signal<Account[]> = this.accounts.asReadonly();
  clients$: Signal<OAuthClient[]> = this.clients.asReadonly();
  signupAllowed$: Signal<boolean> = this.signupAllowed.asReadonly();
  allowNativeSignin$: Signal<boolean> = this.allowNativeSignin.asReadonly();
  allowGoogleSignin$: Signal<boolean> = this.allowGoogleSignin.asReadonly();
  allowMicrosoftSignin$: Signal<boolean> = this.allowMicrosoftSignin.asReadonly();
  sessionTimeoutSeconds$: Signal<number> = this.sessionTimeoutSeconds.asReadonly();
  insecureClientSecret$: Signal<boolean> = this.insecureClientSecret.asReadonly();
  warningMessage$: Signal<string> = this.warningMessage.asReadonly();
  clientsLoading$: Signal<boolean> = this.clientsLoading.asReadonly();
  clientsError$: Signal<string | null> = this.clientsError.asReadonly();
  organisations$: Signal<Organisation[]> = this.organisations.asReadonly();
  organisationsLoading$: Signal<boolean> = this.organisationsLoading.asReadonly();
  organisationsError$: Signal<string | null> = this.organisationsError.asReadonly();
  currentOrganisation$: Signal<Organisation | null> = this.currentOrganisation.asReadonly();
  auditRetentionDays$: Signal<number> = this.auditRetentionDays.asReadonly();

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

  setOrganisations(organisations: Organisation[]) {
    this.organisations.set(organisations);
  }

  setOrganisationsLoading(loading: boolean) {
    this.organisationsLoading.set(loading);
  }

  setOrganisationsError(error: string | null) {
    this.organisationsError.set(error);
  }

  setCurrentOrganisation(org: Organisation | null) {
    this.currentOrganisation.set(org);
  }

  setSignupAllowed(allowed: boolean) {
    this.signupAllowed.set(allowed);
  }

  setLegalContent(legalContent: string | null) {
    this.legalContent.set(legalContent);
  }

  setAllowNativeSignin(allowNativeSignin: boolean) {
    this.allowNativeSignin.set(allowNativeSignin);
  }

  setAllowGoogleSignin(allowGoogleSignin: boolean) {
    this.allowGoogleSignin.set(allowGoogleSignin);
  }

  setAllowMicrosoftSignin(allowMicrosoftSignin: boolean) {
    this.allowMicrosoftSignin.set(allowMicrosoftSignin);
  }

  setSessionTimeoutSeconds(seconds: number) {
    this.sessionTimeoutSeconds.set(seconds);
  }

  setInsecureClientSecret(insecure: boolean) {
    this.insecureClientSecret.set(insecure);
  }

  setWarningMessage(message: string) {
    if(message === '-') {
      this.warningMessage.set('');
    } else {
      this.warningMessage.set(message);
    }
  }

  setBrandLogoUrl(url: string) {
    this.brandLogoUrl.set(url || this.defaultBrandLogoUrl);
  }

  setBrandLogoAlt(alt: string) {
    this.brandLogoAlt.set(alt || this.defaultBrandLogoAlt);
  }

  setBrandName(name: string) {
    this.brandName.set(name || this.defaultBrandName);
  }

  setAuditRetentionDays(days: number) {
    this.auditRetentionDays.set(days > 0 ? days : this.defaultAuditRetentionDays);
  }
}
