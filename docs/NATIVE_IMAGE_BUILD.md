# Building Native Image with Docker and Mandrel

This document describes how to build the abstrauth OAuth server as a native executable using Docker with Mandrel, without requiring GraalVM or Mandrel to be installed on your local Ubuntu system.

## TL;DR

Use method 3 which is described below:

```bash
./build-docker-image.sh
```

This script will:
1. Build the native executable using Maven with container build
2. Extract the build version from `application.properties`
3. Build the Docker image with both version-specific and `latest` tags

Then run the container (make sure to source your env file first: `source /w/abstratium-abstrauth.env`) as shown below, in order to test it. For a production deployment, see [../USER_GUIDE.md](USER_GUIDE.md).

Note: The `latest` tag always refers to the most recently built and pushed image. You can also use a specific version tag (e.g., `ghcr.io/abstratium-dev/abstrauth:20251223212503`).

```bash
docker run -it --rm \
  -p 127.0.0.1:8080:8080 \
  -p 127.0.0.1:9002:9002 \
  --network abstratium \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://abstratium-mysql:3306/abstrauth \
  -e QUARKUS_DATASOURCE_USERNAME=abstrauth \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  -e OAUTH_GOOGLE_CLIENT_ID="${OAUTH_GOOGLE_CLIENT_ID}" \
  -e OAUTH_GOOGLE_CLIENT_SECRET="${OAUTH_GOOGLE_CLIENT_SECRET}" \
  -e CSRF_TOKEN_SIGNATURE_KEY="asdfasdfasdfasdf" \
  -e ALLOW_SIGNUP=false \
  -e ALLOW_NATIVE_SIGNIN=true \
  -e OAUTH_GOOGLE_REDIRECT_URI="http://localhost:8080/oauth2/callback/google" \
  -e PASSWORD_PEPPER="${PASSWORD_PEPPER}" \
  -e QUARKUS_MANAGEMENT_HOST=0.0.0.0 \
  -e ABSTRAUTH_CLIENT_SECRET=qzzRSarGgFFRCz3omjvxdkTHfnlibAjG \
  -e QUARKUS_OIDC_BFF_AUTH_SERVER_URL=http://localhost:8080 \
  -e QUARKUS_OIDC_BFF_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME=false \
  -e SMALLRYE_JWT_SIGN_KEY=MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDBvM+riIoEaxItLtOU0or3kQ3P9Om7pN4NmM32ONMJxHSn6n+GpineEGeCdyFcE5OkRF0a6BrDANXDUfhGQccnN6u6BalIebF0pVBpyra+i9Wcpbb1dixZgm6cgROm1ZDqSfIGnx48PsMqHM/EE6LLHeiB8V/bdmd01eiRg/LAp8q7BZJGBlDZUNmedP/bkcU8szBT8X6ZD4btiITDZZKxLWtgZsEJmF0tt90gDSBy5pU/ewwUrTMaCTj2eQgQ5AfefMZ6tJT6phYBEFyWPTEkkS+ulJct0xt3rPrekcseeAdM61FdtaOdSr+TvWbVRp7s5qSobT9qaCM5+4bnPv9bAgMBAAECggEAAJfcEuxiGngGyuNKe+Qrz2zpm+oQunsGFbM9aN7tASn8KXTLBdXbFEunOtEJOxzxkMkyInOffAVeojB4ECRXaxlSifPxJsBTTht2JDzIqSCzJhL5J8Xq24NOD2XzHMmpSJkICAPTYIqDUnewHdY+41xTapfF8V1qx613/tS77jcT8Qar556YTccJu0qgwNlrapjQYRvV5wT2uV4JqYaN1vC3tNXlIIsCGNR2CN9S/s6jp/gOE+eqfCbiuON1jCbXbN0U1TL4WKj6OYsOiA27hGCJ6n17gMBou+t8T8MccuceV9Lb8ecXB6h/oUok9gqZ57om8dGVoP4hZaOPsSWXwQKBgQDwRMCmunAe9d64yoUQYqmfHKtW584ltIr4do0hpaOsGXTjmrueG10BhFXPlDYoU1BWRezTQXVxWcPoLeIFFA511H22nRQ4g/dvBibCUr8B33jpGfbUq1baMrp2GN2KYdVbChEtrWPGNZCtXeux9sLuoDZVXbakdr4frAEDna+Q0QKBgQDObCG2cW8rdbh+0BOZY2x82titS4uE4Nx8LpF2Yl8zyFa/n+eRyXzhZXtQvEka04IX7SqncOMPYPen0pCAgd3DvoGTVcxRB0d2sCJ9oY++A4kEoUprddcQXwhWGqPTATosVbr3V2XwAx58yvG0odZBUN466shR7FEQmzbmviP4awKBgQDCx2nKgC/u2XHSKtPOob1SsQIx9L/JD2Dt5eWp1kcmeIirD0Bz/0jZtvd9zXBOJqRlHFDOPi3AU34fFjs51LWYTkgPp63B1zHa/oijVkNkeE7j4dmZNMG3KBLDNIs86Oz23eVpOzw8biY4dYBiiGIk4xrI/6zWDTE6Kc20qbuvUQKBgGYoWZ7jELOfdQk9jRWSgPRhkm5hPtEqP7Qtj8vY72i/Mz9usboSz3z1LkxMgpmGJ5ITy9JGKflIcghaSy1uGARx2crC4XUQdyukC83FEVBmi38BG8WG8kKl5YhHcuBQcSvT2c3jMQ3RXVtBTNGqblCw5uqdmzoADDZ9unQDkeW1AoGBALy9D8nFqZQB9i4fPeve9kTLd4wgyB8KrpTGtKnIcDOqjQcA2DA+mG/vzuAxP2ie4/QnCPAxzHtmLTKMAjzxNHgB+6zGYT+zBOgFdWqfUHz14DisHoSICkwaKluTBeVZYakQb0g57TvtfagvNf9ADuiCorbYot7yxoG+IRCDCauw \
  -e MP_JWT_VERIFY_PUBLICKEY=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwbzPq4iKBGsSLS7TlNKK95ENz/Tpu6TeDZjN9jjTCcR0p+p/hqYp3hBngnchXBOTpERdGugawwDVw1H4RkHHJzerugWpSHmxdKVQacq2vovVnKW29XYsWYJunIETptWQ6knyBp8ePD7DKhzPxBOiyx3ogfFf23ZndNXokYPywKfKuwWSRgZQ2VDZnnT/25HFPLMwU/F+mQ+G7YiEw2WSsS1rYGbBCZhdLbfdIA0gcuaVP3sMFK0zGgk49nkIEOQH3nzGerSU+qYWARBclj0xJJEvrpSXLdMbd6z63pHLHngHTOtRXbWjnUq/k71m1Uae7OakqG0/amgjOfuG5z7/WwIDAQAB \
  ghcr.io/abstratium-dev/abstrauth:latest
```

e2e tests will work against this running image. see dev readme for tips on how to run them manually.

Delete test accounts as follows (which cascade deletes other data like federated identities, roles, credentials, authorization codes, etc.):

```
delete from T_accounts;
```

### Deploy to GitHub Container Registry

After building, the upload is based on https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry

Create a personal access token with `read:packages`, `write:packages` and `delete:packages`. (Settings > Developer Settings > Personal access token > Tokens (classic) > Generate new token). Select 30 days.

Export it as follows:

```
export CR_PAT=your_token_here
```

(alternatively add it to `/w/abstratium-abstrauth.env`)

Run the script named `push-docker-image.sh`, which also tags the source code and pushes it to GitHub.

----

## Overview

The project uses Quarkus 3.29.3 with Java 21 and must be deployed as a native image. We use a containerized build approach with Mandrel (Red Hat's downstream distribution of GraalVM) to create the native executable inside a Docker container.

## Prerequisites

- Docker installed and running
- Maven wrapper (included in project)
- Source code and dependencies

## Build Methods

### Method 1: Container Build (Recommended for CI/CD)

This method builds the native executable inside a container without requiring local GraalVM/Mandrel installation.

#### Using Maven

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

This command:
- Uses the Quarkus native profile (defined in `pom.xml`)
- Builds the native executable inside a Mandrel container
- Produces a Linux 64-bit executable at `target/abstrauth-1.0.0-SNAPSHOT-runner`
- Does not require GraalVM/Mandrel on your local Ubuntu system

#### Builder Image Configuration

By default, Quarkus uses the UBI 9 Mandrel builder image. You can configure this in `src/main/resources/application.properties`:

```properties
# Use Mandrel builder image (default)
quarkus.native.container-build=true
quarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21
```

Available builder images:
- **UBI 9 (default)**: `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`
- **UBI 8**: `quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21`

See available tags:
- [UBI 9 tags](https://quay.io/repository/quarkus/ubi9-quarkus-mandrel-builder-image?tab=tags)
- [UBI 8 tags](https://quay.io/repository/quarkus/ubi-quarkus-mandrel-builder-image?tab=tags)

#### Skipping Tests

To speed up the build, skip tests:

```bash
./mvnw package -Dnative -DskipTests -Dquarkus.native.container-build=true
```

### Method 2: Multi-Stage Docker Build (Recommended for Production)

This method builds everything inside Docker using a multi-stage build, producing a minimal final container image.

#### Step 1: Update .dockerignore

The default `.dockerignore` filters everything except `target/`. For a multi-stage build, we need to copy source files. Create a new `.dockerignore.multistage` file:

```
# .dockerignore.multistage
.git
.gitignore
.mvn/wrapper/maven-wrapper.jar
target/
!target/*-runner
```

Or temporarily modify `.dockerignore` to allow `src/` and other necessary files.

#### Step 2: Create Multi-Stage Dockerfile

Create `src/main/docker/Dockerfile.multistage`:

```dockerfile
## Stage 1: Build with Mandrel builder image
FROM quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21 AS build

# Copy Maven wrapper and dependencies
COPY --chown=quarkus:quarkus --chmod=0755 mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/

USER quarkus
WORKDIR /code

# Download dependencies (cached layer)
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline

# Copy source and build native executable
COPY --chown=quarkus:quarkus src /code/src
RUN ./mvnw package -Dnative -DskipTests

## Stage 2: Create minimal runtime image
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0

WORKDIR /work/

# Copy the native executable from build stage
COPY --from=build /code/target/*-runner /work/application

# Set up permissions for user 1001
RUN chmod 775 /work /work/application \
    && chown -R 1001 /work \
    && chmod -R "g+rwX" /work \
    && chown -R 1001:root /work

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

#### Step 3: Build the Docker Image

```bash
docker build -f src/main/docker/Dockerfile.multistage -t ghcr.io/abstratium-dev/abstrauth:latest .
```

#### Step 4: Run the Container

Make sure to source your environment file first: `source /w/abstratium-abstrauth.env`

```bash
docker run -i --rm -p 41080:8080 \
  --network abstratium \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://abstratium-mysql:3306/abstrauth \
  -e QUARKUS_DATASOURCE_USERNAME=abstrauth \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  -e OAUTH_GOOGLE_CLIENT_ID="${OAUTH_GOOGLE_CLIENT_ID}" \
  -e OAUTH_GOOGLE_CLIENT_SECRET="${OAUTH_GOOGLE_CLIENT_SECRET}" \
  -e SMALLRYE_JWT_SIGN_KEY="${SMALLRYE_JWT_SIGN_KEY}" \
  -e MP_JWT_VERIFY_PUBLICKEY="${MP_JWT_VERIFY_PUBLICKEY}" \
  ghcr.io/abstratium-dev/abstrauth:latest
```

### Method 3: Two-Stage Build (Build Then Package)

This is the traditional approach where you build the native executable first, then create a container image.

#### Step 1: Build Native Executable

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

#### Step 2: Build Container Image

Using the existing `Dockerfile.native-micro`:

```bash
docker build -f src/main/docker/Dockerfile.native-micro -t abstrauth:native .
```

Or using `Dockerfile.native`:

```bash
docker build -f src/main/docker/Dockerfile.native -t abstrauth:native .
```

The difference:
- `Dockerfile.native-micro`: Uses `quay.io/quarkus/ubi9-quarkus-micro-image:2.0` (smaller, ~50MB)
- `Dockerfile.native`: Uses `registry.access.redhat.com/ubi9/ubi-minimal:9.6` (larger, includes more tools)

## Verifying the Native Executable

After building, verify the native executable:

```bash
# Check file type
file target/abstrauth-1.0.0-SNAPSHOT-runner

# Expected output:
# target/abstrauth-1.0.0-SNAPSHOT-runner: ELF 64-bit LSB executable, x86-64, ...

# Check size
ls -lh target/abstrauth-1.0.0-SNAPSHOT-runner
```

## Running the Native Executable Locally

If you built with container-build, the executable is a Linux binary. On Ubuntu, you can run it directly:

```bash
# Set environment variables
source /w/abstratium-abstrauth.env

# Run the native executable
./target/abstrauth-1.0.0-SNAPSHOT-runner
```

## Build Performance Tips

1. **Cache Maven dependencies**: The multi-stage Dockerfile downloads dependencies in a separate layer, which is cached
2. **Skip tests**: Use `-DskipTests` for faster builds
3. **Use local Docker daemon**: Container builds are faster with local Docker than remote daemons
4. **Increase Docker resources**: Native image builds are memory-intensive (recommend 8GB+ RAM for Docker)

## Troubleshooting

### Issue: "Invalid Path entry" error

If you see:
```
Error: Invalid Path entry getting-started-1.0.0-SNAPSHOT-runner.jar
Caused by: java.nio.file.NoSuchFileException: /project/...
```

This happens with remote Docker daemons. Use:
```bash
./mvnw package -Dnative -Dquarkus.native.remote-container-build=true
```

### Issue: Native executable won't run on UBI 8

Starting with Quarkus 3.19+, the builder image is based on UBI 9. The native executable will not run on UBI 8 base images. Ensure your runtime base image is UBI 9 compatible.

### Issue: Out of memory during build

Increase Docker memory allocation:
```bash
# Check Docker memory
docker info | grep Memory

# Increase in Docker Desktop settings or daemon config
```

### Issue: Build is very slow

Native image builds are CPU and memory intensive. Typical build times:
- First build: 5-10 minutes
- Subsequent builds (with cache): 2-5 minutes

### Issue: SecureRandom in image heap error

If you see an error like:
```
Error: Detected an instance of Random/SplittableRandom class in the image heap.
Trace: Object was reached by
  trying to constant fold static field dev.abstratium.abstrauth.service.AuthorizationService.secureRandom
```

**Cause**: `SecureRandom` instances cannot be initialized at build time as static fields because they have cached seed values.

**Solution**: Change static fields to instance fields in your `@ApplicationScoped` beans:

```java
// ❌ Wrong - static field initialized at build time
private static final SecureRandom secureRandom = new SecureRandom();

// ✅ Correct - instance field initialized at runtime
private final SecureRandom secureRandom = new SecureRandom();
```

For non-CDI classes, you can alternatively use runtime initialization:
```properties
quarkus.native.additional-build-args=--initialize-at-run-time=com.example.YourClass
```

### Issue: Jackson serialization error - "No serializer found"

If you see an error like:
```
Jackson was unable to serialize type 'dev.abstratium.abstrauth.boundary.api.SignupResource$ErrorResponse'. 
Consider annotating the class with '@RegisterForReflection'
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: No serializer found for class ... 
and no properties discovered to create BeanSerializer
```

**Cause**: Inner classes used as response DTOs need reflection metadata for Jackson serialization in native images.

**Solution**: Add `@RegisterForReflection` annotation to response classes:

```java
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public static class ErrorResponse {
    public String error;
    public String error_description;
    
    public ErrorResponse(String error, String errorDescription) {
        this.error = error;
        this.error_description = errorDescription;
    }
}
```

This applies to all inner classes used as JSON response types in REST endpoints.

## Configuration Options

Add these to `src/main/resources/application.properties` to avoid specifying them every time:

```properties
# Enable container build by default
quarkus.native.container-build=true

# Specify container runtime (docker or podman)
quarkus.native.container-runtime=docker

# Use specific builder image
quarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21

# Additional native build options
quarkus.native.additional-build-args=-H:+ReportExceptionStackTraces,--initialize-at-run-time=org.eclipse.angus.mail.util.MailLogger
```

## Integration with CI/CD

For GitHub Actions or other CI systems:

```yaml
- name: Build Native Image
  run: |
    ./mvnw package -Dnative -DskipTests \
      -Dquarkus.native.container-build=true \
      -Dquarkus.native.container-runtime=docker

- name: Build Docker Image
  run: |
    docker build -f src/main/docker/Dockerfile.native-micro \
      -t abstrauth:${{ github.sha }} .
```

## Related Documentation

- [Quarkus Native Image Guide](https://quarkus.io/guides/building-native-image)
- [Quarkus Container Images Guide](https://quarkus.io/guides/container-image)
- [Mandrel Documentation](https://github.com/graalvm/mandrel)
- [UBI Images](https://catalog.redhat.com/software/containers/ubi9/ubi-minimal/615bd9b4075b022acc111bf5)

## Summary

**Recommended approach for development**: Method 1 (Container Build)
```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

**Recommended approach for production**: Method 2 (Multi-Stage Docker Build)
```bash
docker build -f src/main/docker/Dockerfile.multistage -t abstrauth:native .
```

Both methods use Mandrel inside Docker containers and do not require GraalVM/Mandrel installation on your local Ubuntu system.
