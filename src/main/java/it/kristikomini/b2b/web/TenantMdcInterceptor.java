package it.kristikomini.b2b.web;

import it.kristikomini.b2b.security.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Puts the caller's tenant into the MDC for the duration of the request, so every log line —
 * and the audit rows written for it — carry both {@code traceId} and {@code tenant}. Runs as
 * an interceptor (after the security filters) because the tenant only exists once the JWT has
 * been validated.
 */
public class TenantMdcInterceptor implements HandlerInterceptor {

    private final TenantResolver tenantResolver;

    public TenantMdcInterceptor(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            MDC.put(TraceContext.TENANT, tenantResolver.currentTenant());
        } catch (RuntimeException ignored) {
            // Unauthenticated endpoints (e.g. actuator) have no tenant; that is fine.
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove(TraceContext.TENANT);
    }
}
