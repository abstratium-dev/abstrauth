# User Manual

## Installation

It is intended that Abstrauth be run using docker.
Abstrauth supports MySql and will soon also support postgresql and MS SQL Server.

You need to add a database/schema and a user to the database manually. Thereafter, Abstrauth can access that database with that user and it will create all of the necessary tables and an initial OAuth2 client with the client_id `abstratium-abstrauth`. This client is used by the application itself, and users sign into the application using this client in order to manage other clients, as well as accounts and roles.

### Create the Database, User and Grant Permissions

#### MySQL

Abstrauth requires a MySQL database. Create a database and user with the following steps:

1. **Connect to MySQL** as root or admin user:

(change `<password>` to your password)

```bash
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -p<password>

DROP USER IF EXISTS 'abstrauth'@'%';

CREATE USER 'abstrauth'@'%' IDENTIFIED BY '<password>';

DROP DATABASE IF EXISTS abstrauth;

CREATE DATABASE abstrauth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON abstrauth.* TO abstrauth@'%'; -- on own database

FLUSH PRIVILEGES;

EXIT;
```

Abstrauth will automatically create all necessary tables and an initial OAuth2 client (`abstratium-abstrauth`) when it first connects to the database.

**Important:** The default client is configured as a **confidential client** (required for the BFF pattern). You MUST set the client secret via environment variable (see below).

New versions will update the database as needed.

### Generate JWT Keys

Abstrauth uses RSA keys for signing and verifying JWT tokens. You need to generate a private/public key pair.

1. **Generate a new RSA private key** (2048 bits minimum):
   ```bash
   openssl genpkey -algorithm RSA -out privateKey.pem -pkeyopt rsa_keygen_bits:2048
   ```

2. **Extract the public key**:
   ```bash
   openssl rsa -in privateKey.pem -pubout -out publicKey.pem
   ```

3. **Convert private key to base64** (without headers/newlines):
   ```bash
   grep -v '^-----' privateKey.pem | tr -d '\n'
   ```
   Use this output below for `SMALLRYE_JWT_SIGN_KEY`.

4. **Convert public key to base64** (without headers/newlines):
   ```bash
   grep -v '^-----' publicKey.pem | tr -d '\n'
   ```
   Use this output below for `MP_JWT_VERIFY_PUBLICKEY`.

5. **Secure the private key**:
   ```bash
   chmod 600 privateKey.pem
   # Store securely and never commit to version control
   ```

### Generate Client Secret, Cookie Encryption Key, and CSRF Token Signature Key

**Important:** Abstrauth uses the Backend For Frontend (BFF) pattern and CSRF protection, which requires:
1. A client secret for the default OAuth client
2. A cookie encryption key for securing HTTP-only cookies
3. A CSRF token signature key for HMAC-signing CSRF tokens

1. **Generate a secure client secret** (32+ characters recommended):
   ```bash
   openssl rand -base64 32
   ```
   Use this output for `ABSTRAUTH_CLIENT_SECRET`.

2. **Generate a cookie encryption key** (must be at least 32 characters):
   ```bash
   openssl rand -base64 32
   ```
   Use this output for `COOKIE_ENCRYPTION_SECRET`.

3. **Generate a CSRF token signature key** (must be at least 32 characters):
   ```bash
   openssl rand -base64 64
   ```
   Use this output for `CSRF_TOKEN_SIGNATURE_KEY`.

4. **Store securely**:
   - Never commit these secrets to version control
   - Use a secrets manager in production (e.g., AWS Secrets Manager, HashiCorp Vault)
   - Rotate secrets periodically

**Security Note:** The client secret in the database (`client_secret_hash`) must match the secret provided via `ABSTRAUTH_CLIENT_SECRET`. The database stores a BCrypt hash for security. In development, a default hash is provided, but **you MUST change this in production**.

### Pull and Run the Docker Container

1. **Pull the latest image** from GitHub Container Registry:
   ```bash
   docker pull ghcr.io/abstratium-dev/abstrauth:latest
   ```

2. **Run the container**:

_Replace all `TODO_...` values with the values generated above.

   ```bash
   docker run -d \
     --name abstrauth \
     --network your-network \
     -p 127.0.0.1:41080:8080 \
     -p 127.0.0.1:9002:9002 \
     -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://your-mysql-host:3306/abstrauth" \
     -e QUARKUS_DATASOURCE_USERNAME="abstrauth" \
     -e QUARKUS_DATASOURCE_PASSWORD="TODO_YOUR_SECURE_PASSWORD" \
     -e SMALLRYE_JWT_SIGN_KEY="TODO_YOUR_BASE64_PRIVATE_KEY_HERE" \
     -e MP_JWT_VERIFY_PUBLICKEY="TODO_YOUR_BASE64_PUBLIC_KEY_HERE" \
     -e ABSTRAUTH_CLIENT_SECRET="TODO_YOUR_GENERATED_CLIENT_SECRET" \
     -e COOKIE_ENCRYPTION_SECRET="TODO_YOUR_GENERATED_COOKIE_ENCRYPTION_KEY" \
     -e CSRF_TOKEN_SIGNATURE_KEY="TODO_YOUR_GENERATED_CSRF_TOKEN_SIGNATURE_KEY" \
     -e OAUTH_GOOGLE_CLIENT_ID="TODO_YOUR_GOOGLE_CLIENT_ID" \
     -e OAUTH_GOOGLE_CLIENT_SECRET="TODO_YOUR_GOOGLE_CLIENT_SECRET" \
     -e ALLOW_SIGNUP="TODO_TRUE_OR_FALSE" \
     -e SERVER_BASE_URL="https://auth.yourdomain.com" \
     -e QUARKUS_OIDC_BFF_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME="TODO_TRUE_IF_BEHIND_REVERSE_PROXY_WHICH_ENDS_TLS_TUNNEL"
     ghcr.io/abstratium-dev/abstrauth:latest
   ```

   **Required Environment Variables:**
   - `QUARKUS_DATASOURCE_JDBC_URL`: Database connection URL (format: `jdbc:mysql://<host>:<port>/<database>`)
   - `QUARKUS_DATASOURCE_USERNAME`: Database username
   - `QUARKUS_DATASOURCE_PASSWORD`: Database password (use strong, unique password)
   - `SMALLRYE_JWT_SIGN_KEY`: Base64-encoded RSA private key for signing JWTs (min 2048 bits for PS256)
   - `MP_JWT_VERIFY_PUBLICKEY`: Base64-encoded RSA public key for verifying JWTs (must match private key)
   - `ABSTRAUTH_CLIENT_SECRET`: Client secret for the default OAuth client (BFF pattern, generate with `openssl rand -base64 32`)
   - `COOKIE_ENCRYPTION_SECRET`: Encryption key for HTTP-only cookies (min 32 chars, generate with `openssl rand -base64 32`)
   - `CSRF_TOKEN_SIGNATURE_KEY`: HMAC signature key for CSRF tokens (min 32 chars, generate with `openssl rand -base64 64`)
   
   **Optional Environment Variables:**
   - `OAUTH_GOOGLE_CLIENT_ID`: Google OAuth client ID (required only for "Sign in with Google")
   - `OAUTH_GOOGLE_CLIENT_SECRET`: Google OAuth client secret (required only for "Sign in with Google")
   - `ALLOW_SIGNUP`: Allow user self-registration (`true` in dev, `false` in prod, default: `false`)
   - `PASSWORD_PEPPER`: Application-wide secret for password security (generate with `openssl rand -base64 32`, WARNING: changing requires re-hashing all passwords)
   - `RATE_LIMIT_ENABLED`: Enable rate limiting on OAuth endpoints (default: `true`)
   - `RATE_LIMIT_OAUTH_MAX_REQUESTS`: Max requests per IP per window (default: `100`)
   - `RATE_LIMIT_OAUTH_WINDOW_SECONDS`: Rate limit time window in seconds (default: `60`)
   - `RATE_LIMIT_OAUTH_BAN_DURATION_SECONDS`: Ban duration after exceeding limits (default: `300`)
   - `QUARKUS_MANAGEMENT_ENABLED`: Enable management interface (default: `true`)
   - `QUARKUS_MANAGEMENT_PORT`: Management interface port (default: `9002`)
   - `QUARKUS_OIDC_BFF_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME`: set to true if Abstrauth runs behind a reverse proxy that terminates TLS
   - `ALLOW_NATIVE_SIGNIN`: if true, users can sign in with email & password, otherwise they can only sign in  (default: `true`)

3. **Verify the container is running**:
   ```bash
   docker ps
   docker logs abstrauth
   curl http://localhost:41080/m/health
   curl http://localhost:41080/m/info
   ```

4. **Access the application**:
   - Main application: http://localhost:41080
   - Management interface: http://localhost:9002/m/info

### Prerequisites

Before installation, ensure you have:

- **Docker** installed and running
- **MySQL 8.0+** database server
- **Network connectivity** between Docker container and MySQL
- **OpenSSL** for generating JWT keys
- **GitHub account** (if pulling from GitHub Container Registry)
- **nginx** or similar for reverse proxying and terminating TLS

## Initial Onboarding

The first time a user logs in, they are prompted to create an account. This is done to ensure that the user has an account before they can use the service.

The first user is given the admin role so that they can add other accounts if signing up is disabled.

## Account and Role Management

To manage accounts, the user needs the role `abstratium-abstrauth_manage-accounts`.
Users with an account with this role can see all the accounts which have roles mapped to the client_ids that the user is also mapped to.

Users with an account with this role can add and remove roles for accounts. They cannot add the admin role to any account, as only admin users can do that. 
They also cannot add roles to their or other accounts if the client_id of the account they are adding the role to does not already have a role mapped to that client_id.

The idea here is that users can only see other accounts for clients that they have the right to use, and that they can only add roles to users for clients that they have the right to use.

## Client Management

To manage clients, the user needs the role `abstratium-abstrauth_manage-clients`.
Users with an account with this role can see all the clients which the user is mapped to.

## Adding New Accounts Manually

Use the "create account" link (available to users with the role `manage-accounts`). This is only really designed for setups where signing up has been disabled.

IT IS NOT RECOMMENDED to add roles to an account before the user tells you that they were able to sign in. Imagine adding the `admin` role to their account but a threat actor intercepting your invitation and using that role to remove you and other administrators!

## Monitoring and Health Checks

Abstrauth provides several endpoints for monitoring:

- **Health Check**: `http://localhost:9002/m/health`
  - Returns application health status
  - Includes database connectivity check

- **Info Endpoint**: `http://localhost:9002/m/info`
  - Returns build information, version, and configuration
  - Useful for verifying deployment

## Troubleshooting

### Container logs error on startup

If you see the following error, you can safely ignore it. The reason that it is logged is because abstrauth uses the Quarkus extension `quarkus-oidc` to validate tokens, and it periodically checks the JWKS endpoint for key updates, including at startup. Since the server isn't entirely up and running at this point, the check initially fails, but as soon as you try and sign in, it re-fetches the keys and works.

```bash
2026-01-02 20:49:41,709 ERROR [io.qua.oid.run.OidcProviderClientImpl] (vert.x-eventloop-thread-1) [skey:] Request https://auth.abstratium.dev/.well-known/jwks.json has failed: status: 502, ...
```

### Container won't start

1. Check Docker logs: `docker logs abstrauth`
2. Verify environment variables are set correctly
3. Ensure database is accessible from container
4. Check network connectivity: `docker network inspect your-network`

### Database connection errors

1. Verify MySQL is running: `mysql -u abstrauth -p -h your-mysql-host`
2. Check firewall rules allow connection on port 3306
3. Verify database user has correct permissions
4. Check JDBC URL format is correct

### JWT token errors

1. Verify keys are correctly base64-encoded
2. Ensure public key matches private key
3. Check key length is at least 2048 bits
4. Verify no extra whitespace in environment variables

## Security Best Practices

1. **Never use default/test keys in production**
2. **Store secrets in secure secret management systems** (e.g., HashiCorp Vault, AWS Secrets Manager)
3. **Use strong, unique passwords** for database and admin accounts
4. **Enable HTTPS** in production (configure reverse proxy)
5. **Regularly update** the Docker image to get security patches
6. **Monitor logs** for suspicious activity
7. **Backup database regularly**
8. **Limit network access** to database and management interface
9. **Rotate JWT keys periodically** (requires user re-authentication)
10. **Keep `ALLOW_SIGNUP=false`** unless you need public registration

## Integrating Abstrauth into Your Application

See [client-example](client-example/README.md).

### Additional Resources

- [OAuth 2.0 Flows Documentation](docs/oauth/FLOWS.md)
- [Federated Login Guide](docs/oauth/FEDERATED_LOGIN.md)
- [Security Best Practices](docs/security/SECURITY_DESIGN.md)
- [RFC 6749 - OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)

