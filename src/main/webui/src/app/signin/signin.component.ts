import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, effect, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ModelService } from '../model.service';
import { Controller } from '../controller';

interface AuthRequestDetails {
    clientName: string;
    scope: string;
}

interface AuthenticationResponse {
    name: string;
}

interface InviteData {
    authProvider: string;
    email: string;
    password?: string;
}

@Component({
    selector: 'signin',
    imports: [CommonModule, ReactiveFormsModule, RouterLink],
    templateUrl: './signin.component.html',
    styleUrl: './signin.component.scss',
})
export class SigninComponent implements OnInit {

    modelService = inject(ModelService)
    controller = inject(Controller)
    route = inject(ActivatedRoute)
    http = inject(HttpClient)
    fb = inject(FormBuilder)

    requestId = "";
    clientName = "";
    scopes: string[] = [];
    errorMessage = "";
    getApproval = false;
    signinForm: FormGroup;
    isSubmitting = false;
    name = "";
    showSignup = false;
    signinIsExpired = false;
    inviteData: InviteData | null = null;
    showNativeSignin = true;
    showGoogleSignin = true;

    constructor(
    ) {
        // Check for invite data in session storage
        const inviteDataStr = sessionStorage.getItem('inviteData');
        if (inviteDataStr) {
            try {
                this.showGoogleSignin = false;
                this.showNativeSignin = false;
                this.inviteData = JSON.parse(inviteDataStr);
                // Filter sign-in options based on invite data
                if (this.inviteData?.authProvider === 'native') {
                    this.showNativeSignin = true;
                } else if (this.inviteData?.authProvider === 'google') {
                    this.showGoogleSignin = true;
                } else {
                    throw new Error("Unexpected authorization provider '" + this.inviteData?.authProvider + "' please contact support")
                }
            } catch (err) {
                console.error('Error parsing invite data:', err);
            }
        }

        const username = this.inviteData?.email || this.modelService.signUpUsername$();
        const password = this.inviteData?.password || this.modelService.signUpPassword$();

        this.signinForm = this.fb.group({
            username: [username, Validators.required],
            password: [password, Validators.required]
        });

        effect(() => {
            this.showSignup = this.modelService.signupAllowed$();

            // if invite data is present, we may have already decided if we
            // should show native sign in, so only let the backend override if
            // there is no invite data. invite data is read in the constructor 
            // which is always called before this code
            let allowNativeSignin = this.modelService.allowNativeSignin$();
            if(!this.inviteData) {
                this.showNativeSignin = allowNativeSignin;
            }
        });
    }

    ngOnInit(): void {
        this.requestId = this.route.snapshot.paramMap.get('requestId')!;
        this.controller.setSignInRequestId(this.requestId);

        // now fetch details from backend
        this.http.get<AuthRequestDetails>(`/oauth2/authorize/details/${this.requestId}`)
            .subscribe({
                next: (details) => {
                    this.clientName = details.clientName;
                    this.scopes = details.scope.split(" ");
                },
                error: (error) => {
                    this.errorMessage = error.message;
                }
            });

        // set a timeout for just under 10 minutes time, since the server will expire
        // the request then
        setTimeout(() => {
            this.signinIsExpired = true;
        }, (10 * 60 * 1000) - (30 * 1000));
    }

    signin() {
        if (this.signinForm.invalid) {
            this.signinForm.markAllAsTouched();
            return;
        }

        this.isSubmitting = true;
        this.errorMessage = '';

        const headers = new HttpHeaders({
            'Content-Type': 'application/x-www-form-urlencoded'
        });

        const formData = new URLSearchParams();
        formData.append('username', this.signinForm.value.username);
        formData.append('password', this.signinForm.value.password);
        formData.append('request_id', this.requestId);

        this.http.post<AuthenticationResponse>(`/oauth2/authorize/authenticate`, formData.toString(), { headers }).subscribe({
            next: (authenticationResponse) => {
                this.getApproval = true;
                this.name = authenticationResponse.name;
                this.isSubmitting = false;
                
                // Check if we need to redirect to password change for native invite
                if (this.inviteData?.authProvider === 'native' && this.inviteData?.password) {
                    // Mark that password change is needed
                    sessionStorage.setItem('requirePasswordChange', 'true');
                }
            },
            error: (error) => {
                if(error.status === 410) {
                    this.signinIsExpired = true;
                }
                this.errorMessage = error?.error?.details || error.error || error.message || 'Authentication failed';
                this.isSubmitting = false;
            }
        });
    }

    signinWithGoogle() {
        // Redirect to Google OAuth initiation endpoint
        window.location.href = `/oauth2/federated/google?request_id=${this.requestId}`;
    }

}
