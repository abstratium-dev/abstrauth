import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CLIENT_ID, REDIRECT_URI_POSTFIX } from '../authorize/authorize.component';
import { HttpClient } from '@angular/common/http';
import { HttpHeaders } from '@angular/common/http';
import { AuthService } from '../auth.service';

interface TokenResponse {
    access_token: string;
    token_type: string;
    expires_in: number;
    refresh_token: string;
    scope: string;
}

@Component({
    selector: 'auth-callback',
    imports: [RouterLink],
    templateUrl: './auth-callback.component.html',
    styleUrl: './auth-callback.component.scss',
})
export class AuthCallbackComponent implements OnInit {

    error: string | null = null;
    result: string | null = null;
    emailMismatchWarning: string | null = null;
    redirecting = false;

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
        private authService: AuthService,
        private router: Router
    ) {
    }

    ngOnInit(): void {
        this.exchangeToken();
    }

    exchangeToken() {
        // check if there are any errors in the query param
        const error = this.route.snapshot.queryParamMap.get('error');
        const errorDescription = this.route.snapshot.queryParamMap.get('error_description');
        if (error) {
            this.error = `Error: ${error}`;
            if(errorDescription) {
                this.error += ` - ${errorDescription}`;
            }
            return;
        }

        // CRITICAL: Validate state parameter to prevent CSRF attacks
        const receivedState = this.route.snapshot.queryParamMap.get('state');
        const storedState = sessionStorage.getItem('state');
        
        if (!receivedState || !storedState || receivedState !== storedState) {
            this.error = 'Security Error: Invalid state parameter. Possible CSRF attack detected.';
            console.error('CSRF Protection: State mismatch', { received: receivedState, stored: storedState });
            // Clear stored values
            sessionStorage.removeItem('state');
            sessionStorage.removeItem('code_verifier');
            return;
        }

        // Clear state after successful validation
        sessionStorage.removeItem('state');

        // code comes out of the query param
        const code = this.route.snapshot.queryParamMap.get('code') || '';

        const storedVerifier = sessionStorage.getItem('code_verifier') || '';
        if (storedVerifier) {
            // Clear it after use for security
            sessionStorage.removeItem('code_verifier');
        }

        const redirectUri = window.location.origin + REDIRECT_URI_POSTFIX;

        const headers = new HttpHeaders({
            'Content-Type': 'application/x-www-form-urlencoded'
        });

        const params = new URLSearchParams();
        params.append('grant_type', 'authorization_code');
        params.append('code', code);
        params.append('client_id', CLIENT_ID);
        params.append('redirect_uri', redirectUri);
        params.append('code_verifier', storedVerifier);
        this.http.post<TokenResponse>('/oauth2/token', params.toString(), { headers }).subscribe(
            (response: TokenResponse) => {
                // set the refresh token as a cookie that expires in 1 day
                // it should be http only, and secure if the current protocol is https
                const cookie = response.refresh_token;
                const expires = new Date(Date.now() + 24 * 60 * 60 * 1000).toUTCString();
                document.cookie = `refresh_token=${cookie}; expires=${expires}; path=/; samesite=lax; ${window.location.protocol === 'https:' ? 'secure' : ''}`;

                this.authService.setAccessToken(response.access_token);

                // Check if password change is required (for native invite flow)
                const requirePasswordChange = sessionStorage.getItem('requirePasswordChange');
                if (requirePasswordChange) {
                    this.router.navigate(['/change-password']);
                    return;
                }

                // Check for email mismatch with invite
                const inviteDataStr = sessionStorage.getItem('inviteData');
                try {
                    if (inviteDataStr) {
                        const inviteData = JSON.parse(inviteDataStr);
                        const tokenEmail = this.authService.getEmail();
                        
                        if (tokenEmail && inviteData.email && tokenEmail !== inviteData.email) {
                            // Email mismatch - show warning and stay on this page
                            this.emailMismatchWarning = `You signed in with ${tokenEmail}, but the invite was for ${inviteData.email}. The invitation has not been applied.`;
                            sessionStorage.removeItem('inviteData');
                            this.redirecting = true;
                            // Redirect after showing warning for 10 seconds
                            setTimeout(() => {
                                this.redirect();
                            }, 10_000);
                            return;
                        }
                    }
                    // If no invite data or emails match, redirect immediately
                    this.redirect();
                } catch (e) {
                    console.error('Error parsing invite data:', e);
                    this.redirect();
                } finally {
                    // Clear invite data if present
                    sessionStorage.removeItem('inviteData');
                }
            }
        );
    }

    private redirect(): void {
        const routeBeforeSignIn = this.authService.getRouteBeforeSignIn();
        if(routeBeforeSignIn) {
            this.router.navigateByUrl(routeBeforeSignIn);
        } else {
            this.router.navigate(['/accounts']);
        }
    }
}
