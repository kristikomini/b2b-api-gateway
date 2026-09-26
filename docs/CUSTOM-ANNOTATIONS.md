# Custom annotations via reflection + AOP

The goal is to show the machinery Spring hides. Two annotations, both implemented as aspects.

## `@IdempotentRequest(ttlSeconds = 300)`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IdempotentRequest { int ttlSeconds() default 300; }
```

**Aspect** (`@Around`): read the `Idempotency-Key` request header; key = tenant + method +
Idempotency-Key. On first call, execute and store the serialized response in **Redis** with the
TTL. On a repeat within the TTL, return the stored response without re-executing — so a client
retrying a `POST` after a timeout does not create two orders.

Concerns handled: what counts as "the same request" (key design), in-flight concurrent duplicates
(a short lock), and only caching successful responses.

## `@AuditTrail`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditTrail { String entity() default ""; }
```

**Aspect** (`@Around`): snapshot the target entity before, run the method, snapshot after, and use
**reflection** to diff fields generically (works for any `@Entity` without per-type code). Write
the diff to an `audit_log` table **asynchronously** (`@Async`), propagating the MDC `traceId` into
the async thread so the audit row correlates with the request.

## The gotcha this proves you understand
Spring AOP works via **proxies**: an annotated method called **from within the same bean**
(`this.method()`) bypasses the proxy, so the aspect never runs. The code and a test demonstrate
this and the fix (self-injection / restructuring), because it is a standard senior interview trap.

## Test
- `@IdempotentRequest`: fire the same request twice; assert the handler executed once and both
  responses match. Fire two concurrently; assert exactly one execution.
- `@AuditTrail`: mutate an entity; assert one audit row with the correct before/after diff and the
  request's `traceId`.
