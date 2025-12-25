/**
 * Example OAuth 2.0 Client Application (Stateless)
 * 
 * This server demonstrates how to integrate with the Abstrauth authorization server
 * using the Authorization Code Flow with PKCE (Proof Key for Code Exchange).
 * 
 * Flow:
 * 1. User visits / - Browser loads SPA
 * 2. Browser generates PKCE parameters (code_verifier, state) and stores in sessionStorage
 * 3. Browser redirects to authorization server with code_challenge
 * 4. User authenticates and grants consent
 * 5. Authorization server redirects back to / with authorization code
 * 6. Browser sends code + code_verifier to server /api/token endpoint
 * 7. Server exchanges code for access token and returns it in HTTP-only cookie
 * 8. Browser can now call /api/user with cookie to get user info
 * 
 * IMPORTANT: This server is STATELESS - no sessions, no in-memory storage.
 * The access token is stored in an HTTP-only cookie for security.
 */

const express = require('express');
const cookieParser = require('cookie-parser');
const crypto = require('crypto');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3333;

// Configuration - these should match your OAuth client registration
const CLIENT_ID = process.env.CLIENT_ID || 'test-oauth-client';
const REDIRECT_URI = process.env.REDIRECT_URI || 'http://localhost:3333';
const AUTHORIZATION_ENDPOINT = process.env.AUTHORIZATION_ENDPOINT || 'http://localhost:8080/oauth2/authorize';
const TOKEN_ENDPOINT = process.env.TOKEN_ENDPOINT || 'http://localhost:8080/oauth2/token';
const SCOPE = process.env.SCOPE || 'openid profile email';

app.use(cookieParser());
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

/**
 * Generate a cryptographically secure random string for PKCE
 * @param {number} length - Length of the string (43-128 characters for PKCE)
 * @returns {string} Base64URL-encoded random string
 */
function generateRandomString(length) {
    const bytes = crypto.randomBytes(length);
    return base64URLEncode(bytes);
}

/**
 * Base64URL encode (without padding)
 * @param {Buffer} buffer - Buffer to encode
 * @returns {string} Base64URL-encoded string
 */
function base64URLEncode(buffer) {
    return buffer.toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=/g, '');
}

/**
 * Generate PKCE code challenge from code verifier
 * @param {string} codeVerifier - The code verifier
 * @returns {string} Base64URL-encoded SHA256 hash
 */
function generateCodeChallenge(codeVerifier) {
    const hash = crypto.createHash('sha256').update(codeVerifier).digest();
    return base64URLEncode(hash);
}

/**
 * Home page - serves index.html page
 * The browser handles OAuth flow client-side
 */
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

/**
 * OAuth configuration endpoint
 * Returns configuration for browser-based OAuth flow
 */
app.get('/api/oauth-config', (req, res) => {
    res.json({
        client_id: CLIENT_ID,
        redirect_uri: REDIRECT_URI,
        authorization_endpoint: AUTHORIZATION_ENDPOINT,
        scope: SCOPE
    });
});

/**
 * Token exchange endpoint
 * Browser sends authorization code + code_verifier, server exchanges for token
 * and returns it in an HTTP-only cookie
 */
app.post('/api/token', async (req, res) => {
    const { code, code_verifier } = req.body;

    if (!code || !code_verifier) {
        return res.status(400).json({ error: 'Missing code or code_verifier' });
    }

    try {
        // Exchange authorization code for access token
        const tokenParams = new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: REDIRECT_URI,
            client_id: CLIENT_ID,
            code_verifier: code_verifier
        });

        console.log('Exchanging code for token at:', TOKEN_ENDPOINT);

        const tokenResponse = await fetch(TOKEN_ENDPOINT, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: tokenParams.toString()
        });

        if (!tokenResponse.ok) {
            const errorText = await tokenResponse.text();
            console.error('Token exchange failed:', tokenResponse.status, errorText);
            return res.status(tokenResponse.status).json({ 
                error: 'token_exchange_failed',
                error_description: errorText 
            });
        }

        const tokens = await tokenResponse.json();
        console.log('Successfully obtained tokens');

        // Store access token in HTTP-only cookie
        res.cookie('access_token', tokens.access_token, {
            httpOnly: true,
            secure: process.env.NODE_ENV === 'production', // HTTPS only in production
            sameSite: 'lax',
            maxAge: tokens.expires_in * 1000 // Convert seconds to milliseconds
        });

        // Optionally store refresh token in HTTP-only cookie
        if (tokens.refresh_token) {
            res.cookie('refresh_token', tokens.refresh_token, {
                httpOnly: true,
                secure: process.env.NODE_ENV === 'production',
                sameSite: 'lax',
                maxAge: 30 * 24 * 60 * 60 * 1000 // 30 days
            });
        }

        // Return success (no token in response body)
        res.json({ success: true });
    } catch (error) {
        console.error('Error during token exchange:', error);
        res.status(500).json({ 
            error: 'server_error',
            error_description: error.message 
        });
    }
});

/**
 * API endpoint to get current user info
 * Reads access token from HTTP-only cookie and parses JWT claims
 */
app.get('/api/user', (req, res) => {
    const accessToken = req.cookies.access_token;

    if (!accessToken) {
        return res.status(401).json({ error: 'Not authenticated' });
    }

    try {
        // Parse JWT to get user info (for demo purposes - in production, validate signature)
        const tokenParts = accessToken.split('.');
        if (tokenParts.length !== 3) {
            return res.status(401).json({ error: 'Invalid token format' });
        }

        const payload = JSON.parse(Buffer.from(tokenParts[1], 'base64').toString());
        
        // Check if token is expired
        if (payload.exp && payload.exp < Date.now() / 1000) {
            res.clearCookie('access_token');
            return res.status(401).json({ error: 'Token expired' });
        }

        // Return user info from JWT claims
        res.json({
            user: {
                sub: payload.sub,
                email: payload.email,
                name: payload.name,
                groups: payload.groups
            }
        });
    } catch (error) {
        console.error('Error parsing token:', error);
        res.status(401).json({ error: 'Invalid token' });
    }
});

/**
 * Logout endpoint
 * Clears HTTP-only cookies
 */
app.post('/api/logout', (req, res) => {
    res.clearCookie('access_token');
    res.clearCookie('refresh_token');
    res.json({ success: true });
});

// Serve static files from public directory (must be AFTER route definitions)
app.use(express.static(path.join(__dirname, 'public')));

// Start server
app.listen(PORT, () => {
    console.log(`Example OAuth client running on http://localhost:${PORT}`);
    console.log(`Client ID: ${CLIENT_ID}`);
    console.log(`Redirect URI: ${REDIRECT_URI}`);
    console.log(`Authorization Endpoint: ${AUTHORIZATION_ENDPOINT}`);
    console.log(`Token Endpoint: ${TOKEN_ENDPOINT}`);
});
