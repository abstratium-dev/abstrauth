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

The values are what you get when you configure a Google OAuth client in the Google Cloud Console. See the following website for details: https://developers.google.com/identity/protocols/oauth2

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

exit, then reconnect using the abstrauth user:

    docker run -it --network abstratium --rm mysql mysql -h abstratium-mysql --port 3306 -u abstrauth -psecret abstrauth


# Testing

## Unit and Integration Tests

Run unit tests (including angular tests):

    mvn test

Run all tests (unit + integration):

    mvn verify

## E2E Testing with Playwright

The E2E tests are in `e2e-tests/` and use Playwright to test the full application stack.

See the [E2E Testing Documentation](../e2e-tests/README.md) for detailed instructions.

# Building

See [NATIVE_IMAGE_BUILD.md](NATIVE_IMAGE_BUILD.md) for instructions on building a native image.