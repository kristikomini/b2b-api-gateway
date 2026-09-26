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

## What this demonstrates (CV bullets)

*Proven by tests in this repo (Postgres + Redis + Keycloak via Testcontainers in CI; core flows
also verified against real Postgres/Redis and a live Keycloak locally):*
- Built custom `@IdempotentRequest` and `@AuditTrail` annotations with Spring **AOP + Java
  reflection**, deduplicating repeated `POST`s via a Redis `SET NX` lock (same key → one execution,
  same cached response) and writing **async** audit rows that diff any entity's fields generically
  and carry the request's `traceId` across the thread hop.
- Built composable filtering with **JPA Specifications** and **keyset (cursor) pagination** instead
  of `OFFSET`, proven to return every row exactly once across pages (no gaps/dupes) with a strict
  `(created_at, id)` total order.
- Secured a **multi-tenant** API as an OAuth2/JWT **resource server**: the tenant is a signed token
  claim (not a request parameter), enforced end-to-end — verified with a real **Keycloak**-issued
  token (`201`, tenant derived from the claim) and `401` without one, plus cross-tenant access
  returning empty/`404`.
- Full **Testcontainers** integration coverage — real Postgres + Redis + Keycloak, no H2, no mocks
  at the DB or auth boundary — with the proxy self-invocation gotcha documented.

*To fill in once benchmarked (see [`docs/SPECIFICATIONS-AND-KEYSET.md`](docs/SPECIFICATIONS-AND-KEYSET.md)):*
deep-page p95, `OFFSET` vs keyset, on a large seeded table.

## Run it

```bash
docker compose up --build   # app + postgres + redis + keycloak (realm auto-imported)
```

Get a token from Keycloak (published on `:8081`), then call the API with it:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/b2b/protocol/openid-connect/token \
  -d grant_type=password -d client_id=b2b-api -d username=acme-user -d password=password \
  | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: k1" \
  -H 'Content-Type: application/json' -d '{"customerRef":"C1","amount":10.00}' \
  http://localhost:8080/orders
```

## Course topics exercised

Reflection API, custom annotations (`@Retention`, `@Target`), dynamic proxies
(`InvocationHandler`), functional interfaces (`Predicate`/`Function` composition), custom
comparators & collections internals. Academy modules: 13–16 (the Spring container, DI, config,
autoconfiguration), 20 (security), 18 (API design).
