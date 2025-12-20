#!/bin/bash
# Start Quarkus with e2e profile (H2 database) for e2e tests
set -x  # Enable debug output
echo "Starting Quarkus for e2e tests..."
echo "Working directory: $(pwd)"
echo "ALLOW_SIGNUP: ${ALLOW_SIGNUP:-false}"
echo "Checking if jar exists: target/quarkus-app/quarkus-run.jar"
ls -lh target/quarkus-app/quarkus-run.jar || echo "JAR NOT FOUND!"

exec java -Dquarkus.profile=e2e -DALLOW_SIGNUP="${ALLOW_SIGNUP:-false}" -jar target/quarkus-app/quarkus-run.jar 2>&1
