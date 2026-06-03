---
trigger: glob
description: when working with quarkus (the back end server) or anything to do with java, business logic in the server
globs: src/main/java/**/.*,src/test/java/**/.*
---

The backend is built using the boundary/controller/entity pattern. Rest resources are in the src/main/java/dev/abstratium/abstrauth/boundary folder. "controllers" (simply named "services", which is where the main business logic is found, are in the service package in classes with the postfix "Service". They are responsible for starting transactions. JPA entities are in the "entity" package. Sub-packages within those three main packages are used to group things that are related functionally.  Other top level packages like "filter", "helper", "util", etc. are also fine for technical cross-cutting concerns.

IT IS REALLY IMPORTANT THAT THE REST RESOURCES DO NOT EXECUTE LOGIC WITHIN MULTIPLE TRANSACTIONS! Because if they did, it's possible that the data would become inconsistent if there was a failure in the second transaction.

Tests are run using `mvn test` or `mvn verify -Pe2e` - goals are 80% statement coverage and 70% branch coverage.