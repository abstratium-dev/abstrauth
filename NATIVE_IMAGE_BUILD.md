# Building Native Image with Docker and Mandrel

This document describes how to build the abstrauth OAuth server as a native executable using Docker with Mandrel, without requiring GraalVM or Mandrel to be installed on your local Ubuntu system.

## TL;DR

Use method 3:

```bash
./mvnw package -Dnative -DskipTests -Dquarkus.native.container-build=true
```

Once that completes, build the docker image:

```bash
docker build -f src/main/docker/Dockerfile.native-micro -t abstratium-abstrauth:native .
```

Then run the container (make sure to source your env file first: `source /w/abstratium-abstrauth.env`):

```bash
docker run -it --rm -p 41080:8080 \
  --network abstratium \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://abstratium-mysql:3306/abstrauth \
  -e QUARKUS_DATASOURCE_USERNAME=abstrauth \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  -e OAUTH_GOOGLE_CLIENT_ID="${OAUTH_GOOGLE_CLIENT_ID}" \
  -e OAUTH_GOOGLE_CLIENT_SECRET="${OAUTH_GOOGLE_CLIENT_SECRET}" \
  abstratium-abstrauth:native
```

TODO
  -e SMALLRYE_JWT_SIGN_KEY="${SMALLRYE_JWT_SIGN_KEY}" \
  -e MP_JWT_VERIFY_PUBLICKEY="${MP_JWT_VERIFY_PUBLICKEY}" \

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
docker build -f src/main/docker/Dockerfile.multistage -t abstrauth:native .
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
  abstratium-abstrauth:native
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
