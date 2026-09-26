package it.kristikomini.b2b.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's tenant from the validated JWT. The tenant is a token <b>claim</b>,
 * not a request parameter or header, so a caller cannot ask for another tenant's data by
 * changing the URL — the value is signed by the identity provider.
 */
@Component
public class TenantResolver {

    public static final String TENANT_CLAIM = "tenant";

    /** @throws IllegalStateException if there is no authenticated JWT with a tenant claim. */
    public String currentTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenant = jwt.getClaimAsString(TENANT_CLAIM);
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        throw new IllegalStateException("no tenant claim on the authenticated principal");
    }
}
