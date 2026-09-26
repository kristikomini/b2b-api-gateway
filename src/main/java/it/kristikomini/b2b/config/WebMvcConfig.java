package it.kristikomini.b2b.config;

import it.kristikomini.b2b.security.TenantResolver;
import it.kristikomini.b2b.web.TenantMdcInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the tenant-MDC interceptor. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantResolver tenantResolver;

    public WebMvcConfig(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantMdcInterceptor(tenantResolver));
    }
}
