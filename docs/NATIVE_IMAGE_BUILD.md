# Building Native Image with Docker and Mandrel

This document describes how to build the abstrauth OAuth server as a native executable using Docker with Mandrel, without requiring GraalVM or Mandrel to be installed on your local Ubuntu system.

## TL;DR

Commit everything to git.

Then:

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
  -e OAUTH_MICROSOFT_CLIENT_ID="${OAUTH_MICROSOFT_CLIENT_ID}" \
  -e OAUTH_MICROSOFT_CLIENT_SECRET="${OAUTH_MICROSOFT_CLIENT_SECRET}" \
  -e CSRF_TOKEN_SIGNATURE_KEY="asdfasdfasdfasdf" \
  -e ALLOW_SIGNUP=false \
  -e ALLOW_NATIVE_SIGNIN=true \
  -e OAUTH_GOOGLE_REDIRECT_URI="http://localhost:8080/oauth2/callback/google" \
  -e PASSWORD_PEPPER="${PASSWORD_PEPPER}" \
  -e QUARKUS_MANAGEMENT_HOST=0.0.0.0 \
  -e ABSTRAUTH_CLIENT_SECRET=qzzRSarGgFFRCz3omjvxdkTHfnlibAjG \
  -e QUARKUS_OIDC_BFF_AUTH_SERVER_URL=http://localhost:8080 \
  -e QUARKUS_OIDC_BFF_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME=false \
  -e ABSTRAUTH_WARNING_MESSAGE="You are in a development environment" \
  -e ABSTRAUTH_EMAIL_ENABLED=false \
  -e SMALLRYE_JWT_SIGN_KEY=MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDBvM+riIoEaxItLtOU0or3kQ3P9Om7pN4NmM32ONMJxHSn6n+GpineEGeCdyFcE5OkRF0a6BrDANXDUfhGQccnN6u6BalIebF0pVBpyra+i9Wcpbb1dixZgm6cgROm1ZDqSfIGnx48PsMqHM/EE6LLHeiB8V/bdmd01eiRg/LAp8q7BZJGBlDZUNmedP/bkcU8szBT8X6ZD4btiITDZZKxLWtgZsEJmF0tt90gDSBy5pU/ewwUrTMaCTj2eQgQ5AfefMZ6tJT6phYBEFyWPTEkkS+ulJct0xt3rPrekcseeAdM61FdtaOdSr+TvWbVRp7s5qSobT9qaCM5+4bnPv9bAgMBAAECggEAAJfcEuxiGngGyuNKe+Qrz2zpm+oQunsGFbM9aN7tASn8KXTLBdXbFEunOtEJOxzxkMkyInOffAVeojB4ECRXaxlSifPxJsBTTht2JDzIqSCzJhL5J8Xq24NOD2XzHMmpSJkICAPTYIqDUnewHdY+41xTapfF8V1qx613/tS77jcT8Qar556YTccJu0qgwNlrapjQYRvV5wT2uV4JqYaN1vC3tNXlIIsCGNR2CN9S/s6jp/gOE+eqfCbiuON1jCbXbN0U1TL4WKj6OYsOiA27hGCJ6n17gMBou+t8T8MccuceV9Lb8ecXB6h/oUok9gqZ57om8dGVoP4hZaOPsSWXwQKBgQDwRMCmunAe9d64yoUQYqmfHKtW584ltIr4do0hpaOsGXTjmrueG10BhFXPlDYoU1BWRezTQXVxWcPoLeIFFA511H22nRQ4g/dvBibCUr8B33jpGfbUq1baMrp2GN2KYdVbChEtrWPGNZCtXeux9sLuoDZVXbakdr4frAEDna+Q0QKBgQDObCG2cW8rdbh+0BOZY2x82titS4uE4Nx8LpF2Yl8zyFa/n+eRyXzhZXtQvEka04IX7SqncOMPYPen0pCAgd3DvoGTVcxRB0d2sCJ9oY++A4kEoUprddcQXwhWGqPTATosVbr3V2XwAx58yvG0odZBUN466shR7FEQmzbmviP4awKBgQDCx2nKgC/u2XHSKtPOob1SsQIx9L/JD2Dt5eWp1kcmeIirD0Bz/0jZtvd9zXBOJqRlHFDOPi3AU34fFjs51LWYTkgPp63B1zHa/oijVkNkeE7j4dmZNMG3KBLDNIs86Oz23eVpOzw8biY4dYBiiGIk4xrI/6zWDTE6Kc20qbuvUQKBgGYoWZ7jELOfdQk9jRWSgPRhkm5hPtEqP7Qtj8vY72i/Mz9usboSz3z1LkxMgpmGJ5ITy9JGKflIcghaSy1uGARx2crC4XUQdyukC83FEVBmi38BG8WG8kKl5YhHcuBQcSvT2c3jMQ3RXVtBTNGqblCw5uqdmzoADDZ9unQDkeW1AoGBALy9D8nFqZQB9i4fPeve9kTLd4wgyB8KrpTGtKnIcDOqjQcA2DA+mG/vzuAxP2ie4/QnCPAxzHtmLTKMAjzxNHgB+6zGYT+zBOgFdWqfUHz14DisHoSICkwaKluTBeVZYakQb0g57TvtfagvNf9ADuiCorbYot7yxoG+IRCDCauw \
  -e MP_JWT_VERIFY_PUBLICKEY=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwbzPq4iKBGsSLS7TlNKK95ENz/Tpu6TeDZjN9jjTCcR0p+p/hqYp3hBngnchXBOTpERdGugawwDVw1H4RkHHJzerugWpSHmxdKVQacq2vovVnKW29XYsWYJunIETptWQ6knyBp8ePD7DKhzPxBOiyx3ogfFf23ZndNXokYPywKfKuwWSRgZQ2VDZnnT/25HFPLMwU/F+mQ+G7YiEw2WSsS1rYGbBCZhdLbfdIA0gcuaVP3sMFK0zGgk49nkIEOQH3nzGerSU+qYWARBclj0xJJEvrpSXLdMbd6z63pHLHngHTOtRXbWjnUq/k71m1Uae7OakqG0/amgjOfuG5z7/WwIDAQAB \
  -e DEFAULT_ORG_UUID="${DEFAULT_ORG_UUID}" \
  -e ABSTRAUTH_BASE_URL="http://localhost:8080" \
  ghcr.io/abstratium-dev/abstrauth:latest
```

e2e tests will work against this running image. see DEVELOPMENT_AND_TESTING.md for tips on how to run them manually.

Delete test data by deleting and recreating the test database as follows:

```
DROP USER IF EXISTS 'abstrauth'@'%';

CREATE USER 'abstrauth'@'%' IDENTIFIED BY 'secret';

DROP DATABASE IF EXISTS abstrauth;

CREATE DATABASE abstrauth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON abstrauth.* TO abstrauth@'%'; -- on own database

FLUSH PRIVILEGES;
USE abstrauth;
```

### Deploy to GitHub Container Registry

After building, the upload is based on https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry

Create a personal access token with `read:packages`, `write:packages` and `delete:packages`. (Settings > Developer Settings > Personal access token > Tokens (classic) > Generate new token classic). Select 30 days.

Export it as follows:

```
export CR_PAT=your_token_here
```

(alternatively add it to `/w/abstratium-abstrauth.env`)

IMPORTANT: to run the following script you must be signed into GitHub and have the env var exported.

Run the script named `./push-docker-image.sh`, which also tags the source code and pushes it to GitHub.

You are now finished. Re-install in test and production environments.
