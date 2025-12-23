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

### Pull and Run the Docker Container

1. **Pull the latest image** from GitHub Container Registry:
   ```bash
   docker pull ghcr.io/abstratium/abstrauth:latest
   ```

2. **Run the container**:
   ```bash
   docker run -d \
     --name abstrauth \
     --network your-network \
     -p 127.0.0.1:41080:8080 \
     -p 127.0.0.1:9002:9002 \
     -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://your-mysql-host:3306/abstrauth" \
     -e QUARKUS_DATASOURCE_USERNAME="abstrauth" \
     -e QUARKUS_DATASOURCE_PASSWORD="your_secure_password" \
     -e SMALLRYE_JWT_SIGN_KEY="your_base64_private_key_here" \
     -e MP_JWT_VERIFY_PUBLICKEY="your_base64_public_key_here" \
     -e OAUTH_GOOGLE_CLIENT_ID="your_google_client_id" \
     -e OAUTH_GOOGLE_CLIENT_SECRET="your_google_client_secret" \
     -e ALLOW_SIGNUP="false" \
     ghcr.io/abstratium/abstrauth:latest
   ```

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

## Environment Variables Reference

The application uses several environment variables for configuration:

### Required Variables

- **`QUARKUS_DATASOURCE_JDBC_URL`** - Database connection string
  - Format: `jdbc:mysql://<host>:<port>/<databasename>`
  - Example: `jdbc:mysql://abstrauth-db:3306/abstrauth`
  - Currently only MySQL is supported

- **`QUARKUS_DATASOURCE_USERNAME`** - Database user
  - Example: `abstrauth`

- **`QUARKUS_DATASOURCE_PASSWORD`** - Database password
  - Use a strong, unique password
  - Never commit this to version control

- **`SMALLRYE_JWT_SIGN_KEY`** - Base64-encoded RSA private key for signing JWT tokens
  - Must be at least 2048 bits for PS256 algorithm
  - Generate using OpenSSL (see "Generate JWT Keys" section)
  - Keep this secret and secure

- **`MP_JWT_VERIFY_PUBLICKEY`** - Base64-encoded RSA public key for verifying JWT tokens
  - Must match the private key
  - Extract from private key using `extract-public-key.sh` or OpenSSL

### Optional Variables

- **`ALLOW_SIGNUP`** - Boolean flag to enable/disable user self-registration
  - `true` - Allow users to create accounts via signup
  - `false` - Only admins can create accounts (default)
  - Default: `false` (disabled for security)

- **`OAUTH_GOOGLE_CLIENT_ID`** - Google OAuth client ID for federated login
  - Required only if using "Sign in with Google"
  - Obtain from Google Cloud Console

- **`OAUTH_GOOGLE_CLIENT_SECRET`** - Google OAuth client secret
  - Required only if using "Sign in with Google"
  - Keep this secret and secure

- **`PASSWORD_PEPPER`** - Application-wide secret for additional password security
  - Generate using: `openssl rand -base64 32`
  - WARNING: Changing this requires re-hashing all passwords
  - Default: Development value (change in production)

### Management Interface Variables

- **`QUARKUS_MANAGEMENT_ENABLED`** - Enable management interface (default: `true`)
- **`QUARKUS_MANAGEMENT_PORT`** - Management interface port (default: `9002`)

