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

It is EXTREMELY IMPORTANT that this project be tested using unit and integration tests. The aim is to have 80-90% coverage. The project should be developed in small steps, and in cycles of modifying the code, executing tests, fixing tests or modifying them as required, then going through the cycle again.