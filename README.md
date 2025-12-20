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

For information about the security implementation and features, see [SECURITY.md](SECURITY.md).

## Documentation

- [OAuth 2.0 Authorization Flows](FLOWS.md)
- [Federated Login](FEDERATED_LOGIN.md)
- [Database](DATABASE.md)
- [Native Image Build](NATIVE_IMAGE_BUILD.md)

## Development

### Update CLI

    jbang version --update
    jbang app install --fresh --force quarkus@quarkusio

### Server

First add env vars:

    source /w/abstratium-abstrauth.env

The application uses Quarkus. Run it with either `./mvnw quarkus:dev` or `quarkus dev` if you have installed the Quarkus CLI.

#### Code coverage

    ./mvnw clean verify

Open the jacoco report from `target/jacoco-report/index.html`.

### Trouble Shooting

Use `ng serve` and accept the port it offers, in order to see the actual error messages that are occuring during the build, if you see the following error in Quarkus, and it shows a Quarkus page with the error message `Error restarting Quarkus` and `io.vertx.core.impl.NoStackTraceException`

    Error in Quinoa while running package manager 


### Database

The application uses a MySQL database. It expects a database to be running at `localhost:41040` with the user `root` and password `secret`.

Create the container, the database and user for abstrauth:

    docker run -d \
        --restart unless-stopped \
        --name abstratium-mysql \
        --network abstratium \
        -e MYSQL_ROOT_PASSWORD=secret \
        -p 127.0.0.1:41040:3306 \
        -v /shared2/mysql-abstratium/:/var/lib/mysql:rw \
        mysql:9.3

    # create the database and user for abstrauth
    docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -psecret

    DROP USER IF EXISTS 'abstrauth'@'%';

    CREATE USER 'abstrauth'@'%' IDENTIFIED BY 'secret';

    DROP DATABASE IF EXISTS abstrauth;

    CREATE DATABASE abstrauth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    GRANT ALL PRIVILEGES ON abstrauth.* TO abstrauth@'%'; -- on own database

    FLUSH PRIVILEGES;

exit, then reconnect using the abstrauth user:

    docker run -it --network abstratium --rm mysql mysql -h abstratium-mysql --port 3306 -u abstrauth -psecret abstrauth


## Testing

### Unit and Integration Tests

Run unit tests (including angular tests):

    mvn test

Run all tests (unit + integration):

    mvn verify

### E2E Testing with Playwright

The E2E tests are in `e2e-tests/` and use Playwright to test the full application stack.

See the [E2E Testing Documentation](e2e-tests/README.md) for detailed instructions.


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

