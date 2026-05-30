import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';
import { WINDOW } from '../window.token';

interface Organisation {
    id: string;
    name: string;
}

interface OrgSelectionResponse {
    consentRequired: boolean;
}

@Component({
    selector: 'app-org-selection',
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './org-selection.component.html',
    styleUrl: './org-selection.component.scss',
})
export class OrgSelectionComponent implements OnInit {
    http = inject(HttpClient);
    route = inject(ActivatedRoute);
    router = inject(Router);
    fb = inject(FormBuilder);
    authService = inject(AuthService);
    window = inject(WINDOW);

    requestId = "";
    organisations: Organisation[] = [];
    selectedOrgId = "";
    errorMessage = "";
    isLoading = true;
    isSubmitting = false;
    accountId = "";

    orgSelectionForm: FormGroup;

    constructor() {
        this.orgSelectionForm = this.fb.group({
            orgId: ['', Validators.required]
        });
    }

    ngOnInit(): void {
        this.requestId = this.route.snapshot.paramMap.get('requestId')!;

        // Load the organisations for this request
        this.loadOrganisations();
    }

    loadOrganisations(): void {
        this.isLoading = true;
        this.errorMessage = "";

        this.http.get<Organisation[]>(`/org-selection/${this.requestId}`)
            .subscribe({
                next: (orgs) => {
                    this.organisations = orgs;
                    this.isLoading = false;

                    if (orgs.length === 0) {
                        this.errorMessage = "You are not a member of any organisation. Please contact your administrator.";
                        return;
                    }

                    if (orgs.length === 1) {
                        // Auto-select if only one org
                        this.selectedOrgId = orgs[0].id;
                        this.orgSelectionForm.patchValue({ orgId: orgs[0].id });
                    } else {
                        // Check for lastOrgId in localStorage
                        const lastOrgId = this.authService.getLastOrgId();
                        if (lastOrgId) {
                            const validOrg = orgs.find(o => o.id === lastOrgId);
                            if (validOrg) {
                                this.selectedOrgId = lastOrgId;
                                this.orgSelectionForm.patchValue({ orgId: lastOrgId });
                            }
                        }
                    }

                    // Get accountId from userinfo endpoint
                    this.loadAccountInfo();
                },
                error: (error) => {
                    this.isLoading = false;
                    this.errorMessage = error?.error?.error || "Failed to load organisations. Please try again.";
                }
            });
    }

    loadAccountInfo(): void {
        // Get userinfo to extract the account ID (sub claim)
        this.http.get<any>('/api/userinfo').subscribe({
            next: (userInfo) => {
                this.accountId = userInfo.sub;
            },
            error: () => {
                this.errorMessage = "Failed to load user information. Please try signing in again.";
            }
        });
    }

    onOrgChange(orgId: string): void {
        this.selectedOrgId = orgId;
    }

    selectOrg(): void {
        if (this.orgSelectionForm.invalid || !this.selectedOrgId) {
            this.errorMessage = "Please select an organisation.";
            return;
        }

        if (!this.accountId) {
            this.errorMessage = "User information not available. Please try signing in again.";
            return;
        }

        this.isSubmitting = true;
        this.errorMessage = "";

        const headers = new HttpHeaders({
            'Content-Type': 'application/x-www-form-urlencoded'
        });

        const formData = new URLSearchParams();
        formData.append('request_id', this.requestId);
        formData.append('org_id', this.selectedOrgId);
        formData.append('account_id', this.accountId);

        this.http.post<OrgSelectionResponse>('/org-selection', formData.toString(), { headers })
            .subscribe({
                next: (response) => {
                    // Store selected org as lastOrgId in localStorage
                    this.authService.setLastOrgId(this.selectedOrgId);

                    if (response.consentRequired) {
                        // Redirect to consent/approval page
                        // The backend will redirect to /signin/{requestId} for consent
                        this.window.location.href = `/signin/${this.requestId}`;
                    } else {
                        // No consent needed, redirect to home
                        this.router.navigate(['/']);
                    }
                },
                error: (error) => {
                    this.isSubmitting = false;
                    this.errorMessage = error?.error?.error || "Failed to select organisation. Please try again.";
                }
            });
    }
}
