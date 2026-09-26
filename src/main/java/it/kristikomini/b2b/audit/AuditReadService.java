package it.kristikomini.b2b.audit;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Loads the "before" image of an entity for the audit aspect, in its <b>own</b> read-only
 * transaction ({@code REQUIRES_NEW}). Running in a separate transaction is deliberate: it
 * reads the committed state as it was <i>before</i> the audited method's transaction mutates
 * it, and it does not depend on an already-open session at the point the aspect runs.
 */
@Service
public class AuditReadService {

    private final EntityManager entityManager;

    public AuditReadService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, String> snapshot(Class<?> entityClass, Object id) {
        Object entity = entityManager.find(entityClass, id);
        return FieldSnapshotter.snapshot(entity);
    }
}
