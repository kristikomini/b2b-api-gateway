package it.kristikomini.b2b.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One audited change. {@code changes} is the reflection-computed field diff as JSON, and
 * {@code traceId} ties the row back to the request that caused it (carried across the async
 * boundary), so an auditor can reconstruct "who changed what, when, in which request".
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenant;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "changes", nullable = false, columnDefinition = "text")
    private String changes;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected AuditLog() {
    }

    public AuditLog(String tenant, String entityName, String entityId, String changes, String traceId) {
        this.tenant = tenant;
        this.entityName = entityName;
        this.entityId = entityId;
        this.changes = changes;
        this.traceId = traceId;
        this.at = Instant.now();
    }

    public Long getId() { return id; }
    public String getTenant() { return tenant; }
    public String getEntityName() { return entityName; }
    public String getEntityId() { return entityId; }
    public String getChanges() { return changes; }
    public String getTraceId() { return traceId; }
    public Instant getAt() { return at; }
}
