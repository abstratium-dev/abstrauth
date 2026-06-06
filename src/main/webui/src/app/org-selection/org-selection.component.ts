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
    readonly HAS_SUBMITTED_KEY = 'orgSelectionSubmitted';

    orgSelectionForm: FormGroup;

    constructor() {
        this.orgSelectionForm = this.fb.group({
            orgId: ['', Validators.required]
        });
    }

    ngOnInit(): void {
        this.requestId = this.route.snapshot.paramMap.get('requestId')!;

        // Check if already submitted in this session (prevents error after form submission)
        const hasSubmitted = sessionStorage.getItem(this.HAS_SUBMITTED_KEY) === this.requestId;

        // Load the organisations for this request
        if (!hasSubmitted) {
            this.loadOrganisations();
        }
    }

    loadOrganisations(): void {
        this.isLoading = true;
        this.errorMessage = "";

        this.http.get<Organisation[]>(`/api/org-selection/${this.requestId}`)
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

                },
                error: (error) => {
                    this.isLoading = false;
                    this.errorMessage = error?.error?.error || "Failed to load organisations. Please try again.";
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

        this.isSubmitting = true;
        this.errorMessage = "";

        const headers = new HttpHeaders({
            'Content-Type': 'application/x-www-form-urlencoded'
        });

        const formData = new URLSearchParams();
        formData.append('request_id', this.requestId);
        formData.append('org_id', this.selectedOrgId);
        // account_id is now extracted from the OIDC session token by the backend

        this.http.post<OrgSelectionResponse>('/api/org-selection', formData.toString(), { headers })
            .subscribe({
                next: (response) => {
                    // Store selected org as lastOrgId in localStorage
                    this.authService.setLastOrgId(this.selectedOrgId);

                    // Mark as submitted in sessionStorage to prevent reload errors
                    sessionStorage.setItem(this.HAS_SUBMITTED_KEY, this.requestId);

                    // After org selection, submit consent via form POST to complete OAuth flow
                    // This avoids going through signin which triggers OIDC auth check
                    // Using form submission (not HTTP client) so browser handles 302 redirects
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
                    document.body.removeChild(form);
                },
                error: (error) => {
                    this.isSubmitting = false;
                    this.errorMessage = error?.error?.error || "Failed to select organisation. Please try again.";
                }
            });
    }
}
