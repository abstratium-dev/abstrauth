#!/bin/bash

# Script to push Docker image to GitHub Container Registry
# This script:
# 1. Logs into the container registry using a personal access token
# 2. Pushes the Docker image with the build version tag
# 3. Cleans up authentication credentials for security

set -e

# Extract version from application.properties
# The version is injected by Maven during build in ISO-8601 format (yyyyMMddHHmmss)
VERSION=$(grep '^build.version=' target/classes/application.properties | cut -d'=' -f2)

if [ -z "$VERSION" ]; then
    echo "Error: Could not extract version from application.properties"
    echo "Make sure the application has been built with Maven first"
    exit 1
fi

echo "Using version: $VERSION"

# Login to the container registry
# CR_PAT environment variable must be set with a GitHub Personal Access Token
# that has write:packages permission
echo "Logging into GitHub Container Registry..."
echo $CR_PAT | docker login ghcr.io -u abstratium-dev --password-stdin

# Push the image with the version tag and latest tag
echo "Pushing Docker images..."
docker push ghcr.io/abstratium-dev/abstrauth:$VERSION
docker push ghcr.io/abstratium-dev/abstrauth:latest

# Clean up so that malicious scripts cannot access tokens saved during docker login
# This removes the Docker configuration file containing authentication credentials
echo "Cleaning up authentication credentials..."
rm /home/ant/.docker/config.json

echo "Successfully pushed:"
echo "  - ghcr.io/abstratium-dev/abstrauth:$VERSION"
echo "  - ghcr.io/abstratium-dev/abstrauth:latest"

# Tag the git commit with the version from application.properties
git tag $VERSION
git push origin $VERSION

