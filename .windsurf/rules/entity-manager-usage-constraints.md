---
trigger: glob
globs: src/main/java/**/service/**/*.java
---

**No Bulk Operations with EntityManager.** Never use bulk `DELETE WHERE` or `UPDATE WHERE` operations (`executeUpdate()`, `CriteriaUpdate`, `CriteriaDelete`) because Hibernate Envers cannot audit them. Always load entities and use `em.remove()` or field updates within a transaction.

**Note on Tenant Filtering:** Hibernate discriminator-based multi-tenancy (which applies to entities with `@TenantId`) correctly applies tenant filters to:
- `em.find()` when hitting the database (adds `org_id = ?` to WHERE clause)
- JPQL SELECT/UPDATE/DELETE (adds `org_id = ?` to WHERE clause)

**Actual Security Risks related to multi-tenancy:**
- `em.getReference(id)` with user-supplied ID — returns a lazy proxy without hitting the database, so no tenant filter is applied. Used to establish foreign key relationships without loading the full entity, but dangerous if the ID comes from user input because the entity might belong to a different tenant
- `em.merge()` with manually set ID from external input — if you create a new entity instance, set its ID from user input, populate other fields, and call `em.merge()`, Hibernate will INSERT or UPDATE that row without verifying the ID belongs to the current tenant
