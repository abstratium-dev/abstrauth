import { Injectable, signal } from '@angular/core';

export interface Token {
    issuer: string;
    subject: string; // id of the user
    groups: string[];
    email: string;
    name: string;
    email_verified: boolean;
    scope: string;
    iat: number; // issued at
    exp: number; // expires at
    isAuthenticated: boolean;
}

export const ANONYMOUS: Token = {
    issuer: 'https://abstrauth.abstratium.dev',
    subject: '2354372b-1704-4b88-9d62-b03395e0131c',
    groups: [],
    email: 'anon@abstratium.dev',
    name: 'Anonymous',
    email_verified: false,
    scope: '',
    iat: Date.now(),
    exp: Date.now() + 3650 * 24 * 60 * 60 * 1000,
    isAuthenticated: false,
};


@Injectable({
    providedIn: 'root',
})
export class AuthService {

    token$ = signal<Token>(ANONYMOUS);
    private token = ANONYMOUS;
    private jwt = '';
    private routeBeforeSignIn = '/';

    setRouteBeforeSignIn(route: string) {
        this.routeBeforeSignIn = route;
    }

    getRouteBeforeSignIn() {
        return this.routeBeforeSignIn;
    }

    getAccessToken() {
        return this.token;
    }

    setAccessToken(jwt: string) {

        console.info("setAccessToken")

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

        // set a timer to refresh the token, 5 mins before expiry
        let now = Date.now();
        let expiry = new Date(this.token.exp * 1000);
        let millisUntilExpiry = expiry.getTime() - now;
        let fiveMinsLessThanMillisUntilExpiry = Math.max(0, millisUntilExpiry - (5 * 60 * 1000));
        console.debug("refreshing in ", fiveMinsLessThanMillisUntilExpiry, "ms")
        setTimeout(() => {
            this.refreshToken();
        }, fiveMinsLessThanMillisUntilExpiry);

        console.info("setAccessToken for ", this.token.email)
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

    refreshToken() {
        // TODO actually refresh it using the refresh token
        // for the time being, set it to anon
        this.token = ANONYMOUS;
        this.token.isAuthenticated = false;
        this.token$.set(this.token);
    }

    signout() {
        // TODO call back end
        this.token = ANONYMOUS;
        this.token.isAuthenticated = false;
        this.token$.set(this.token);
    }
}
