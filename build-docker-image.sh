#!/bin/bash

# Script to build Docker image for Abstrauth OAuth Server
# This script:
# 1. Builds the native executable using Maven with container build
# 2. Extracts the build version from application.properties
# 3. Builds the Docker image with version and latest tags

set -e

echo "Building native executable..."
# ./mvnw package -Dnative -Dquarkus.native.container-build=true
# secure random issues: https://quarkus.io/guides/native-reference
./mvnw package -Dnative -Dquarkus.native.container-build=true -Dquarkus.native.additional-build-args="--trace-object-instantiation=java.security.SecureRandom"

# Extract version from application.properties
# The version is injected by Maven during build in ISO-8601 format (yyyyMMddHHmmss)
VERSION=$(grep '^build.version=' target/classes/application.properties | cut -d'=' -f2)

if [ -z "$VERSION" ]; then
    echo "Error: Could not extract version from application.properties"
    echo "Make sure the Maven build completed successfully"
    exit 1
fi

echo "Build version: $VERSION"

# Build the Docker image with version tag and latest tag
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.native-micro \
    -t ghcr.io/abstratium-dev/abstrauth:$VERSION \
    -t ghcr.io/abstratium-dev/abstrauth:latest \
    .

echo ""
echo "Successfully built Docker images:"
echo "  - ghcr.io/abstratium-dev/abstrauth:$VERSION"
echo "  - ghcr.io/abstratium-dev/abstrauth:latest"
echo ""
echo "To push to GitHub Container Registry, run: ./push-docker-image.sh"
