import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit, Signal, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../auth.service';
import { Controller } from '../controller';
import { ModelService, Organisation } from '../model.service';
import { ThemeService } from '../theme.service';

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    private controller = inject(Controller);
    private modelService = inject(ModelService);
    private router = inject(Router);
    themeService = inject(ThemeService);
    emailMismatchWarning: string | null = null;

    currentOrg: Signal<Organisation | null> = this.modelService.currentOrganisation$;
    protected brandLogoUrl$ = this.modelService.brandLogoUrl$;
    protected brandLogoAlt$ = this.modelService.brandLogoAlt$;
    protected brandName$ = this.modelService.brandName$;

    get token(): Token {
        return this.authService.token$();
    }

    get isSignedIn(): boolean {
        return this.token.isAuthenticated;
    }

    // --- Session-clock ring ---
    // Radius of the progress ring in SVG user units. Must match the
    // <circle r="..."> in the template and the stroke-dasharray in the SCSS.
    private readonly sessionClockRadius = 7;

    get sessionFraction(): number {
        return this.authService.sessionFraction$();
    }

    get sessionMinutesRemaining(): number {
        return this.authService.sessionMinutesRemaining$();
    }

    /**
     * stroke-dashoffset for the fill circle. 0 = full ring drawn,
     * circumference = empty ring. Driven by the fraction of session remaining.
     */
    get sessionClockDashoffset(): number {
        const circumference = 2 * Math.PI * this.sessionClockRadius;
        return circumference * (1 - this.sessionFraction);
    }

    constructor() {
        effect(() => {
            const token = this.authService.token$();
            if (token.isAuthenticated && token.orgId) {
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
