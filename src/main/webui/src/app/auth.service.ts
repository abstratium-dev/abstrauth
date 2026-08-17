import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { CLIENT_ID } from './authorize/authorize.component';
import { RouteRestorationService } from './route-restoration.service';
import { ToastService } from './shared/toast/toast.service';
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
    orgId?: string; // organisation ID (tenantId)
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
    private toastService = inject(ToastService);
    private window = inject(WINDOW);

    token$ = signal<Token>(ANONYMOUS);
    /**
     * Fraction of the session remaining: 1.0 = just logged in, 0.0 = expired.
     * Drives the session-clock ring in the header.
     */
    sessionFraction$ = signal<number>(0);
    /**
     * Whole minutes remaining until sign-out (rounded up). Used for the
     * session-clock tooltip/aria-label.
     */
    sessionMinutesRemaining$ = signal<number>(0);
    private token = ANONYMOUS;
    private initialized = false;
    private signoutWarningTimer: ReturnType<typeof setTimeout> | null = null;
    private sessionClockInterval: ReturnType<typeof setInterval> | null = null;


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
                this.setupTokenExpiryTimer(token);
                
                // Handle post-authentication navigation (includes invite validation)
                // BUT skip navigation if user is on a signin or org-selection page (OAuth flow in progress)
                if (!initialUrl.startsWith('/signin/') && !initialUrl.startsWith('/org-selection/')) {
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
     *
     * A warning toast is shown 2 minutes before signout() fires, i.e. at
     * exp - 3min. If there isn't a full 2-minute warning window before
     * sign-out, the warning is skipped entirely (a setTimeout with a 0/negative
     * delay would fire immediately, which is misleading for short-lived
     * sessions where sign-out is imminent anyway).
     *
     * Also drives the session-clock ring in the header by recomputing
     * {@link sessionFraction$} and {@link sessionMinutesRemaining$} on a
     * 1-second interval. The fraction is `millisUntilExpiry / totalDuration`
     * where `totalDuration = (exp - iat) * 1000`.
     */
    private setupTokenExpiryTimer(token: Token): void {
        const now = Date.now();
        const expiry = new Date(token.exp * 1000);
        const millisUntilExpiry = expiry.getTime() - now;
        const oneMinLessThanMillisUntilExpiry = Math.max(0, millisUntilExpiry - (1 * 60 * 1000));

        console.debug("Token expires in", millisUntilExpiry, "ms, redirecting to sign-in in", oneMinLessThanMillisUntilExpiry, "ms");

        setTimeout(() => {
            console.info("Session expired, redirecting to sign-in");
            this.signout();
        }, oneMinLessThanMillisUntilExpiry);

        // Show a warning toast 2 minutes before signout() fires.
        // signout() fires at exp - 1min, so the warning fires at exp - 3min.
        const signoutWarningDelay = millisUntilExpiry - (3 * 60 * 1000);
        if (signoutWarningDelay > 0) {
            this.signoutWarningTimer = setTimeout(() => {
                this.toastService.warning('You will be signed out in 2 minutes.');
                this.signoutWarningTimer = null;
            }, signoutWarningDelay);
        }

        // Drive the session-clock ring. totalDuration is the full session
        // lifetime (exp - iat) in ms; the fraction shrinks from 1 -> 0 as the
        // session approaches expiry.
        this.clearSessionClockInterval();
        const totalDuration = Math.max(1, (token.exp - token.iat) * 1000);
        const tick = () => {
            const millisLeft = (token.exp * 1000) - Date.now();
            const fraction = Math.max(0, Math.min(1, millisLeft / totalDuration));
            const minutesRemaining = Math.max(0, Math.ceil(millisLeft / (60 * 1000)));
            this.sessionFraction$.set(fraction);
            this.sessionMinutesRemaining$.set(minutesRemaining);
        };
        tick();
        this.sessionClockInterval = setInterval(tick, 1000);
    }

    private clearSessionClockInterval(): void {
        if (this.sessionClockInterval) {
            clearInterval(this.sessionClockInterval);
            this.sessionClockInterval = null;
        }
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
        // Cancel any pending sign-out warning toast so a user who signs out
        // manually doesn't get a stale warning popping up later.
        if (this.signoutWarningTimer) {
            clearTimeout(this.signoutWarningTimer);
            this.signoutWarningTimer = null;
        }
        this.clearSessionClockInterval();
        this.sessionFraction$.set(0);
        this.sessionMinutesRemaining$.set(0);
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

    /**
     * Get the last selected organisation ID from localStorage.
     * Returns null if not set or if localStorage is not available.
     */
    getLastOrgId(): string | null {
        try {
            if (typeof localStorage === 'undefined') {
                return null;
            }
            return localStorage.getItem('lastOrgId');
        } catch {
            return null;
        }
    }

    /**
     * Store the selected organisation ID in localStorage.
     */
    setLastOrgId(orgId: string): void {
        try {
            if (typeof localStorage === 'undefined') {
                return;
            }
            localStorage.setItem('lastOrgId', orgId);
        } catch {
            // Ignore localStorage errors
        }
    }

    /**
     * Get the current organisation ID from the token.
     * Returns undefined if not authenticated or no orgId in token.
     */
    getOrgId(): string | undefined {
        return this.token.orgId;
    }
}
