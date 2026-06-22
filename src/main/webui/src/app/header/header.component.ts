import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit, Signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../auth.service';
import { Controller } from '../controller';
import { ModelService, Organisation } from '../model.service';
import { ThemeService } from '../theme.service';

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    private controller = inject(Controller);
    private modelService = inject(ModelService);
    private router = inject(Router);
    themeService = inject(ThemeService);
    emailMismatchWarning: string | null = null;

    token!: Token;
    isSignedIn = false;
    currentOrg: Signal<Organisation | null> = this.modelService.currentOrganisation$;
    protected brandLogoUrl$ = this.modelService.brandLogoUrl$;
    protected brandLogoAlt$ = this.modelService.brandLogoAlt$;
    protected brandName$ = this.modelService.brandName$;

    constructor() {
        effect(() => {
            this.token = this.authService.token$();
            this.isSignedIn = this.token.isAuthenticated;

            if (this.isSignedIn && this.token.orgId) {
                this.controller.loadCurrentOrganisation();
            } else {
                this.modelService.setCurrentOrganisation(null);
            }
        });
    }

    ngOnInit(): void {
        this.controller.loadConfig();

        // Check for email mismatch warning from invite flow
        const warning = sessionStorage.getItem('emailMismatchWarning');
        if (warning) {
        this.emailMismatchWarning = warning;
        sessionStorage.removeItem('emailMismatchWarning');
        }
    }

    toggleTheme(): void {
        this.themeService.toggleTheme();
    }

    signin() {
        this.router.navigate(['/authorize']);
    }

    signout() {
        this.authService.signout();
    }
}
