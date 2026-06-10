# Abstrauth™

| "Simple Yet Powerful Authentication That Scales With Your Business"

**Abstrauth™** is a lightweight OAuth 2.0 Authorization Server and OpenID Connect Provider with multi-tenant organisation support and federated identity, designed for confidential clients using the Backend For Frontend (BFF) pattern.

## Why Abstrauth™?

Your users deserve a sign-in experience that just works — and your team deserves full control over it without huge fees, accepting vendor lock-in, or wrestling with sprawling configuration consoles designed for enterprises ten times your size.

Abstrauth™ is **self-hosted authentication and identity management** built around how modern SaaS products actually work:

- **One server, many organisations.** Every customer gets their own isolated space. Users belong to organisations, organisations subscribe to your applications, and each session carries a cryptographically signed tenant identifier that your own services can use directly — no extra middleware, no per-request lookup.
- **Your data, your infrastructure.** Abstrauth™ runs on a single server with as little as ~64 MB of RAM. You own the database, the keys, and the logs.
- **Sign in how your users expect.** Native email/password accounts sit alongside "Sign in with Google" and "Sign in with Microsoft" — you choose which to offer.
- **Roles that travel with the user.** When a user signs in, their roles for *that organisation and that application* are baked into the token. Downstream services get everything they need in a single verified claim — no extra round-trips, no shared session state.
- **Control without complexity.** Organisation owners manage their own members and decide which applications their team can access. Application owners define which roles exist. Neither can exceed the boundaries the other has set.
- **Security by default.** Tokens are stored in HTTP-only cookies, never touched by JavaScript. PKCE is mandatory. Rate limiting and CSRF protection are built in, not bolted on.
- **Tiny footprint, production ready.** A GraalVM native image means near-instant startup, minimal memory, and a single deployable binary. No JVM warm-up, no bloat.

If you are building a software application and need authentication that scales from one customer to thousands Abstrauth™ is for you.

## A more technical overview 

Abstrauth™ functions as:

- **OAuth 2.0 Authorization Server** - Authorization Code Flow with PKCE (RFC 6749, RFC 7636); confidential clients only
- **OpenID Connect Provider** - Issues JWT tokens with `openid`, `profile`, `email`, and `orgId` claims
- **Identity Provider (IdP)** - Native username/password authentication
- **Identity Broker** - Federated authentication with external IdPs (Google, Microsoft)
- **Identity and Access Management (IAM)** - Manages user accounts, organisations, roles, subscriptions, and client applications
- **Multi-tenant Platform** - Organisations subscribe to applications; each JWT carries an `orgId` claim used as a tenant discriminator by downstream services

## Key Features

- **Organisations** - Every user belongs to one or more organisations. A user selects their active organisation at sign-in; the resulting `orgId` JWT claim doubles as the `tenantId` for downstream services.
- **Subscriptions** - Organisations subscribe to applications (OAuth clients). Access is denied unless a subscription exists; public clients support auto-subscription.
- **Role-based access control** - Roles are scoped per account, client, and organisation. Public clients declare an allowlist of assignable roles; private clients allow free-form roles.
- **BFF architecture** - All clients MUST be confidential clients with a backend; public (SPA-only) clients are rejected.
- **JWT-based tokens** - Signed with PS256; carry `groups` (roles) and `orgId` claims for stateless RBAC and tenant resolution downstream.
- **HTTP-only encrypted cookies** - Tokens never exposed to JavaScript.
- **Federated login** - Google and Microsoft OAuth supported alongside native credentials.
- **Self-hosted admin UI** - Angular management interface secured by Abstrauth™ itself using the BFF pattern.
- **Security hardened** - PKCE required, CSRF protection, rate limiting, CSP headers, role allowlist enforcement, session-fixation protection during org selection.
- **Low footprint** - ~64 MB RAM at idle; built as a GraalVM native image.
- **Based on Quarkus and Angular** - industry-standard frameworks.

**Security Architecture:**
- Tokens stored in encrypted HTTP-only cookies (never accessible to JavaScript)
- PKCE required for all authorization requests
- Only confidential clients supported
- Org membership verified at token issuance; a token issued for one org cannot elevate privileges in another
- Role allowlist enforced server-side for public clients, preventing privilege escalation via subscription
- Compliant with [OAuth 2.0 for Browser-Based Apps](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26)

Abstrauth™ uses itself as its own authorization server for the admin UI, demonstrating the BFF pattern and multi-tenant org model in practice.

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

