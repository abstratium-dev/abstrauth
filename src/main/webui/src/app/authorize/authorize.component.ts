import { Component } from '@angular/core';

export const CLIENT_ID = 'abstratium-abstrauth';
export const REDIRECT_URI_POSTFIX = '/auth-callback';

@Component({
    selector: 'authorize',
    imports: [],
    templateUrl: './authorize.component.html',
    styleUrl: './authorize.component.scss',
})
export class AuthorizeComponent {

    ngOnInit(): void {
        this.authorize()
    }

    generateRandomString(length: number) {
        let text = '';
        const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

        for (let i = 0; i < length; i++) {
            text += possible.charAt(Math.floor(Math.random() * possible.length));
        }

        return text;
    }

    async generateCodeChallenge(codeVerifier: string): Promise<string> {
        const encoder = new TextEncoder();
        const data = encoder.encode(codeVerifier);
        const digest = await window.crypto.subtle.digest('SHA-256', data);

        // Convert to base64url format
        const base64 = btoa(String.fromCharCode(...new Uint8Array(digest)));
        return base64
            .replace(/\+/g, '-')
            .replace(/\//g, '_')
            .replace(/=/g, '');
    }

    async authorize() {
        const codeVerifier = this.generateRandomString(128);
        const codeChallenge = await this.generateCodeChallenge(codeVerifier);

        // Generate cryptographically random state for CSRF protection
        const state = this.generateRandomString(32);

        // Store for later validation in callback
        sessionStorage.setItem('code_verifier', codeVerifier);
        sessionStorage.setItem('state', state);

        const protocolHostPort = window.location.protocol + '//' + window.location.host;

        const params = new URLSearchParams({
            response_type: 'code',
            client_id: CLIENT_ID,
            redirect_uri: protocolHostPort + REDIRECT_URI_POSTFIX,
            scope: 'openid profile email',
            state: state,  // Include state parameter for CSRF protection
            code_challenge: codeChallenge,
            code_challenge_method: 'S256'
        });

        window.location.href = `/oauth2/authorize?${params}`;
    }

}
