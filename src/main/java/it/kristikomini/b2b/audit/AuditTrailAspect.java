package it.kristikomini.b2b.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.kristikomini.b2b.annotation.AuditTrail;
import it.kristikomini.b2b.security.TenantResolver;
import it.kristikomini.b2b.web.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Implements {@link AuditTrail}: snapshot the entity before, run the method, snapshot after,
 * diff by reflection, and record the change set asynchronously with the request's traceId.
 *
 * <p>Why this is more than a logging call:
 * <ul>
 *   <li>The before-image is read in a separate transaction ({@link AuditReadService}), so it
 *       reflects committed state, not the half-mutated in-flight entity.</li>
 *   <li>The diff is generic ({@link FieldSnapshotter}) — the same aspect audits any entity.</li>
 *   <li>Only actual changes are written, and the write is off-thread so auditing never slows
 *       or breaks the business transaction.</li>
 * </ul>
 *
 * <p><b>Proxy gotcha:</b> this is a Spring-AOP proxy aspect, so it only fires when the
 * annotated method is invoked <i>through</i> the bean's proxy. A call to {@code this.method()}
 * from inside the same bean bypasses the proxy and is NOT audited — see {@code docs} and the
 * self-invocation test.
 */
@Aspect
@Component
@Order(10) // outside the business transaction: the after-image reflects committed state
public class AuditTrailAspect {

    private final AuditReadService auditReadService;
    private final AuditService auditService;
    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    public AuditTrailAspect(AuditReadService auditReadService, AuditService auditService,
                            TenantResolver tenantResolver, ObjectMapper objectMapper) {
        this.auditReadService = auditReadService;
        this.auditService = auditService;
        this.tenantResolver = tenantResolver;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditTrail)")
    public Object audit(ProceedingJoinPoint pjp, AuditTrail auditTrail) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Class<?> entityClass = signature.getReturnType();
        Object id = firstIdArgument(pjp.getArgs());

        // Before-image (committed state), read generically by reflection.
        Map<String, String> before = (id == null)
                ? Map.of()
                : auditReadService.snapshot(entityClass, id);

        Object result = pjp.proceed();

        Map<String, String> after = FieldSnapshotter.snapshot(result);
        Map<String, String[]> changes = FieldSnapshotter.diff(before, after);

        if (!changes.isEmpty()) {
            String entityName = auditTrail.entity().isBlank() ? entityClass.getSimpleName() : auditTrail.entity();
            auditService.record(
                    tenantResolver.currentTenant(),
                    entityName,
                    id == null ? "?" : id.toString(),
                    objectMapper.writeValueAsString(changes),
                    MDC.get(TraceContext.TRACE_ID));
        }
        return result;
    }

    /** The audited method's entity id — the first numeric argument (the {@code id} parameter). */
    private static Object firstIdArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Number) {
                return arg;
            }
        }
        return null;
    }
}
