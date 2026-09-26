# B2B API service — Spring under the hood (Reflection, AOP, Security, Testcontainers)

Senior interviews test whether you understand **how Spring actually works** — proxies,
reflection, dependency injection, transaction boundaries — rather than treating annotations as
magic. This is a **multi-tenant B2B API** where the infrastructure is built *by hand* with Java
**Reflection**, **dynamic proxies** and **Spring AOP**, backed by bulletproof Testcontainers
testing.

Target market: product companies, SaaS, enterprise-architecture teams — all four cities.

> Follows the shared [engineering standards](../ENGINEERING-STANDARDS.md).

## What it demonstrates

### 1. Custom annotations via reflection + AOP
- **`@IdempotentRequest(ttlSeconds = 300)`** — a method annotation whose aspect reads an
  `Idempotency-Key` header, stores the first response in **Redis** for the TTL, and short-circuits
  duplicate `POST`s with the stored result. → [`docs/CUSTOM-ANNOTATIONS.md`](docs/CUSTOM-ANNOTATIONS.md)
- **`@AuditTrail`** — an aspect that captures before/after entity state and writes an audit record
  **asynchronously**, using reflection to diff fields generically across any entity type.
- Written to show you know `@Retention`/`@Target`, `InvocationHandler`/dynamic proxies, and why
  **self-invocation breaks proxy-based AOP** (the classic gotcha).

### 2. Dynamic filtering done right
- A complex search endpoint via **JPA Criteria API / Specifications** — composable predicates, no
  string concatenation and no 15 bespoke `@Query` methods.
- **Keyset (cursor) pagination**, not `OFFSET` — because `OFFSET 1000000` scans a million rows.
  → [`docs/SPECIFICATIONS-AND-KEYSET.md`](docs/SPECIFICATIONS-AND-KEYSET.md)

### 3. Production security & observability
- **OAuth2 / JWT** resource-server validation against **Keycloak** (in Docker), multi-tenant
  claims. → [`docs/SECURITY.md`](docs/SECURITY.md)
- Structured **JSON logging** with an MDC **`traceId`** correlated across threads (including the
  async audit path).
- **Flyway** migrations; `ddl-auto=validate`.

### 4. Zero-mock database testing
- **Testcontainers** spinning up real **PostgreSQL + Redis + Keycloak** during `mvn verify` — no
  H2 hiding Postgres-specific behaviour.

## What this demonstrates (CV bullets — fill numbers after building)

- Built custom `@IdempotentRequest` and `@AuditTrail` annotations with Spring AOP + Java
  reflection/dynamic proxies, deduplicating duplicate `POST`s via Redis and capturing async audit
  trails across any entity type.
- Replaced `OFFSET` pagination with keyset pagination on a `<N>`-row table, cutting deep-page p95
  from `<N>` ms to `<N>` ms; built composable filtering with JPA Specifications.
- Secured a multi-tenant API with OAuth2/JWT (Keycloak) and full Testcontainers integration
  coverage (Postgres + Redis + Keycloak) — no H2, no mocks at the DB boundary.

## Run it

```bash
docker compose up   # app + postgres + redis + keycloak
```

## Course topics exercised

Reflection API, custom annotations (`@Retention`, `@Target`), dynamic proxies
(`InvocationHandler`), functional interfaces (`Predicate`/`Function` composition), custom
comparators & collections internals. Academy modules: 13–16 (the Spring container, DI, config,
autoconfiguration), 20 (security), 18 (API design).
