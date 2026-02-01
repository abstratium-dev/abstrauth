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
   openssl rand -base64 64 | tr -d '\n'
   ```
   Use this output for `CSRF_TOKEN_SIGNATURE_KEY`.

4. **Generate password pepper** (must be at least 32 characters):
   ```bash
   openssl rand -base64 32
   ```
   Use this output for `PASSWORD_PEPPER`.

5. **Store securely**:
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
     -e PASSWORD_PEPPER="TODO_YOUR_PASSWORD_PEPPER" \
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
   - `CSRF_TOKEN_SIGNATURE_KEY`: HMAC signature key for CSRF tokens (min 32 chars, generate with `openssl rand -base64 64 | tr -d '\n'`)

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
   - `ABSTRAUTH_EMAIL_ENABLED`: Enable/disable email notifications (default: `false` in dev, `true` in prod)
   - `SMTP_HOST`: SMTP server hostname
   - `SMTP_PORT`: SMTP server port (default: `587`)
   - `SMTP_USERNAME`: SMTP authentication username
   - `SMTP_PASSWORD`: SMTP authentication password
   - `EMAIL_FROM`: Sender email address (default: `noreply@abstratium.dev`)


3. **Verify the container is running**:
   ```bash
   docker ps
   docker logs abstrauth
   curl http://localhost:41080/m/health
   curl http://localhost:41080/m/info
   ```

4. Update the client information in the database:
   ```sql
   UPDATE T_oauth_clients SET redirect_uris = '["http://localhost:8080/api/auth/callback", "https://your.host/api/auth/callback"]';
   ```

5. **Access the application**:
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

### Client Secret Management

Abstrauth supports multiple active secrets per client for zero-downtime secret rotation. This allows you to generate a new secret, update your applications to use it, and then revoke the old secret without any service interruption.

#### Viewing Client Secrets

1. Navigate to the **Clients** page
2. Find the client you want to manage
3. Click the **🔑 Manage Secrets** button
4. You'll see a list of all secrets (active and revoked) with:
   - Description
   - Creation date
   - Expiration date (if set)
   - Status (Active, Expired, or Revoked)

**Note:** The actual secret value is only shown once when you create it. After that, only metadata is visible.

#### Creating a New Secret

1. Click **🔑 Manage Secrets** for the client
2. Click **+ Generate New Secret**
3. Enter a description (e.g., "Production secret - Jan 2026")
4. Optionally set an expiration period in days (e.g., 90 days)
5. Click **Generate Secret**
6. **IMPORTANT:** Copy the secret immediately - you won't be able to see it again!
7. Store the secret securely (password manager, environment variables, etc.)

**Best Practices:**
- Use descriptive names that indicate the secret's purpose or environment
- Set expiration dates for secrets (e.g., 90 days) to enforce regular rotation
- Never commit secrets to version control
- Store secrets in secure secret management systems (HashiCorp Vault, AWS Secrets Manager, etc.)

#### Rotating Secrets (Zero-Downtime)

To rotate a secret without service interruption:

1. **Generate a new secret** (see above)
2. **Update your application** to use the new secret
3. **Deploy and test** that the new secret works
4. **Revoke the old secret** once you're confident the new one is working
5. **Optionally delete** the revoked secret to clean up

This process ensures your service continues running throughout the rotation.

#### Revoking a Secret

1. Click **🔑 Manage Secrets** for the client
2. Find the secret you want to revoke
3. Click **🔒 Revoke** next to the secret
4. Confirm the action

**Note:** You cannot revoke the last active secret. You must have at least one active secret at all times.

#### Deleting a Revoked Secret

Once a secret is revoked, you can permanently delete it:

1. Click **🔑 Manage Secrets** for the client
2. Find the revoked secret
3. Click **🗑️ Delete** next to the secret
4. Confirm the permanent deletion

**Warning:** Deletion is permanent and cannot be undone. Only delete secrets you're certain you no longer need.

#### Expiration Warnings

If you've set an expiration date for a secret, Abstrauth will send email notifications to the account that created the secret:

- **30 days before expiration** - First warning
- **3 days before expiration** - Final warning
- **When the secret expires** - Expiration notice

**Email Configuration:** Email notifications require SMTP configuration. See the environment variables section for details:
- `ABSTRAUTH_EMAIL_ENABLED`: Enable/disable email notifications (default: `false` in dev, `true` in prod)
- `SMTP_HOST`: SMTP server hostname
- `SMTP_PORT`: SMTP server port (default: `587`)
- `SMTP_USERNAME`: SMTP authentication username
- `SMTP_PASSWORD`: SMTP authentication password
- `EMAIL_FROM`: Sender email address (default: `noreply@abstratium.dev`)

#### Security Considerations

- **Multiple active secrets** allow for zero-downtime rotation
- **Expired secrets** are automatically marked as expired but not deleted
- **Revoked secrets** cannot be used for authentication
- **Account tracking** - Each secret records who created it for audit purposes
- **Regular rotation** is recommended (e.g., every 90 days)

## Machine-to-Machine (M2M) Authentication

Abstrauth supports service-to-service authentication using the OAuth 2.0 Client Credentials Grant. This allows backend services, microservices, and automated jobs to authenticate without user interaction.

### Use Cases

- Microservice-to-microservice communication
- Backend services accessing APIs
- Automated jobs and scripts
- Service accounts for CI/CD pipelines

### Creating an M2M Service Client

**Via UI:**

1. Navigate to **OAuth Clients** page
2. Click **+ Add Client**
3. Fill in the client details:
   - **Client ID**: `my-service`
   - **Client Name**: `My Backend Service`
   - **Client Type**: `confidential`
   - **Redirect URIs**: `http://localhost:3000/callback` (required but not used for M2M)
   - **Allowed Scopes**: **Leave empty** for role-based authorization
4. Click **Create Client**
5. **Copy the generated client secret** - you won't see it again!
6. Click **👥 Manage Roles**
7. Add roles (e.g., `api-reader`, `api-writer`)

**Important:** Roles can only be added to clients with **no scopes configured**. If you configure scopes (like `openid`, `profile`, `email`), the role management will be disabled.

### Obtaining an Access Token

Use the client credentials grant to get an access token:

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=my-service" \
  -d "client_secret=YOUR_CLIENT_SECRET"
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

**Note:** For clients with no scopes, the `scope` field will be omitted from the response.

### Using the Access Token

Once you have an access token, use it to access protected resources:

```bash
export TOKEN="eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9..."

curl -v -X GET http://localhost:8080/api/auth/check \
  -H "Authorization: Bearer $TOKEN"
```

**Success Response (200 OK):**
```json
{
  "authenticated": true,
  "clientId": "my-service",
  "groups": ["my-service_api-reader"]
}
```

**Failure Responses:**
- **Invalid token**: Returns `401 Unauthorized` or redirects to login
- **Expired token**: Returns `401 Unauthorized`
- **No token**: Redirects to login page

### Role-Based Authorization

Service clients use roles in the `groups` claim for `@RolesAllowed` authorization:

**Token Claims:**
```json
{
  "iss": "https://abstrauth.abstratium.dev",
  "sub": "my-service",
  "exp": 1234567890,
  "iat": 1234564290,
  "jti": "unique-token-id",
  "client_id": "my-service",
  "groups": ["my-service_api-reader", "my-service_api-writer"]
}
```

**Backend Code:**
```java
@GET
@Path("/data")
@RolesAllowed("my-service_api-reader")
public Response getData() {
    // Only accessible to clients with api-reader role
    return Response.ok().build();
}
```

### Scope-Based vs Role-Based

**Scope-Based (User Authentication):**
- Client configured with scopes: `["openid", "profile", "email"]`
- Used for user login flows
- Claims filtered based on requested scopes
- Cannot add roles

**Role-Based (M2M Services):**
- Client configured with **empty** scopes: `[]`
- Used for service-to-service authentication
- Roles added to `groups` claim
- Cannot request scopes

**Important:** A client uses **either** scopes **or** roles, never both.

### Security Best Practices

1. **Rotate secrets regularly** (e.g., every 90 days)
2. **Use descriptive client names** to identify services
3. **Grant minimum required roles** (principle of least privilege)
4. **Store secrets securely** (environment variables, secret managers)
5. **Monitor token usage** via logs and metrics
6. **Set up alerts** for failed authentication attempts

### Example: Python Service

```python
import requests
import os

# Configuration
TOKEN_URL = "http://localhost:8080/oauth2/token"
API_URL = "http://localhost:8080/api/data"
CLIENT_ID = "my-service"
CLIENT_SECRET = os.environ["CLIENT_SECRET"]

# Get access token
token_response = requests.post(TOKEN_URL, data={
    "grant_type": "client_credentials",
    "client_id": CLIENT_ID,
    "client_secret": CLIENT_SECRET
})
token_response.raise_for_status()
access_token = token_response.json()["access_token"]

# Use token to access API
api_response = requests.get(API_URL, headers={
    "Authorization": f"Bearer {access_token}"
})
api_response.raise_for_status()
print(api_response.json())
```

### Example: Java Service

```java
import java.net.http.*;
import java.net.URI;

public class ServiceClient {
    private static final String TOKEN_URL = "http://localhost:8080/oauth2/token";
    private static final String CLIENT_ID = "my-service";
    private static final String CLIENT_SECRET = System.getenv("CLIENT_SECRET");
    
    public String getAccessToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String body = String.format(
            "grant_type=client_credentials&client_id=%s&client_secret=%s",
            CLIENT_ID, CLIENT_SECRET
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
            
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Parse JSON and extract access_token
        return parseAccessToken(response.body());
    }
}
```

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

