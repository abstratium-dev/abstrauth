import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
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
    imports: [],
    templateUrl: './auth-callback.component.html',
    styleUrl: './auth-callback.component.scss',
})
export class AuthCallbackComponent implements OnInit {

    result = '';
    error = '';

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

                const routeBeforeSignIn = this.authService.getRouteBeforeSignIn();
                this.router.navigate([routeBeforeSignIn])
            }
        );
    }
}
