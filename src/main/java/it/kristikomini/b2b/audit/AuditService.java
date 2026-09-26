package it.kristikomini.b2b.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit rows <b>off the request thread</b> ({@code @Async}). Auditing must never
 * slow down or fail the business transaction, so it runs after the fact on the audit
 * executor, in its own transaction. The {@code traceId} is passed in explicitly because it
 * was captured on the request thread and carried across by the executor's task decorator.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String tenant, String entityName, String entityId, String changesJson, String traceId) {
        try {
            repository.save(new AuditLog(tenant, entityName, entityId, changesJson, traceId));
        } catch (RuntimeException e) {
            // A failed audit write is logged, not propagated — it must not undo the business change.
            log.error("failed to write audit row for {}#{} (trace={})", entityName, entityId, traceId, e);
        }
    }
}
