import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { CLIENT_ID } from './authorize/authorize.component';

export const ISSUER = 'https://abstrauth.abstratium.dev';
export const ROLE_ADMIN = 'abstratium-abstrauth_admin';
export const ROLE_MANAGE_CLIENTS = 'abstratium-abstrauth_manage-clients';
export const ROLE_MANAGE_ACCOUNTS = 'abstratium-abstrauth_manage-accounts';

const LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN = 'routeBeforeSignIn';
const defaultRoute = '/accounts';
const ignoredRoutes = ['/signout', '/signin', '/signup', '/authorize'];

export interface Token {
    iss: string;
    sub: string; // id of the user
    groups: string[];
    email: string;
    email_verified: boolean;
    name: string;
    scope: string;
    iat: number; // issued at
    exp: number; // expires at
    isAuthenticated: boolean;
    client_id: string;
    jti: string;
    upn: string;
    auth_method: string;
}

export const ANONYMOUS: Token = {
    iss: ISSUER,
    sub: '2354372b-1704-4b88-9d62-b03395e0131c',
    groups: [],
    email: 'anon@abstratium.dev',
    email_verified: false,
    name: 'Anonymous',
    scope: '',
    iat: Date.now(),
    exp: Date.now() + 3650 * 24 * 60 * 60 * 1000,
    isAuthenticated: false,
    client_id: CLIENT_ID,
    jti: 'aeede9a0-3cc3-4536-81c2-5b47a6952abf',
    upn: 'anon@abstratium.dev',
    auth_method: 'none',
};

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    private router = inject(Router);
    private http = inject(HttpClient);

    token$ = signal<Token>(ANONYMOUS);
    private token = ANONYMOUS;
    private routeBeforeSignIn = defaultRoute;
    private currentUrl: string = defaultRoute;
    private initialized = false;

    constructor() {
        this.routeBeforeSignIn = localStorage.getItem(LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN) || defaultRoute;
        
        // Listen to route changes to track previous URL
        this.router.events.pipe(
            filter((event): event is NavigationEnd => event instanceof NavigationEnd)
        ).subscribe((event) => {
            if (ignoredRoutes.some(route => event.urlAfterRedirects.startsWith(route))) {
                return;
            }
            this.currentUrl = event.urlAfterRedirects;
        });
    }

    setRouteBeforeSignIn(route: string) {
        this.routeBeforeSignIn = route;
        localStorage.setItem(LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN, route);
    }

    getRouteBeforeSignIn() {
        return this.routeBeforeSignIn;
    }

    /**
     * Initialize auth service by loading user info from backend.
     * Called by APP_INITIALIZER before app starts.
     * 
     * If user is authenticated (has OIDC session), loads their info.
     * If not authenticated, sets ANONYMOUS token.
     */
    initialize(): Observable<void> {
        if (this.initialized) {
            return of(void 0);
        }

        return this.http.get<Token>('/api/userinfo').pipe(
            tap(token => {
                this.token = token;
                this.token$.set(token);
                this.initialized = true;
                this.setupTokenExpiryTimer(token.exp);
            }),
            catchError(() => {
                // Not authenticated - use ANONYMOUS token
                this.token = ANONYMOUS;
                this.token$.set(ANONYMOUS);
                this.initialized = true;
                return of(ANONYMOUS);
            }),
            map(() => void 0)
        );
    }


    /**
     * Setup timer to reset token 1 minute before expiry.
     */
    private setupTokenExpiryTimer(exp: number): void {
        const now = Date.now();
        const expiry = new Date(exp * 1000);
        const millisUntilExpiry = expiry.getTime() - now;
        const oneMinLessThanMillisUntilExpiry = Math.max(0, millisUntilExpiry - (1 * 60 * 1000));
        
        console.debug("Token expires in", millisUntilExpiry, "ms, resetting in", oneMinLessThanMillisUntilExpiry, "ms");
        
        setTimeout(() => {
            this.resetToken();
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

    hasScope(scope: string) {
        let scopes = this.token.scope.split(' ');
        return scopes.includes(scope);
    }

    isAuthenticated() {
        return this.token.email !== ANONYMOUS.email;
    }

    isExpired() {
        return this.token.exp < Date.now();
    }

    isAboutToExpire() {
        return this.token.exp < Date.now() + 60 * 60 * 1000;
    }

    resetToken() {
        this.token = ANONYMOUS;
        this.token.isAuthenticated = false;
        this.token$.set(this.token);
    }

    signout() {
        this.setRouteBeforeSignIn(this.currentUrl);

        // Call OIDC logout endpoint which clears HTTP-only cookies
        // and redirects to / (configured in application.properties)
        window.location.href = '/api/auth/logout';
    }

    hasRole(role: string): boolean {
        return this.token.groups.includes(role);
    }

    isAdmin(): boolean {
        return this.hasRole(ROLE_ADMIN);
    }
}
