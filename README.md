# abstrauth

A minimum implementation of an oauth authorization server capable of serving multiple applications at same time.
It uses JWT for authentication and authorization, signed with a public/private key pair so that third-party
applications can validate the tokens and roles without calls to the authorization server.

It coincidentally also uses itself as an authorization server for users signing into the admin UI.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

It was generated using `quarkus create app --maven --package-name=dev.abstratium.abstrauth --java=21 --name abstrauth dev.abstratium:abstrauth`

## Documentation

- [OAuth 2.0 Authorization Flows](FLOWS.md)
- [Federated Login](FEDERATED_LOGIN.md)
- [Database](DATABASE.md)

## Development

### Update CLI

    jbang version --update
    jbang app install --fresh --force quarkus@quarkusio

### Server

The application uses Quarkus. Run it with either `./mvnw quarkus:dev` or `quarkus dev` if you have installed the Quarkus CLI.

#### Code coverage

    ./mvnw clean verify

Open the jacoco report from `target/jacoco-report/index.html`.


### Database

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


## TODO

### Now

- fix tests
- add playwright tests
  - https://docs.quarkiverse.io/quarkus-quinoa/dev/testing.html
  - https://docs.quarkiverse.io/quarkus-playwright/dev/
- add http interceptor in angular app to add token to requests
- add RolesAllowed to rest endpoints that need it
- add ability to add and manage applications using a UI and rest endpoints
- add roles. search for generateAccessToken or "user"
- add google login
- do security scan using claude
  - double check that pkce is properly implemented, especially storing the code challenge in session storage
  - find out how best to store the token, e.g. so that hledger can use it

### Later

- make aesthetics better and document colours, etc. below
- add optional env vars that allow initial sign in without registration, so that that user has admin role
- show how to make the client verify the signature of the token
- use an enum for the status on authorization requests - see sql 004 for values
- make authorization requests become expired
- state needs to work if other third party apps want to use it
- clean up authorization requests after a month
- add microsoft login
- add github login
- add using refresh tokens
- add a nonce (number used once) to the authorization request
- make issuer depend on redirect url?
- split redirect urls into own table or just up to 5 cols?
- check db columns and add functionality for them / check that they are used properly
  - T_accounts.email_verified
  - T_authorization_codes.used
  - T_authorization_codes.expires_at
  - T_authorization_requests.status
  - T_authorization_requests.expires_at
  - T_credentials.failed_login_attempts
  - T_credentials.locked_until
- logout (revocation?)
- don't allow multiple credentials for one account (uk on foreign key?)
- don't allow multiple auth requests (or codes?) for same client and account - remove if there are duplicates?
- how to build native
- github build? https://github.com/abstratium-dev/abstrauth/new/main?filename=.github%2Fworkflows%2Fmaven.yml&workflow_template=ci%2Fmaven
  - and then show results in github
- document production setup
- autocomplete: https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#attr-fe-autocomplete-username
- [x] check using https://oauthdebugger.com/

# Aesthetics

## favicon

https://favicon.io/favicon-generator/ - text based

Text: a
Background: rounded
Font Family: Leckerli One
Font Variant: Regular 400 Normal
Font Size: 110
Font Color: #FFFFFF
Background Color: #5c6bc0


----

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/abstrauth-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

