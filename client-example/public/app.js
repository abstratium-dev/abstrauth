/**
 * Client-side Application Logic (Backend For Frontend Pattern)
 * 
 * In the BFF pattern, the frontend does NOT handle OAuth parameters:
 * 1. User clicks "Sign In" - redirect to /oauth/login (BFF endpoint)
 * 2. BFF generates PKCE parameters and redirects to authorization server
 * 3. User authenticates at authorization server
 * 4. Authorization server redirects to /oauth/callback (BFF endpoint)
 * 5. BFF exchanges code for token and stores in encrypted HTTP-only cookie
 * 6. BFF redirects to / (home page)
 * 7. Frontend loads user info from /api/user (cookie sent automatically)
 * 
 * The frontend NEVER sees or handles:
 * - PKCE parameters (code_verifier, code_challenge)
 * - State parameter
 * - Authorization code
 * - Access tokens
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
     * Initiate OAuth flow - redirect to BFF login endpoint
     * BFF handles all OAuth parameters (PKCE, state, etc.)
     */
    function initiateLogin() {
        // Simply redirect to BFF login endpoint
        // BFF will generate PKCE parameters and redirect to authorization server
        window.location.href = '/oauth/login';
    }

    /**
     * Check for OAuth errors in URL
     * BFF redirects here with error parameters if something went wrong
     */
    function checkForErrors() {
        const params = new URLSearchParams(window.location.search);
        const error = params.get('error');
        
        if (error) {
            const errorDescription = params.get('error_description');
            showError(`Authentication error: ${error} - ${errorDescription || 'No description'}`);
            return true;
        }
        
        return false;
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
                    window.initiateLogin = initiateLogin;
                    loadingEl.innerHTML = '<p>Not authenticated. Click to sign in.</p><button onclick="initiateLogin()">Sign In</button>';
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
    (function init() {
        // Check for OAuth errors in URL
        const hasError = checkForErrors();
        
        // If no errors, fetch user info
        if (!hasError) {
            fetchUserInfo();
        }
    })();
})();
