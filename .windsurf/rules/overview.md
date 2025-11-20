---
trigger: always_on
---

The basis of this project are the following RFCs:

- https://datatracker.ietf.org/doc/html/rfc6749
- https://datatracker.ietf.org/doc/html/rfc6749 and 
- https://datatracker.ietf.org/doc/rfc6819/
- https://datatracker.ietf.org/doc/rfc9700/

The aim of the project is to implement an oauth authorization server which is kept as simple as possible for only the following flows used by the company named abstratium:

- Authorization Code Flow
- Authorization Code Flow with Proof Key for Code Exchange (PKCE)

The project uses the quarkus framework with Java 21. The extensions in use are:

- Hibernate ORM [quarkus-hibernate-orm] Define your persistent model with Hibernate ORM and Jakarta Persistence
- Flyway [quarkus-flyway] Handle your database schema migrations
- JDBC Driver - MySQL [quarkus-jdbc-mysql] Connect to the MySQL database via JDBC
- quinoa for integrating an angular UI into the server
- quarkus-rest-jackson and quarkus-rest for transporting json
- SmallRye JWT [quarkus-smallrye-jwt] Secure your applications with JSON Web Token
- SmallRye JWT Build [quarkus-smallrye-jwt-build] Create JSON Web Token with SmallRye JWT Build API
- quarkus-smallrye-openapi used to document the ui

It MUST be deployed as a native image, so it may only use java constructs that are capable of being built into a native image.

It is EXTREMELY IMPORTANT that this project be tested using unit and integration tests. The aim is to check that coverage is at 80-90%, in order to find missing tests. Do not write senseless tests just to increase the coverage. Coverage can be measured using `mvn verify` and reading coverage results in xml files from the folder `target/jacoco-report`. The project should be developed in small steps, and in cycles of modifying the code, executing tests, fixing tests or modifying them as required, then going through the cycle again. Only tests annotated with `@QuarkusTest` are counted towards coverage. These are the primary kind of test for this project. You can however write plain unit tests with that annotation, in order to test edge cases. Never disable tests e.g. with the @org.junit.jupiter.api.Disabled annotation. Never delete tests just because you cannot make them work.

The documentation in markdown files with the extension m̀d` need to be checked to ensure that it too is up to date.

Only create markdown documents if they are explicitly requested. Don't create them to simply document what you have just done.