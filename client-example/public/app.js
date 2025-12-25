/**
 * Client-side OAuth 2.0 Application Logic
 * 
 * This handles the complete OAuth flow in the browser:
 * 1. Generate PKCE parameters (code_verifier, code_challenge) and state
 * 2. Store code_verifier and state in sessionStorage
 * 3. Redirect to authorization server
 * 4. Handle callback with authorization code
 * 5. Exchange code for token via server endpoint
 * 6. Display user info from token in HTTP-only cookie
 */

(function () {
    'use strict';

    // DOM elements
    const loadingEl = document.getElementById('loading');
    const errorEl = document.getElementById('error');
    const contentEl = document.getElementById('content');

    // User info elements
    const userNameEl = document.getElementById('user-name');
    const userEmailEl = document.getElementById('user-email');
    const userSubEl = document.getElementById('user-sub');
    const userGroupsEl = document.getElementById('user-groups');

    /**
     * Generate a cryptographically random string
     */
    function generateRandomString(length) {
        const array = new Uint8Array(length);
        crypto.getRandomValues(array);
        return base64URLEncode(array);
    }

    /**
     * Base64URL encode (without padding)
     */
    function base64URLEncode(buffer) {
        const base64 = btoa(String.fromCharCode(...buffer));
        return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    }

    /**
     * Generate PKCE code challenge from code verifier
     */
    async function generateCodeChallenge(codeVerifier) {
        const encoder = new TextEncoder();
        const data = encoder.encode(codeVerifier);
        const hash = await crypto.subtle.digest('SHA-256', data);
        return base64URLEncode(new Uint8Array(hash));
    }

    /**
     * Initiate OAuth flow - generate PKCE params and redirect
     */
    async function initiateLogin() {
        try {
            // Fetch OAuth configuration from server
            const configResponse = await fetch('/api/oauth-config');
            const config = await configResponse.json();

            // Generate PKCE parameters
            const codeVerifier = generateRandomString(32);
            const codeChallenge = await generateCodeChallenge(codeVerifier);
            const state = generateRandomString(16);

            // Store in sessionStorage (short-lived, cleared after token exchange)
            sessionStorage.setItem('code_verifier', codeVerifier);
            sessionStorage.setItem('state', state);

            // Build authorization URL
            const authUrl = new URL(config.authorization_endpoint);
            authUrl.searchParams.set('response_type', 'code');
            authUrl.searchParams.set('client_id', config.client_id);
            authUrl.searchParams.set('redirect_uri', config.redirect_uri);
            authUrl.searchParams.set('scope', config.scope);
            authUrl.searchParams.set('state', state);
            authUrl.searchParams.set('code_challenge', codeChallenge);
            authUrl.searchParams.set('code_challenge_method', 'S256');

            // Redirect to authorization server
            window.location.href = authUrl.toString();
        } catch (error) {
            console.error('Error initiating login:', error);
            showError('Failed to initiate login: ' + error.message);
        }
    }

    /**
     * Handle OAuth callback - exchange code for token
     */
    async function handleCallback() {
        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        const state = params.get('state');
        const error = params.get('error');

        // Check for errors from authorization server
        if (error) {
            const errorDescription = params.get('error_description');
            showError(`Authorization error: ${error} - ${errorDescription || 'No description'}`);
            return;
        }

        if (!code) {
            // No code in URL, not a callback
            return false;
        }

        // Validate state parameter (CSRF protection)
        const storedState = sessionStorage.getItem('state');
        if (state !== storedState) {
            showError('Invalid state parameter - possible CSRF attack');
            return true;
        }

        // Get code_verifier from sessionStorage
        const codeVerifier = sessionStorage.getItem('code_verifier');
        if (!codeVerifier) {
            showError('Missing code_verifier - please try logging in again');
            return true;
        }

        try {
            // Exchange code for token via server endpoint
            const response = await fetch('/api/token', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    code: code,
                    code_verifier: codeVerifier
                })
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error_description || errorData.error);
            }

            // Clear PKCE parameters from sessionStorage
            sessionStorage.removeItem('code_verifier');
            sessionStorage.removeItem('state');

            // Redirect to home (clean URL, token is in HTTP-only cookie)
            window.location.href = '/';
        } catch (error) {
            console.error('Error exchanging code for token:', error);
            showError('Failed to exchange code for token: ' + error.message);
        }

        return true;
    }

    /**
     * Fetch user information from the server
     * Server reads token from HTTP-only cookie
     */
    async function fetchUserInfo() {
        try {
            const response = await fetch('/api/user');

            if (!response.ok) {
                if (response.status === 401) {
                    // Not authenticated - initiate login
                    loadingEl.textContent = 'Not authenticated. Redirecting to login...';
                    setTimeout(initiateLogin, 1000);
                    return;
                }
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const data = await response.json();
            displayUserInfo(data);
        } catch (error) {
            console.error('Error fetching user info:', error);
            showError('Failed to load user information: ' + error.message);
        }
    }

    /**
     * Display user information in the UI
     */
    function displayUserInfo(data) {
        // Hide loading, show content
        loadingEl.style.display = 'none';
        contentEl.style.display = 'block';

        // Populate user info
        if (data.user) {
            userNameEl.textContent = data.user.name || 'N/A';
            userEmailEl.textContent = data.user.email || 'N/A';
            userSubEl.textContent = data.user.sub || 'N/A';
            userGroupsEl.textContent = data.user.groups || 'N/A';
        }
    }

    /**
     * Show error message
     */
    function showError(message) {
        loadingEl.style.display = 'none';
        errorEl.textContent = message;
        errorEl.style.display = 'block';
    }

    /**
     * Handle logout
     */
    async function logout() {
        try {
            await fetch('/api/logout', { method: 'POST' });
            window.location.href = '/';
        } catch (error) {
            console.error('Logout error:', error);
        }
    }

    // Make logout available globally
    window.logout = logout;

    // Initialize
    (async function init() {
        // Check if this is an OAuth callback
        const isCallback = await handleCallback();
        
        // If not a callback, fetch user info
        if (!isCallback) {
            fetchUserInfo();
        }
    })();
})();
