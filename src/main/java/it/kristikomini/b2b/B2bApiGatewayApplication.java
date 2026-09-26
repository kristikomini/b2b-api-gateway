package it.kristikomini.b2b;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * B2B API service. {@code @EnableAsync} turns on the async executor behind the
 * {@code @AuditTrail} aspect — audit rows are written off the request thread, with the
 * request's MDC {@code traceId} carried across (see {@code AsyncConfig}).
 */
@SpringBootApplication
@EnableAsync
public class B2bApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(B2bApiGatewayApplication.class, args);
    }
}
