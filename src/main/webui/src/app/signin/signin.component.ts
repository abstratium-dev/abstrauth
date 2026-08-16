import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ModelService } from '../model.service';
import { Controller } from '../controller';
import { AutofocusDirective } from '../autofocus.directive';
import { AuthService } from '../auth.service';

interface AuthRequestDetails {
    clientId: string;
    clientName: string;
    scope: string;
}

interface AuthenticationResponse {
    name: string;
    redirectTo?: string; // If present, redirect to org-selection page
}

interface InviteData {
    authProvider: string;
    email: string;
    password?: string;
}

@Component({
    selector: 'signin',
    imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, AutofocusDirective],
    templateUrl: './signin.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './signin.component.scss',
})
export class SigninComponent implements OnInit {

    modelService = inject(ModelService)
    controller = inject(Controller)
    route = inject(ActivatedRoute)
    router = inject(Router)
    http = inject(HttpClient)
    fb = inject(FormBuilder)
    authService = inject(AuthService)

    requestId = "";
    signinForm: FormGroup;

    private clientId$ = signal<string>("");
    private clientName$ = signal<string>("");
    private scopes$ = signal<string[]>([]);
    private errorMessage$ = signal<string>("");
    private getApproval$ = signal<boolean>(false);
    private isSubmitting$ = signal<boolean>(false);
    private name$ = signal<string>("");
    private signinIsExpired$ = signal<boolean>(false);
    private inviteData$ = signal<InviteData | null>(null);
    private rememberApproval$ = signal<boolean>(false);
    private shouldShowApproval$ = signal<boolean>(false);

    get clientId(): string { return this.clientId$(); }
    set clientId(v: string) { this.clientId$.set(v); }
    get clientName(): string { return this.clientName$(); }
    set clientName(v: string) { this.clientName$.set(v); }
    get scopes(): string[] { return this.scopes$(); }
    set scopes(v: string[]) { this.scopes$.set(v); }
    get errorMessage(): string { return this.errorMessage$(); }
    set errorMessage(v: string) { this.errorMessage$.set(v); }
    get getApproval(): boolean { return this.getApproval$(); }
    set getApproval(v: boolean) { this.getApproval$.set(v); }
    get isSubmitting(): boolean { return this.isSubmitting$(); }
    set isSubmitting(v: boolean) { this.isSubmitting$.set(v); }
    get name(): string { return this.name$(); }
    set name(v: string) { this.name$.set(v); }
    get signinIsExpired(): boolean { return this.signinIsExpired$(); }
    set signinIsExpired(v: boolean) { this.signinIsExpired$.set(v); }
    get inviteData(): InviteData | null { return this.inviteData$(); }
    set inviteData(v: InviteData | null) { this.inviteData$.set(v); }
    get rememberApproval(): boolean { return this.rememberApproval$(); }
    set rememberApproval(v: boolean) { this.rememberApproval$.set(v); }
    get shouldShowApproval(): boolean { return this.shouldShowApproval$(); }
    set shouldShowApproval(v: boolean) { this.shouldShowApproval$.set(v); }

    private static readonly SCOPE_DESCRIPTIONS: Record<string, string> = {
        'profile': 'Your name',
        'email': 'Your email address and whether it\'s verified',
    };

    /**
     * Returns human-readable descriptions for scopes that release user data.
     * 'openid' is excluded because it grants no personal data (it only
     * requests an ID token). Unknown scopes fall back to the raw string.
     */
    get scopeDescriptions(): string[] {
        return this.scopes$()
            .filter((s: string) => s !== 'openid')
            .map((s: string) => SigninComponent.SCOPE_DESCRIPTIONS[s] ?? s);
    }

    /**
     * True if any scope other than 'openid' was requested.
     * When false, the approval dialog shows a generic "grant access" prompt
     * instead of a bullet list of data scopes.
     */
    get hasDataScopes(): boolean {
        return this.scopeDescriptions.length > 0;
    }

    get showSignup(): boolean {
        return this.modelService.signupAllowed$();
    }

    get showNativeSignin(): boolean {
        return this.inviteData ? this.inviteData.authProvider === 'native' : this.modelService.allowNativeSignin$();
    }

    get showGoogleSignin(): boolean {
        return this.inviteData ? this.inviteData.authProvider === 'google' : this.modelService.allowGoogleSignin$();
    }

    get showMicrosoftSignin(): boolean {
        return this.inviteData ? this.inviteData.authProvider === 'microsoft' : this.modelService.allowMicrosoftSignin$();
    }

    constructor(
    ) {
        // Check for invite data in session storage
        const inviteDataStr = sessionStorage.getItem('inviteData');
        if (inviteDataStr) {
            try {
                const parsed: InviteData = JSON.parse(inviteDataStr);
                // Filter sign-in options based on invite data
                if (parsed?.authProvider !== 'native'
                    && parsed?.authProvider !== 'google'
                    && parsed?.authProvider !== 'microsoft') {
                    throw new Error("Unexpected authorization provider '" + parsed?.authProvider + "' please contact support")
                }
                this.inviteData$.set(parsed);
            } catch (err) {
                console.error('Error parsing invite data:', err);
                this.inviteData$.set(null);
            }
        }

        const username = this.inviteData$()?.email || this.modelService.signUpUsername$();
        const password = this.inviteData$()?.password || this.modelService.signUpPassword$();

        this.signinForm = this.fb.group({
            username: [username, Validators.required],
            password: [password, Validators.required]
        });
    }

    ngOnInit(): void {
        this.requestId = this.route.snapshot.paramMap.get('requestId')!;
        this.controller.setSignInRequestId(this.requestId);

        // now fetch details from backend
        this.http.get<AuthRequestDetails>(`/oauth2/authorize/details/${this.requestId}`)
            .subscribe({
                next: (details) => {
                    this.clientId = details.clientId;
                    this.clientName = details.clientName;
                    this.scopes = details.scope ? details.scope.trim().split(/\s+/) : [];
                    
                    // Check if user is already authenticated
                    // If yes, approve the request and skip to approval step (they're completing OAuth flow for a third-party app)
                    if (this.authService.isAuthenticated()) {
                        console.debug("[SIGNIN] User is already authenticated, approving request and skipping to approval");
                        
                        // Call backend to approve the authorization request for the authenticated user
                        // Note: This endpoint is under /api so OIDC BFF authentication applies
                        this.http.post<AuthenticationResponse>(
                            `/api/oauth/approve-authenticated?request_id=${this.requestId}`,
                            null
                        ).subscribe({
                            next: (response) => {
                                this.name$.set(response.name);
                                
                                // Check for stored approval - will set getApproval = true only if UI needs to be shown
                                setTimeout(() => this.checkStoredApproval(), 100);
                            },
                            error: (error) => {
                                console.error("[SIGNIN] Failed to approve for authenticated user:", error);
                                if (error.status === 403) {
                                    // User has no roles for this client
                                    this.errorMessage$.set((error.error || "You do not have any roles for this application. Please contact your administrator.") + " (" + this.clientId$() + ")");
                                } else {
                                    this.errorMessage$.set("Failed to process authorization request. Please try again.");
                                }
                                // Show the error on the signin page (not approval page)
                                this.getApproval$.set(false);
                            }
                        });
                    }
                },
                error: (error) => {
                    this.errorMessage$.set(error.message);
                }
            });

        // set a timeout for just under 10 minutes time, since the server will expire
        // the request then
        setTimeout(() => {
            this.signinIsExpired$.set(true);
        }, (10 * 60 * 1000) - (30 * 1000));
    }

    signin() {
        if (this.signinForm.invalid) {
            this.signinForm.markAllAsTouched();
            return;
        }

        this.isSubmitting$.set(true);
        this.errorMessage$.set('');

        const headers = new HttpHeaders({
            'Content-Type': 'application/x-www-form-urlencoded'
        });

        const formData = new URLSearchParams();
        formData.append('username', this.signinForm.value.username);
        formData.append('password', this.signinForm.value.password);
        formData.append('request_id', this.requestId);

        this.http.post<AuthenticationResponse>(`/oauth2/authorize/authenticate`, formData.toString(), { headers }).subscribe({
            next: (authenticationResponse) => {
                this.isSubmitting$.set(false);

                // Check if we need to redirect to org-selection page (multiple orgs)
                if (authenticationResponse.redirectTo) {
                    console.debug("[SIGNIN] Redirecting to org-selection:", authenticationResponse.redirectTo);
                    this.router.navigateByUrl(authenticationResponse.redirectTo);
                    return;
                }

                this.name$.set(authenticationResponse.name);

                // Check if we need to redirect to password change for native invite
                if (this.inviteData$()?.authProvider === 'native' && this.inviteData$()?.password) {
                    // Mark that password change is needed
                    sessionStorage.setItem('requirePasswordChange', 'true');
                    console.debug("[SIGNIN] Marked password change required");
                }

                // Check for stored approval after successful authentication
                // This will set getApproval = true only if UI needs to be shown
                setTimeout(() => this.checkStoredApproval(), 100);
            },
            error: (error) => {
                if(error.status === 410) {
                    this.signinIsExpired = true;
                } else if (error.status === 403) {
                    // User has no roles for this client
                    this.errorMessage = (error.error?.error || error.error || "You do not have any roles for this application. Please contact your administrator.") + " (" + this.clientId + ")";
                } else {
                    this.errorMessage = error?.error?.details || error.error || error.message || 'Authentication failed';
                }
                this.isSubmitting$.set(false);
            }
        });
    }

    signinWithGoogle() {
        // Redirect to Google OAuth initiation endpoint
        window.location.href = `/oauth2/federated/google?request_id=${this.requestId}`;
    }

    signinWithMicrosoft() {
        // Redirect to Microsoft OAuth initiation endpoint
        window.location.href = `/oauth2/federated/microsoft?request_id=${this.requestId}`;
    }

    checkStoredApproval() {
        const key = `approval_${this.clientName}`;
        const stored = localStorage.getItem(key);
        console.debug("[SIGNIN] Checking stored approval: " + stored);
        
        if (!stored) {
            this.getApproval$.set(true);
            this.shouldShowApproval$.set(true);
            console.debug("[SIGNIN] No stored approval found");
            return;
        }
        
        try {
            const approval = JSON.parse(stored);
            const approvalDate = new Date(approval.date);
            const now = new Date();
            const daysDiff = (now.getTime() - approvalDate.getTime()) / (1000 * 60 * 60 * 24);
            
            // Check if approval is older than 30 days
            if (daysDiff > 30) {
                this.shouldShowApproval$.set(true);
                localStorage.removeItem(key);
                console.debug("[SIGNIN] Approval is older than 30 days");
                return;
            }
            
            // Check if scopes match
            const storedScopes = approval.scopes.sort().join(',');
            const currentScopes = this.scopes.sort().join(',');
            
            if (storedScopes !== currentScopes) {
                this.shouldShowApproval$.set(true);
                localStorage.removeItem(key);
                console.debug("[SIGNIN] Scopes do not match");
                return;
            }
            
            // Approval is valid, auto-approve
            // Keep shouldShowApproval false to hide the UI, submit directly
            this.shouldShowApproval$.set(false);
            this.autoApproveDirectly();
        } catch (err) {
            console.error('Error checking stored approval:', err);
            this.getApproval$.set(true);
            this.shouldShowApproval$.set(true);
            localStorage.removeItem(key);
        }
    }

    autoApprove() {
        // Automatically submit the approval form
        // Use setTimeout to ensure the form is rendered in the DOM
        setTimeout(() => {
            const form = document.querySelector('form[action="/oauth2/authorize"]') as HTMLFormElement;
            if (form) {
                console.debug("[SIGNIN] Auto-submitting approval form");
                form.submit();
            } else {
                console.error("[SIGNIN] Approval form not found in DOM");
            }
        }, 50);
    }

    autoApproveDirectly() {
        // Submit approval directly without showing the UI
        // Create a hidden form and submit it programmatically
        console.debug("[SIGNIN] Auto-approving directly without showing UI");
        
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = '/oauth2/authorize';
        
        const requestIdInput = document.createElement('input');
        requestIdInput.type = 'hidden';
        requestIdInput.name = 'request_id';
        requestIdInput.value = this.requestId;
        form.appendChild(requestIdInput);
        
        const consentInput = document.createElement('input');
        consentInput.type = 'hidden';
        consentInput.name = 'consent';
        consentInput.value = 'approve';
        form.appendChild(consentInput);
        
        document.body.appendChild(form);
        form.submit();
    }

    onApproveClick(form: HTMLFormElement, consent: HTMLInputElement) {
        // Save approval to localStorage if checkbox is checked
        // This runs BEFORE the form submits (button click happens before form submit)
        if (this.rememberApproval$()) {
            const key = `approval_${this.clientName}`;
            const approval = {
                date: new Date().toISOString(),
                scopes: this.scopes$()
            };
            localStorage.setItem(key, JSON.stringify(approval));
        }
        consent.value = "approve";
        form.submit();
    }

    onDenyClick(form: HTMLFormElement, consent: HTMLInputElement) {
        consent.value = "deny";
        form.submit();
    }
}
