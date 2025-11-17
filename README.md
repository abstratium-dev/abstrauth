# abstrauth

A minimum implementation of an oauth authorization server capable of serving multiple applications at same time.
It uses JWT for authentication and authorization, signed with a public/private key pair so that third-party applications
can validate the tokens and roles without calls to the authorization server.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

It was generated using `quarkus create app --maven --package-name=dev.abstratium.abstrauth --java=21 --name abstrauth dev.abstratium:abstrauth`

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Documentation

- [OAuth 2.0 Authorization Flows](FLOWS.md)
- [Federated Login](FEDERATED_LOGIN.md)

## Development

### Update CLI

    jbang version --update
    jbang app install --fresh --force quarkus@quarkusio

### Server

The application uses Quarkus. Run it with either `./mvnw quarkus:dev` or `quarkus dev` if you have installed the Quarkus CLI.

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

    CREATE USER 'abstrauth'@'%' IDENTIFIED BY 'secret';
    CREATE DATABASE abstrauth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    GRANT ALL PRIVILEGES ON abstrauth.* TO abstrauth@'%'; -- on own database

    FLUSH PRIVILEGES;

exit, then reconnect using the abstrauth user:

    docker run -it --network abstratium --rm mysql mysql -h abstratium-mysql --port 3306 -u abstrauth -psecret abstrauth


## TODO

- deal with log entry "The smallrye-jwt extension has configured an in-memory key pair, which is not enabled in production. Please ensure the correct keys/locations are set in production to avoid potential issues."
- how to build native
- document production setup

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
