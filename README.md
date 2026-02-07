# abstrauth

**Abstrauth** is a lightweight OAuth 2.0 Authorization Server and OpenID Connect Provider with federated identity support, designed to serve multiple client applications using the Backend For Frontend (BFF) pattern.

## What is Abstrauth?

Abstrauth functions as:

- **OAuth 2.0 Authorization Server** - Implements Authorization Code Flow with PKCE (RFC 6749, RFC 7636) for confidential clients only
- **OpenID Connect Provider** - Issues JWT tokens with OpenID Connect claims (`openid`, `profile`, `email` scopes)
- **Identity Provider (IdP)** - Provides native username/password authentication
- **Identity Broker** - Federates authentication with external IdPs (Google, Microsoft, GitHub)
- **Identity and Access Management (IAM)** - Manages user accounts, roles, and client applications

## Key Features

- **Backend For Frontend (BFF) Architecture** - All clients MUST be confidential clients using a backend to handle OAuth flows
- **JWT-based authentication** - Tokens signed with PS256 using public/private key pairs for stateless verification
- **HTTP-only encrypted cookies** - Tokens never exposed to JavaScript for maximum security
- **Federated login** - Users can authenticate via Google OAuth or native credentials
- **Multi-tenancy** - Single server instance serves multiple client applications with role-based access control (RBAC)
- **Self-hosted admin UI** - Angular-based management interface secured by Abstrauth itself using BFF pattern
- **Security hardened** - PKCE required, confidential clients only, HTTP-only cookies, CSRF protection, rate limiting, CSP headers
- **Low footprint** - uses as little as 64MB RAM and a small amount of CPU for typical workloads, idles at near zero CPU, achieved by being built as a native image (GraalVM)
- **Based on Quarkus and Angular** - industry standard frameworks

**Security Architecture:**
- Tokens are stored in encrypted HTTP-only cookies (never accessible to JavaScript)
- PKCE is REQUIRED for all authorization requests
- Only confidential clients are supported (public clients are rejected)
- Compliant with [OAuth 2.0 for Browser-Based Apps](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26)

Abstrauth uses itself as an authorization server for users signing into the admin UI, demonstrating the BFF pattern in practice.

## Security

🔒 **Found a security vulnerability?** Please read our [Security Policy](SECURITY.md) for responsible disclosure guidelines.

For information about the security implementation and features, see [SECURITY_DESIGN.md](docs/security/SECURITY_DESIGN.md).

## Documentation

- [User Guide](USER_GUIDE.md)
- [OAuth 2.0 Authorization Flows](docs/oauth/FLOWS.md)
- [Federated Login](docs/oauth/FEDERATED_LOGIN.md)
- [Database](docs/DATABASE.md)
- [Native Image Build](docs/NATIVE_IMAGE_BUILD.md)
- [Why do I need to implement a BFF?](decisions/BFF.md)
- [Metrics and Grafana](docs/METRICS.md)
- [Machine to Machine authentication](USER_GUIDE.md#machine-to-machine-m2m-authentication)

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

