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
- `em.merge()` with manually set ID from external input — if you call `em.merge()` on a detached entity with a user-supplied ID **without first verifying tenant ownership**, Hibernate may INSERT or UPDATE a row belonging to a different tenant. **Safe pattern for updates:** call `em.find(id)` first (which applies the tenant filter and returns `null` for cross-tenant IDs), verify the result is non-null, then call `em.merge()` with the incoming data. This verifies tenant ownership before any mutation and avoids stale code when new fields are added to the entity.
- `em.remove(entity)` on a detached entity with user-supplied ID — only call `em.remove()` on entities that were loaded via `em.find()` or JPQL within the same transaction, so the tenant filter has already been applied.

**Native SQL:**
- never use native SQL in production code

