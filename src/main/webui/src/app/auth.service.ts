import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router, RoutesRecognized } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { CLIENT_ID } from './authorize/authorize.component';

export const ISSUER = 'https://abstrauth.abstratium.dev';
export const ROLE_ADMIN = 'abstratium-abstrauth_admin';
export const ROLE_MANAGE_CLIENTS = 'abstratium-abstrauth_manage-clients';
export const ROLE_MANAGE_ACCOUNTS = 'abstratium-abstrauth_manage-accounts';

const LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN = 'routeBeforeSignIn';
const defaultRoute = '/accounts';
const ignoredRoutes = ['/signout', '/signin', '/signup', '/?state=', '/authorize'];

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
    private router = inject(Router);
    private http = inject(HttpClient);

    token$ = signal<Token>(ANONYMOUS);
    private token = ANONYMOUS;
    private initialized = false;

    constructor() {
        // Listen to route changes to track previous URL
        this.router.events.pipe(
            tap(event => {
                console.log("TODO received event " + event);
            }),
            filter((event): event is RoutesRecognized => event instanceof RoutesRecognized)
        ).subscribe((event) => {
            if (ignoredRoutes.some(route => event.urlAfterRedirects.startsWith(route))) {
                return;
            }

            // the user has entered a URL which we shall try and respect, after signing in
            this.setRouteBeforeSignIn(event.urlAfterRedirects);
        });
    }
    
    setRouteBeforeSignIn(route: string) {
        console.log("TODO setting route before sign in to " + route);
        localStorage.setItem(LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN, route);
    }

    getRouteBeforeSignIn() {
        return localStorage.getItem(LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN) || defaultRoute;
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
                console.log("TODO initialised, now use route before signin ", this.getRouteBeforeSignIn());
                // this.router.navigateByUrl(this.getRouteBeforeSignIn());
            }),
            catchError((err) => {
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
     * Setup timer to redirect to sign-in when session expires.
     * Redirects 1 minute before actual expiry to ensure smooth UX.
     * Disabled in test environment to prevent test interference.
     */
    private setupTokenExpiryTimer(exp: number): void {
        // Skip timer setup in test environment (Karma/Jasmine)
        if (typeof (window as any).__karma__ !== 'undefined') {
            return;
        }
        
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
        this.resetToken();

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
