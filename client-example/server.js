/**
 * Example OAuth 2.0 Client Application (Backend For Frontend Pattern)
 * 
 * This server demonstrates how to integrate with the Abstrauth authorization server
 * using the Backend For Frontend (BFF) pattern with Authorization Code Flow + PKCE.
 * 
 * BFF Pattern Flow:
 * 1. User visits / - Browser loads SPA
 * 2. SPA redirects to /oauth/login to initiate OAuth flow
 * 3. BFF generates PKCE parameters (code_verifier, state) and stores in server session
 * 4. BFF redirects browser to authorization server with code_challenge
 * 5. User authenticates and grants consent at authorization server
 * 6. Authorization server redirects back to /oauth/callback with authorization code
 * 7. BFF exchanges code + code_verifier + client_secret for access token
 * 8. BFF stores access token in encrypted HTTP-only cookie
 * 9. BFF redirects browser to / (home page)
 * 10. Browser can now call /api/user with cookie to get user info
 * 
 * IMPORTANT: This is a CONFIDENTIAL CLIENT using the BFF pattern.
 * - PKCE parameters generated and stored by the backend (not browser)
 * - client_secret used in token exchange
 * - Tokens stored in encrypted HTTP-only cookies (never exposed to JavaScript)
 * - Session storage used for PKCE parameters during OAuth flow
 */

const express = require('express');
const session = require('express-session');
const cookieParser = require('cookie-parser');
const crypto = require('crypto');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3333;

// Configuration - these should match your OAuth client registration
let CLIENT_ID = process.env.CLIENT_ID || 'abstratium-abstrauth';
let CLIENT_SECRET = process.env.CLIENT_SECRET || 'dev-secret-CHANGE-IN-PROD';
const REDIRECT_URI = process.env.REDIRECT_URI || 'http://localhost:3333/oauth/callback';
const AUTHORIZATION_ENDPOINT = process.env.AUTHORIZATION_ENDPOINT || 'http://localhost:8080/oauth2/authorize';
const TOKEN_ENDPOINT = process.env.TOKEN_ENDPOINT || 'http://localhost:8080/oauth2/token';
const SCOPE = process.env.SCOPE || 'openid profile email';
const SESSION_SECRET = process.env.SESSION_SECRET || 'dev-session-secret-CHANGE-IN-PROD';
const COOKIE_SECRET = process.env.COOKIE_SECRET || 'dev-cookie-secret-CHANGE-IN-PROD';

// Session middleware for storing PKCE parameters during OAuth flow
app.use(session({
    secret: SESSION_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: {
        secure: process.env.NODE_ENV === 'production',
        httpOnly: true,
        maxAge: 10 * 60 * 1000 // 10 minutes
    }
}));

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
 * Encrypt token using AES-256-GCM
 * @param {string} token - Token to encrypt
 * @returns {string} Encrypted token (base64)
 */
function encryptToken(token) {
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-gcm', Buffer.from(COOKIE_SECRET.padEnd(32, '0').substring(0, 32)), iv);
    
    let encrypted = cipher.update(token, 'utf8', 'base64');
    encrypted += cipher.final('base64');
    
    const authTag = cipher.getAuthTag();
    
    // Return: iv + authTag + encrypted (all base64)
    return `${iv.toString('base64')}:${authTag.toString('base64')}:${encrypted}`;
}

/**
 * Decrypt token using AES-256-GCM
 * @param {string} encryptedToken - Encrypted token (base64)
 * @returns {string} Decrypted token
 */
function decryptToken(encryptedToken) {
    const parts = encryptedToken.split(':');
    if (parts.length !== 3) {
        throw new Error('Invalid encrypted token format');
    }
    
    const iv = Buffer.from(parts[0], 'base64');
    const authTag = Buffer.from(parts[1], 'base64');
    const encrypted = parts[2];
    
    const decipher = crypto.createDecipheriv('aes-256-gcm', Buffer.from(COOKIE_SECRET.padEnd(32, '0').substring(0, 32)), iv);
    decipher.setAuthTag(authTag);
    
    let decrypted = decipher.update(encrypted, 'base64', 'utf8');
    decrypted += decipher.final('utf8');
    
    return decrypted;
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
 * The BFF handles OAuth flow server-side
 */
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

/**
 * OAuth login initiation endpoint
 * BFF generates PKCE parameters and redirects to authorization server
 */
app.get('/oauth/login', (req, res) => {
    // Generate PKCE parameters
    const codeVerifier = generateRandomString(32);
    const codeChallenge = generateCodeChallenge(codeVerifier);
    const state = generateRandomString(16);

    // Store PKCE parameters in session (server-side)
    req.session.codeVerifier = codeVerifier;
    req.session.state = state;

    console.log('Generated PKCE parameters:', { codeVerifier, codeChallenge, state });

    // Build authorization URL
    const authUrl = new URL(AUTHORIZATION_ENDPOINT);
    authUrl.searchParams.set('response_type', 'code');
    authUrl.searchParams.set('client_id', CLIENT_ID);
    authUrl.searchParams.set('redirect_uri', REDIRECT_URI);
    authUrl.searchParams.set('scope', SCOPE);
    authUrl.searchParams.set('state', state);
    authUrl.searchParams.set('code_challenge', codeChallenge);
    authUrl.searchParams.set('code_challenge_method', 'S256');

    console.log('Redirecting to authorization endpoint:', authUrl.toString());

    // Redirect to authorization server
    res.redirect(authUrl.toString());
});

/**
 * OAuth callback endpoint
 * Authorization server redirects here with authorization code
 * BFF exchanges code for token using PKCE + client_secret
 */
app.get('/oauth/callback', async (req, res) => {
    const { code, state, error, error_description } = req.query;

    // Handle authorization errors
    if (error) {
        console.error('Authorization error:', error, error_description);
        return res.redirect(`/?error=${error}&error_description=${encodeURIComponent(error_description || '')}`);
    }

    if (!code || !state) {
        return res.redirect('/?error=invalid_callback&error_description=Missing+code+or+state');
    }

    // Validate state parameter (CSRF protection)
    if (state !== req.session.state) {
        console.error('State mismatch:', { received: state, expected: req.session.state });
        return res.redirect('/?error=invalid_state&error_description=CSRF+validation+failed');
    }

    const codeVerifier = req.session.codeVerifier;
    if (!codeVerifier) {
        return res.redirect('/?error=missing_verifier&error_description=PKCE+verifier+not+found');
    }

    try {
        // Exchange authorization code for access token
        const tokenParams = new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: REDIRECT_URI,
            client_id: CLIENT_ID,
            client_secret: CLIENT_SECRET, // Confidential client authentication
            code_verifier: codeVerifier // PKCE verification
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
            return res.redirect(`/?error=token_exchange_failed&error_description=${encodeURIComponent(errorText)}`);
        }

        const tokens = await tokenResponse.json();
        console.log('Successfully obtained tokens');

        // Encrypt token before storing in cookie
        const encryptedToken = encryptToken(tokens.access_token);

        // Store encrypted access token in HTTP-only cookie
        res.cookie('access_token', encryptedToken, {
            httpOnly: true,
            secure: process.env.NODE_ENV === 'production',
            sameSite: 'Strict',
            maxAge: tokens.expires_in * 1000
        });

        // Clear session data (no longer needed)
        delete req.session.codeVerifier;
        delete req.session.state;

        // Redirect to home page
        res.redirect('/');
    } catch (error) {
        console.error('Error during token exchange:', error);
        res.redirect(`/?error=server_error&error_description=${encodeURIComponent(error.message)}`);
    }
});

/**
 * API endpoint to get current user info
 * Reads encrypted access token from HTTP-only cookie, decrypts it, and parses JWT claims
 */
app.get('/api/user', (req, res) => {
    const encryptedToken = req.cookies.access_token;

    if (!encryptedToken) {
        return res.status(401).json({ error: 'Not authenticated' });
    }

    try {
        // Decrypt token
        const accessToken = decryptToken(encryptedToken);

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

        // Return user info from JWT claims (payload only, no signature)
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
        res.clearCookie('access_token');
        res.status(401).json({ error: 'Invalid token' });
    }
});

/**
 * Logout endpoint
 * Clears HTTP-only cookies
 */
app.post('/api/logout', (req, res) => {
    res.clearCookie('access_token');
    res.json({ success: true });
});

/**
 * Test endpoint to configure client credentials dynamically
 * This is ONLY for E2E testing. It is required because without it
 * we cannot create a test client, and then configure this server to use it.
 * In production, one would create a client, copy the secret and provide it 
 * to a server like this one using an environment variable or similar.
 */
app.post('/test/configure', (req, res) => {
    const { clientId, clientSecret } = req.body;
    
    if (!clientId || !clientSecret) {
        return res.status(400).json({ error: 'clientId and clientSecret are required' });
    }
    
    CLIENT_ID = clientId;
    CLIENT_SECRET = clientSecret;
    
    console.log(`Test configuration updated: CLIENT_ID=${CLIENT_ID}, CLIENT_SECRET=${clientSecret.substring(0, 10)}...`);
    
    res.json({ success: true, clientId: CLIENT_ID });
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
