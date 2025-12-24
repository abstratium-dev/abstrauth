# abstrauth

**Abstrauth** is a lightweight OAuth 2.0 Authorization Server and OpenID Connect Provider with federated identity support, designed to serve multiple client applications simultaneously.

## What is Abstrauth?

Abstrauth functions as:

- **OAuth 2.0 Authorization Server** - Implements Authorization Code Flow and Authorization Code Flow with PKCE (RFC 6749, RFC 7636)
- **OpenID Connect Provider** - Issues JWT tokens with OpenID Connect claims (`openid`, `profile`, `email` scopes)
- **Identity Provider (IdP)** - Provides native username/password authentication
- **Identity Broker** - Federates authentication with external IdPs (Google, Microsoft, GitHub)
- **Identity and Access Management (IAM)** - Manages user accounts, roles, and client applications

## Key Features

- **JWT-based authentication** - Tokens signed with RS256/PS256 using public/private key pairs for stateless verification
- **Federated login** - Users can authenticate via Google OAuth or native credentials
- **Multi-tenancy** - Single server instance serves multiple client applications with role-based access control (RBAC)
- **Self-hosted admin UI** - Angular-based management interface secured by Abstrauth itself
- **Security hardened** - PKCE enforcement, CSRF protection, rate limiting, CSP headers, and comprehensive security audit compliance

It uses JWT for authentication and authorization, signed with a public/private key pair so that third-party applications can validate the tokens and roles without calls to the authorization server unless they want to do introspection to check if the token has been revoked.

It coincidentally also uses itself as an authorization server for users signing into the admin UI.

## Security

🔒 **Found a security vulnerability?** Please read our [Security Policy](SECURITY_POLICY.md) for responsible disclosure guidelines.

For information about the security implementation and features, see [SECURITY.md](docs/security/SECURITY.md).

## Documentation

- [User Guide](USER_GUIDE.md)
- [OAuth 2.0 Authorization Flows](docs/oauth/FLOWS.md)
- [Federated Login](docs/oauth/FEDERATED_LOGIN.md)
- [Database](docs/DATABASE.md)
- [Native Image Build](docs/NATIVE_IMAGE_BUILD.md)

## Running the Application

See [User Guide](USER_GUIDE.md)

## Development and Testing

See [Development and Testing](docs/DEVELOPMENT_AND_TESTING.md)

## TODO

See [TODO.md](TODO.md)


## Aesthetics

### favicon

https://favicon.io/favicon-generator/ - text based

Text: a
Background: rounded
Font Family: Leckerli One
Font Variant: Regular 400 Normal
Font Size: 110
Font Color: #FFFFFF
Background Color: #5c6bc0

