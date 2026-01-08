import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { CLIENT_ID } from './authorize/authorize.component';
import { RouteRestorationService } from './route-restoration.service';
import { WINDOW } from './window.token';

export const ISSUER = 'https://abstrauth.abstratium.dev';
export const ROLE_ADMIN = 'abstratium-abstrauth_admin';
export const ROLE_MANAGE_CLIENTS = 'abstratium-abstrauth_manage-clients';
export const ROLE_MANAGE_ACCOUNTS = 'abstratium-abstrauth_manage-accounts';

export interface Token {
    sub: string; // id of the user
    email_verified: boolean;
    iss: string;
    groups: string[];
    isAuthenticated: boolean;
    client_id: string;
    upn: string;
    auth_method: string;
    name: string;
    exp: number; // expires at
    iat: number; // issued at
    email: string;
    jti: string;
}

export const ANONYMOUS: Token = {
    sub: '2354372b-1704-4b88-9d62-b03395e0131c',
    email_verified: false,
    iss: ISSUER,
    groups: [],
    isAuthenticated: false,
    client_id: CLIENT_ID,
    upn: 'anon@abstratium.dev',
    auth_method: 'none',
    name: 'Anonymous',
    exp: Date.now() + 3650 * 24 * 60 * 60 * 1000,
    iat: Date.now(),
    email: 'anon@abstratium.dev',
    jti: 'aeede9a0-3cc3-4536-81c2-5b47a6952abf',
};

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    private http = inject(HttpClient);
    private routeRestoration = inject(RouteRestorationService);
    private window = inject(WINDOW);

    token$ = signal<Token>(ANONYMOUS);
    private token = ANONYMOUS;
    private initialized = false;


    /**
     * Initialize auth service by loading user info from backend.
     * Called by APP_INITIALIZER before app starts.
     * 
     * If user is authenticated (has OIDC session), loads their info.
     * If not authenticated, sets ANONYMOUS token.
     */
    initialize(): Observable<void> {
        console.debug('[AUTH] initialize() called');
        if (this.initialized) {
            console.debug('[AUTH] Already initialized, skipping');
            return of(void 0);
        }

        // Capture the initial URL before any navigation happens
        const initialUrl = this.window.location.pathname + this.window.location.search;
        console.debug('[AUTH] Initial URL captured:', initialUrl);
        
        return this.http.get<Token>('/api/userinfo').pipe(
            tap(token => {
                console.debug('[AUTH] User is authenticated:', token.email);
                this.token = token;
                this.token$.set(token);
                this.initialized = true;
                this.setupTokenExpiryTimer(token.exp);
                
                // Handle post-authentication navigation (includes invite validation)
                // BUT skip navigation if user is on a signin page (OAuth flow in progress)
                if (!initialUrl.startsWith('/signin/')) {
                    this.routeRestoration.handlePostAuthenticationNavigation(initialUrl, token.email);
                } else {
                    console.debug('[AUTH] Skipping route restoration - OAuth signin flow in progress');
                }
            }),
            catchError((err) => {
                console.debug('[AUTH] User is NOT authenticated, error:', err.status);
                // Not authenticated - save the initial URL for later
                this.routeRestoration.saveRoute(initialUrl);
                
                // Use ANONYMOUS token
                this.token = ANONYMOUS;
                this.token$.set(ANONYMOUS);
                this.initialized = true;
                return of(ANONYMOUS);
            }),
            map(() => void 0)
        );
    }


    /**
     * Setup timer to redirect to sign-in when session expires.
     * Redirects 1 minute before actual expiry to ensure smooth UX.
     */
    private setupTokenExpiryTimer(exp: number): void {
        const now = Date.now();
        const expiry = new Date(exp * 1000);
        const millisUntilExpiry = expiry.getTime() - now;
        const oneMinLessThanMillisUntilExpiry = Math.max(0, millisUntilExpiry - (1 * 60 * 1000));
        
        console.debug("Token expires in", millisUntilExpiry, "ms, redirecting to sign-in in", oneMinLessThanMillisUntilExpiry, "ms");
        
        setTimeout(() => {
            console.info("Session expired, redirecting to sign-in");
            this.signout();
        }, oneMinLessThanMillisUntilExpiry);
    }

    getAccessToken() {
        return this.token;
    }

    getEmail() {
        return this.token.email;
    }

    getName() {
        return this.token.name;
    }

    getGroups() {
        return this.token.groups;
    }

    isAuthenticated() {
        return this.token.email !== ANONYMOUS.email;
    }

    isExpired() {
        // exp is in seconds, Date.now() is in milliseconds
        return this.token.exp * 1000 < Date.now();
    }

    isAboutToExpire() {
        // exp is in seconds, Date.now() is in milliseconds
        return this.token.exp * 1000 < Date.now() + 60 * 60 * 1000;
    }

    resetToken() {
        this.token = ANONYMOUS;
        this.token.isAuthenticated = false;
        this.token$.set(this.token);
    }

    signout() {
        console.debug('[AUTH] signout() called');
        this.resetToken();
        this.routeRestoration.saveCurrentRouteBeforeSignout();
        console.debug('[AUTH] Redirecting to logout endpoint');
        this.window.location.href = '/api/auth/logout';
    }

    hasRole(role: string): boolean {
        return this.token.groups.includes(role);
    }

    isAdmin(): boolean {
        return this.hasRole(ROLE_ADMIN);
    }
}
