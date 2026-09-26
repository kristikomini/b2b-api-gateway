package it.kristikomini.b2b.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose result should be de-duplicated per {@code Idempotency-Key} header.
 *
 * <p>The first call with a given key executes and its response is cached in Redis for
 * {@link #ttlSeconds()}; a retry with the same key within the window returns the cached
 * response without re-executing — so a client that retries a {@code POST} after a timeout
 * does not create two orders.
 *
 * <p>{@code RUNTIME} retention is mandatory: the aspect discovers the annotation by
 * <b>reflection</b> at call time, so it must survive into the class file and be visible to
 * the JVM (a {@code SOURCE}/{@code CLASS}-retained annotation would be invisible here).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IdempotentRequest {

    /** How long a stored response remains authoritative for a repeated key. */
    int ttlSeconds() default 300;
}
