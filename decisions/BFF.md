# Backend for Frontend

According to https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26#section-6.3.4.3, an architecture where the browser does the token exchange is not recommended for business applications, **sensitive applications**, and applications that handle personal data.

When the authorization server responds to the consent request, it redirects the browser back to the application using HTTP 302. The URL contains the code and state. The browser then makes the request to the application server. While malicious code injected into the page could access the code and state (although how, if no code is being loaded when responding to the redirect), it could also access the code verifier and the token. When using a BFF, the browser never sees the code verifier and never sees the token.

As a result, we cannont recommend public clients, or simple applications that do not have a BFF where the callback URL is handled by code in the browser.

All clients using abtrauth must be confidential, meaning that they need a BFF which can use a client secret to exchange the authorization code for a token.

PKCE will *always* be required, as recommended by the draft IETF document linked above.