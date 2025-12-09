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

    constructor(
    ) {
        const username = this.modelService.signUpUsername$();
        const password = this.modelService.signUpPassword$();

        this.signinForm = this.fb.group({
            username: [username, Validators.required],
            password: [password, Validators.required]
        });

        effect(() => {
            this.showSignup = this.modelService.signupAllowed$();
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
            },
            error: (error) => {
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
