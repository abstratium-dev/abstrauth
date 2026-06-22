# Envers Auditing Guide

Guide for adding Hibernate Envers audit support to abstracore-based projects.

## Overview

This document describes how to implement entity auditing using Hibernate Envers in a Quarkus-based abstracore project. The implementation provides:

- Automatic audit tracking for all entity changes (create, update, delete)
- User attribution via custom revision entity
- Change notes for semantic context
- REST API for querying audit history

## Dependencies

Add the Quarkus Envers extension to `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-envers</artifactId>
</dependency>
```

## Entity Configuration

Annotate all auditable entities with `@Audited`:

```java
import org.hibernate.envers.Audited;

@Entity
@Table(name = "T_example")
@Audited
public class Example {
    @Id
    private String id;
    // ... fields
}
```

**Constraints:**
- All JPA entities in the project must be audited (enforced by project conventions)
- No bulk `DELETE WHERE` or `UPDATE WHERE` operations (see below)
- Audit queries cannot traverse relations (only ID constraints)

## Bulk Operations Constraint

Hibernate Envers captures changes by listening to entity lifecycle events (`@PrePersist`, `@PostUpdate`, `@PreRemove`, etc.). **Bulk operations bypass these events entirely**, meaning changes are made directly at the database level without Envers knowledge.

### What Is Not Supported

The following operations **will not generate audit records** and must be avoided:

**JPQL Bulk Operations:**
```java
// DANGER: No audit records created!
em.createQuery("DELETE FROM Example e WHERE e.status = :status")
    .setParameter("status", "obsolete")
    .executeUpdate();

// DANGER: No audit records created!
em.createQuery("UPDATE Example e SET e.active = false WHERE e.lastAccess < :date")
    .setParameter("date", cutoffDate)
    .executeUpdate();
```

**Criteria API Bulk Operations:**
```java
// DANGER: No audit records created!
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaDelete<Example> delete = cb.createCriteriaDelete(Example.class);
Root<Example> root = delete.from(Example.class);
delete.where(cb.equal(root.get("status"), "obsolete"));
em.createQuery(delete).executeUpdate();

// DANGER: No audit records created!
CriteriaUpdate<Example> update = cb.createCriteriaUpdate(Example.class);
Root<Example> root = update.from(Example.class);
update.set(root.get("active"), false);
update.where(cb.lessThan(root.get("lastAccess"), cutoffDate));
em.createQuery(update).executeUpdate();
```

**Native SQL Bulk Operations:**
```java
// DANGER: No audit records created!
em.createNativeQuery("DELETE FROM T_example WHERE status = 'obsolete'")
    .executeUpdate();
```

### Why This Happens

Envers registers Hibernate event listeners (`EnversPostInsertEventListenerImpl`, `EnversPostUpdateEventListenerImpl`, `EnversPostDeleteEventListenerImpl`) that capture entity state during the persistence context flush. Bulk operations:

1. Execute directly as SQL without loading entities into the persistence context
2. Do not trigger pre/post lifecycle callbacks
3. Skip the event listener chain entirely
4. Update/delete rows in the database without Hibernate tracking the changes

### The Solution: Iterative Operations

Replace bulk operations with iterative entity operations within a transaction:

```java
// CORRECT: Audit records created for each deletion
@Transactional
public void deleteObsoleteEntities(String status) {
    List<Example> toDelete = em.createQuery(
            "SELECT e FROM Example e WHERE e.status = :status", Example.class)
        .setParameter("status", status)
        .getResultList();

    for (Example entity : toDelete) {
        em.remove(entity);  // Triggers @PreRemove, Envers captures this
    }
}

// CORRECT: Audit records created for each update
@Transactional
public void deactivateOldEntities(Instant cutoffDate) {
    List<Example> toUpdate = em.createQuery(
            "SELECT e FROM Example e WHERE e.lastAccess < :date", Example.class)
        .setParameter("date", cutoffDate)
        .getResultList();

    for (Example entity : toUpdate) {
        entity.setActive(false);  // Triggers @PreUpdate, Envers captures this
    }
}
```

### Performance Considerations

Iterative operations are slower than bulk operations due to:
- Entity loading into persistence context (memory overhead)
- Individual SQL statements per entity
- Flush synchronization after each operation
- Audit record insertion for each change

For large datasets, implement batch processing:

```java
@Transactional
public void batchDeleteObsoleteEntities(String status, int batchSize) {
    while (true) {
        List<Example> batch = em.createQuery(
                "SELECT e FROM Example e WHERE e.status = :status", Example.class)
            .setParameter("status", status)
            .setMaxResults(batchSize)
            .getResultList();

        if (batch.isEmpty()) break;

        for (Example entity : batch) {
            em.remove(entity);
        }

        em.flush();
        em.clear();  // Clear persistence context to prevent memory issues
    }
}
```

### Validation

Static analysis or code review should verify:
- No `executeUpdate()` calls on bulk DELETE/UPDATE queries
- No `CriteriaUpdate` or `CriteriaDelete` usage
- No native SQL that modifies data outside entity lifecycle
- All deletions use `EntityManager.remove(entity)`
- All updates modify entity fields and rely on dirty checking

## Custom Revision Entity

Create a custom revision entity to capture user information and change notes:

```java
@Entity
@RevisionEntity(RevisionInfo.RevisionInfoListener.class)
@Table(name = "REVINFO")
public class RevisionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long rev;

    @RevisionTimestamp
    private Long revtstmp;

    private String username;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "change_note")
    private String changeNote;

    // Getters and setters...

    public static class RevisionInfoListener implements RevisionListener {
        @Override
        @ActivateRequestContext
        public void newRevision(Object revisionEntity) {
            RevisionInfo revisionInfo = (RevisionInfo) revisionEntity;
            revisionInfo.setRevtstmp(Instant.now().toEpochMilli());
            revisionInfo.setUsername(getCurrentUsername());
            revisionInfo.setChangeNote(getCurrentChangeNote());
        }

        private String getCurrentUsername() {
            try {
                SecurityIdentity identity = CDI.current().select(SecurityIdentity.class).get();
                if (identity != null && identity.getPrincipal() != null) {
                    return identity.getPrincipal().getName();
                }
            } catch (Exception e) {
                // No security context available
            }
            return "system";
        }

        private String getCurrentChangeNote() {
            try {
                ChangeNoteContext ctx = CDI.current().select(ChangeNoteContext.class).get();
                if (ctx != null && ctx.hasChangeNote()) {
                    return ctx.getChangeNote();
                }
            } catch (Exception e) {
                // No request context available
            }
            return null;
        }
    }
}
```

## Change Note Pattern

Implement a request-scoped context to capture change notes from REST calls:

```java
@RequestScoped
public class ChangeNoteContext {
    private String changeNote;

    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public boolean hasChangeNote() { return changeNote != null && !changeNote.isBlank(); }
}
```

Create an interceptor to validate and capture change notes:

```java
@Interceptor
@RequiresChangeNote
@Priority(Interceptor.Priority.APPLICATION)
public class ChangeNoteInterceptor {

    @Context
    UriInfo uriInfo;

    @Inject
    ChangeNoteContext changeNoteContext;

    @ConfigProperty(name = "change.note.mandatory", defaultValue = "true")
    boolean changeNoteMandatory;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        String changeNote = uriInfo.getQueryParameters().getFirst("changeNote");

        if (changeNoteMandatory && (changeNote == null || changeNote.isBlank())) {
            throw HttpProblem.builder()
                .withStatus(Response.Status.BAD_REQUEST)
                .withTitle("Bad Request")
                .withDetail("Missing required query parameter: changeNote")
                .build();
        }

        if (changeNote != null && !changeNote.isBlank()) {
            changeNoteContext.setChangeNote(changeNote);
        }

        return ctx.proceed();
    }
}
```

```java
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresChangeNote {
}
```

Apply the annotation to mutating REST endpoints:

```java
@POST
@RequiresChangeNote
public EntityDto create(EntityDto request) { ... }

@PUT
@Path("/{id}")
@RequiresChangeNote
public EntityDto update(@PathParam("id") String id, EntityDto request) { ... }

@DELETE
@Path("/{id}")
@RequiresChangeNote
public Response delete(@PathParam("id") String id) { ... }
```

## Database Schema

Create migration scripts for Envers tables. Example patterns:

**REVINFO table:**
```sql
CREATE TABLE REVINFO (
    REV BIGINT AUTO_INCREMENT PRIMARY KEY,
    REVTSTMP BIGINT,
    username VARCHAR(255),
    correlation_id VARCHAR(255),
    change_note VARCHAR(255)
);

CREATE INDEX I_revinfo_timestamp ON REVINFO(REVTSTMP);
CREATE INDEX I_revinfo_correlation_id ON REVINFO(correlation_id);
CREATE INDEX I_revinfo_change_note ON REVINFO(change_note);
```

**Entity audit table (one per audited entity):**
```sql
CREATE TABLE T_example_AUD (
    id VARCHAR(36),           -- Entity primary key
    -- All entity columns (nullable)
    column1 VARCHAR(255),
    column2 BOOLEAN,
    -- Envers columns
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,          -- 0=ADD, 1=MOD, 2=DEL
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_example_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

-- Indices for performance
CREATE INDEX I_example_aud_rev ON T_example_AUD(REV);
CREATE INDEX I_example_aud_id ON T_example_AUD(id);
```

## Test Cleanup

Integration tests using `@QuarkusTest` must clean up test data to ensure test isolation. The standard pattern uses `UserTransaction` for programmatic transaction control in `@AfterEach` methods.

### Standard Cleanup Pattern

Most tests use bulk delete queries in reverse dependency order to clean up all test data:

```java
@QuarkusTest
class ExampleResourceTest {

    @Inject
    EntityManager em;

    @Inject
    UserTransaction userTransaction;

    @AfterEach
    void cleanup() throws Exception {
        userTransaction.begin();
        try {
            // Delete in reverse dependency order to avoid FK violations
            em.createQuery("DELETE FROM Criterion").executeUpdate();
            em.createQuery("DELETE FROM ToggleStageRule").executeUpdate();
            em.createQuery("DELETE FROM Rule").executeUpdate();
            em.createQuery("DELETE FROM Toggle").executeUpdate();
            em.createQuery("DELETE FROM Stage").executeUpdate();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }
}
```

**Note:** While bulk operations bypass Envers auditing (see [Bulk Operations Constraint](#bulk-operations-constraint)), this is acceptable for test cleanup since audit trail preservation in tests is not required.

### Setup with Pre-Created Data

When tests need pre-existing data, use `@BeforeEach` with the same transaction pattern:

```java
@BeforeEach
void setUp() throws Exception {
    userTransaction.begin();
    try {
        testEntity = new Example();
        testEntity.setName("test-data");
        em.persist(testEntity);
        em.flush();
        userTransaction.commit();
    } catch (Exception e) {
        userTransaction.rollback();
        throw e;
    }
}
```

### Helper Method for Data Setup

A common pattern is a `commitData` helper that wraps setup code in a transaction:

```java
private void commitData(Runnable setup) throws Exception {
    userTransaction.begin();
    try {
        setup.run();
        em.flush();
        userTransaction.commit();
    } catch (Exception e) {
        userTransaction.rollback();
        throw e;
    }
}

// Usage in test methods:
@Test
void testSomething() throws Exception {
    commitData(() -> {
        Toggle toggle = new Toggle();
        toggle.setName("test-toggle");
        em.persist(toggle);
    });

    // Test code here...
}
```

### Selective Entity Cleanup

For tests that create specific entities needing individual cleanup:

```java
@AfterEach
void tearDown() throws Exception {
    userTransaction.begin();
    try {
        // Clean up specific entity if it was created
        if (createdEntity != null && createdEntity.getId() != null) {
            Example entity = em.find(Example.class, createdEntity.getId());
            if (entity != null) {
                em.remove(entity);
            }
        }
        userTransaction.commit();
    } catch (Exception e) {
        userTransaction.rollback();
        throw e;
    }
}
```

### Cleanup Order Considerations

When defining entity deletion order, respect foreign key constraints:

1. Delete child/junction entities first (e.g., `Criterion`, `ToggleStageRule`)
2. Delete independent entities last (e.g., `Stage`)
3. Use `em.find()` + `em.remove()` for entity-specific cleanup that triggers Envers
4. Use bulk `DELETE FROM` for comprehensive test cleanup (skips Envers, but acceptable for tests)

## History Service

Implement a service to query audit data using `AuditReader`:

```java
@ApplicationScoped
public class HistoryService {

    @Inject
    EntityManager em;

    public List<HistoryEntryDto> searchHistory(String search, int limit, int offset) {
        // Query REVINFO with optional filtering on username/changeNote
    }

    public List<HistoryChangeDto> getRevisionDetails(long rev) {
        AuditReader reader = AuditReaderFactory.get(em);
        // Query entities modified at specific revision
    }

    public List<EntityRevisionDto> getEntityHistory(String table, String entityId) {
        AuditReader reader = AuditReaderFactory.get(em);
        // Query all revisions of a specific entity
    }
}
```

## History API Endpoints

```java
@Path("/api/history")
public class HistoryResource {

    @GET
    public List<HistoryEntryDto> searchHistory(
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) { ... }

    @GET
    @Path("/{rev}")
    public List<HistoryChangeDto> getRevisionDetails(@PathParam("rev") long rev) { ... }

    @GET
    @Path("/entity/{table}/{entityId}")
    public List<EntityRevisionDto> getEntityHistory(
            @PathParam("table") String table,
            @PathParam("entityId") String entityId) { ... }
}
```

## Native Image Considerations

When building as a GraalVM native image:

1. Ensure all audit entity classes are registered for reflection
2. Use `@ActivateRequestContext` on the `RevisionListener` method
3. Test audit functionality in native mode (`mvn verify -Pnative`)

## Testing

Write `@QuarkusTest` integration tests that:

1. Create entities via REST with `changeNote` query parameter
2. Verify history entries are recorded
3. Test history search by username and change note
4. Verify entity revision history retrieval

Example test pattern:
```java
@QuarkusTest
class HistoryResourceTest {

    @Test
    @TestSecurity(user = "test@example.com", roles = {"user"})
    void testCreateAndRetrieveHistory() {
        // Create entity
        given()
            .queryParam("changeNote", "Creating test entity")
            .body(Map.of("name", "test-entity"))
            .post("/api/entities")
            .then()
            .statusCode(200);

        // Search history
        given()
            .queryParam("search", "test@example.com")
            .get("/api/history")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }
}
```

## Migration Ordering

Create audit table migrations after entity tables:

1. `V01.001__create_entity_tables.sql` - Create main entity tables
2. `V01.007__create_table_revinfo.sql` - Create revision info table
3. `V01.008__create_table_entity_aud.sql` - Create audit tables per entity
4. `V01.017__add_history_indices.sql` - Add performance indices

## See Also

- [DATABASE.md](DATABASE.md) - Database schema documentation
- [DESIGN.md](DESIGN.md) - System design including audit architecture
