import { Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { CLIENT_ID } from './authorize/authorize.component';

export const ISSUER = 'https://abstrauth.abstratium.dev';
export const ROLE_ADMIN = 'abstratium-abstrauth_admin';
export const ROLE_MANAGE_CLIENTS = 'abstratium-abstrauth_manage-clients';
export const ROLE_MANAGE_ACCOUNTS = 'abstratium-abstrauth_manage-accounts';

const LOCALSTORAGE_KEY_ROUTE_BEFORE_SIGN_IN = 'routeBeforeSignIn';
const defaultRoute = '/accounts';
const ignoredRoutes = ['/signout', '/signin', '/signup', '/auth-callback', '/authorize'];

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

    token$ = signal<Token>(ANONYMOUS);
    private token = ANONYMOUS;
    private jwt = ''; // ok to keep in memory: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26#name-in-memory-token-storage
    private routeBeforeSignIn = defaultRoute;
    private currentUrl: string = defaultRoute;

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

    getAccessToken() {
        return this.token;
    }

    setAccessToken(jwt: string) {

        this.jwt = jwt;

        // convert jwt to token
        var base64Url = jwt.split('.')[1];
        var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        var jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        this.token = JSON.parse(jsonPayload);
        this.token.isAuthenticated = true;
        this.token$.set(this.token);

        // set a timer to reset the token, 1 min before expiry
        let now = Date.now();
        let expiry = new Date(this.token.exp * 1000);
        let millisUntilExpiry = expiry.getTime() - now;
        let oneMinLessThanMillisUntilExpiry = Math.max(0, millisUntilExpiry - (1 * 60 * 1000));
        console.debug("resetting in ", oneMinLessThanMillisUntilExpiry, "ms")
        setTimeout(() => {
            this.resetToken();
        }, oneMinLessThanMillisUntilExpiry);
    }

    getJwt() {
        return this.jwt;
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

        // TODO call back end, except... it's stateless so there is no point

        // Defer token update to avoid ExpressionChangedAfterItHasBeenCheckedError
        // This ensures the signal update happens after the current change detection cycle
        setTimeout(() => {
            this.token = ANONYMOUS;
            this.token.isAuthenticated = false;
            this.token$.set(this.token);

            this.router.navigate(['/']);
        }, 0);
    }

    hasRole(role: string): boolean {
        return this.token.groups.includes(role);
    }

    isAdmin(): boolean {
        return this.hasRole(ROLE_ADMIN);
    }
}
