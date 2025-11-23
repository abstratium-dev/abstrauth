import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

interface AuthRequestDetails {
    clientName: string;
    scope: string;
}

interface AuthenticationResponse {
    name: string;
}

@Component({
    selector: 'signin',
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './signin.component.html',
    styleUrl: './signin.component.scss',
})
export class SigninComponent implements OnInit {

    requestId = "";
    clientName = "";
    scopes: string[] = [];
    errorMessage = "";
    getApproval = false;
    signinForm: FormGroup;
    isSubmitting = false;
    name = "";

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
        private fb: FormBuilder
    ) {
        this.signinForm = this.fb.group({
            username: ['', Validators.required],
            password: ['', Validators.required]
        });
    }

    ngOnInit(): void {
        this.requestId = this.route.snapshot.paramMap.get('requestId')!;

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
                this.errorMessage = error.error || error.message || 'Authentication failed';
                this.isSubmitting = false;
            }
        });
    }

}
