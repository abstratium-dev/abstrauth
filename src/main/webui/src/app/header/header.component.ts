import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../auth.service';
import { Controller } from '../controller';
import { ThemeService } from '../theme.service';

interface Organisation {
    id: string;
    name: string;
    createdByAccountId: string;
    createdAt: string;
}

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    private controller = inject(Controller);
    private http = inject(HttpClient);
    private router = inject(Router);
    themeService = inject(ThemeService);

    token!: Token;
    isSignedIn = false;
    currentOrg: Organisation | null = null;
    isLoadingOrg = false;
    orgError: string | null = null;

    constructor() {
        effect(() => {
            this.token = this.authService.token$();
            this.isSignedIn = this.token.isAuthenticated;

            // Load organisation when user signs in
            if (this.isSignedIn && this.token.orgId) {
                this.loadCurrentOrganisation();
            } else {
                this.currentOrg = null;
            }
        });
    }

    ngOnInit(): void {
        this.controller.loadConfig();
    }

    loadCurrentOrganisation(): void {
        if (!this.token.orgId) {
            return;
        }

        this.isLoadingOrg = true;
        this.orgError = null;

        this.http.get<Organisation>('/api/organisations/current')
            .subscribe({
                next: (org) => {
                    this.currentOrg = org;
                    this.isLoadingOrg = false;
                },
                error: (error) => {
                    this.isLoadingOrg = false;
                    this.orgError = error?.error?.error || 'Failed to load organisation';
                    console.error('Failed to load current organisation:', error);
                }
            });
    }

    toggleTheme(): void {
        this.themeService.toggleTheme();
    }

    signout() {
        this.authService.signout();
    }

    /**
     * Switch organisation - signs out user so they can sign back in and select a different org
     */
    switchOrganisation(): void {
        // Clear lastOrgId so user gets to select org again on next sign-in
        this.authService.clearLastOrgId();
        // Sign out - user will need to sign back in and select org
        this.authService.signout();
    }

    /**
     * Create new organisation - navigate to user page where org creation is handled
     */
    createNewOrganisation(): void {
        this.router.navigate(['/user']);
    }
}
