package it.kristikomini.b2b.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a state-changing method whose effect on an entity should be audited.
 *
 * <p>The aspect snapshots the entity before and after the call, diffs the fields
 * <b>generically by reflection</b> (no per-entity code), and writes the change set to the
 * audit log <b>asynchronously</b>, carrying the request's {@code traceId} so the audit row
 * correlates with the request that caused it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditTrail {

    /** Optional logical entity name for the audit row; defaults to the entity's simple class name. */
    String entity() default "";
}
