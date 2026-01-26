# Development and Testing

## Update CLI

    jbang version --update
    jbang app install --fresh --force quarkus@quarkusio

## Server

First add env vars:

    source /w/abstratium-abstrauth.env

That file should contain:

    export OAUTH_GOOGLE_CLIENT_ID=...
    export OAUTH_GOOGLE_CLIENT_SECRET=GOCSPX-...
    export PASSWORD_PEPPER=... created using `openssl rand -base64 32`
    export ABSTRAUTH_CLIENT_SECRET=... also created using `openssl rand -base64 32`

The first couple of values are what you get when you configure a Google OAuth client in the Google Cloud Console. See the following website for details: https://developers.google.com/identity/protocols/oauth2

The application uses Quarkus. Run it with either `./mvnw quarkus:dev` or `quarkus dev` if you have installed the Quarkus CLI.

### Code coverage

    ./mvnw clean verify

Open the jacoco report from `target/jacoco-report/index.html`.

## Trouble Shooting

Use `ng serve` and accept the port it offers, in order to see the actual error messages that are occuring during the build, if you see the following error in Quarkus, and it shows a Quarkus page with the error message `Error restarting Quarkus` and `io.vertx.core.impl.NoStackTraceException`

    Error in Quinoa while running package manager 


## Database

The application uses a MySQL database. It expects a database to be running at `localhost:41040` with the user `root` and password `secret`.

Create the container, the database and user for abstrauth:

```bash
docker run -d \
    --restart unless-stopped \
    --name abstratium-mysql \
    --network abstratium \
    -e MYSQL_ROOT_PASSWORD=secret \
    -p 127.0.0.1:41040:3306 \
    -v /shared2/mysql-abstratium/:/var/lib/mysql:rw \
    mysql:9.3

# create the database and user for abstrauth
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -psecret


DROP USER IF EXISTS 'abstrauth'@'%';

CREATE USER 'abstrauth'@'%' IDENTIFIED BY 'secret';

DROP DATABASE IF EXISTS abstrauth;

CREATE DATABASE abstrauth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON abstrauth.* TO abstrauth@'%'; -- on own database

FLUSH PRIVILEGES;
```

exit, then reconnect using the abstrauth user:

```bash
docker run -it --network abstratium --rm mysql mysql -h abstratium-mysql --port 3306 -u abstrauth -psecret abstrauth
```

# Testing

## Unit and Integration Tests

Run unit tests (including angular tests):

    mvn test

Run all tests (unit + integration):

    mvn verify

## E2E Testing with Playwright

The E2E tests are in `e2e-tests/` and use Playwright to test the full application stack.

See the [E2E Testing Documentation](../e2e-tests/README.md) for detailed instructions.

It might be easier to test these manually during testing.

Start Abstrauth:
```bash
source /w/abstratium-abstrauth.env
quarkus dev
```

Start the client example:
```bash
cd client-example
npm start
```

And then the e2e tests:

```bash
cd e2e-tests
ALLOW_SIGNUP=true npx playwright test --ui
```

And then execute them manually by clicking the play button in the UI which opened.

# Upgrading

1. Update Quarkus:
```bash
jbang version --update
quarkus update
```

2. Update node/npm using nvm.
Search for nvm in all the docs in this project and update which version is used, e.g. v24.11.1

3. Update Angular:
```bash
cd src/main/webui
nvm use v24.11.1 
ng update
# or 
ng update @angular/cli @angular/core
```

4. Ensure that there are no nodejs vulnerabilities:
```bash
cd ../../../client-example
npm i
npm audit fix
```

5. Check Github for security problems by signing in and viewing the problems here: https://github.com/abstratium-dev/abstrauth/security/dependabot and https://github.com/abstratium-dev/abstrauth/security/code-scanning

6. Upgrading `nvm` means searching all places that use `v24.11.1` and updating it to the new version. Use `nvm` itself to install the latest stable version, but check Angular documentation for what a suitable version is.

# Issues with Webkit

For some strange reason, cookies aren't properly transported when testing localhost with Webkit (e.g. e2e tests, but also manual browser tests). If you sign out and try and sign in again and it doesn't pass the cookies properly and you remain on the sign in page.

The application works fine in production, so just don't test with Webkit locally.

# Building

See [NATIVE_IMAGE_BUILD.md](NATIVE_IMAGE_BUILD.md) for instructions on building a native image.