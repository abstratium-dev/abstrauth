#!/bin/bash
# Start Quarkus with e2e profile (H2 database) for e2e tests
set -x  # Enable debug output
echo "Starting Quarkus for e2e tests..."
echo "Working directory: $(pwd)"
echo "Checking if jar exists: target/quarkus-app/quarkus-run.jar"
ls -lh target/quarkus-app/quarkus-run.jar || echo "JAR NOT FOUND!"

exec java -Dquarkus.profile=e2e -jar target/quarkus-app/quarkus-run.jar 2>&1
