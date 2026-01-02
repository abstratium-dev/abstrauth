import { Component, inject } from '@angular/core';
import { WINDOW } from '../window.token';

export const CLIENT_ID = 'abstratium-abstrauth';

/**
 * Authorization component for BFF pattern.
 * 
 * This component redirects to the BFF login endpoint.
 * The backend (Quarkus OIDC) handles the entire OAuth flow:
 * - Detects unauthenticated user
 * - Redirects to /oauth2/authorize with all parameters (response_type=code, PKCE, etc.)
 * 
 * The Angular frontend does NOT handle any OAuth parameters directly.
 */
@Component({
    selector: 'authorize',
    imports: [],
    templateUrl: './authorize.component.html',
    styleUrl: './authorize.component.scss',
})
export class AuthorizeComponent {
    private window = inject(WINDOW);

    ngOnInit(): void {
        this.authorize()
    }

    /**
     * Redirect to BFF login endpoint.
     * This triggers the OAuth flow via Quarkus OIDC.
     */
    authorize() {
        // Redirect to the BFF login endpoint
        // Quarkus OIDC will detect we're not authenticated and redirect to /oauth2/authorize
        // with all required OAuth parameters automatically added
        this.window.location.href = '/api/auth/login';
    }

}
