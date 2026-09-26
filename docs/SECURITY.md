# Security & observability

## OAuth2 / JWT resource server (Keycloak)
The service is a **resource server**: it validates incoming JWTs (signature via Keycloak's JWKS,
issuer, audience, expiry) — it does not issue tokens. Keycloak runs in Docker as the identity
provider.

- **Multi-tenancy**: the tenant is a JWT claim; a `Filter`/`@PreAuthorize` enforces that a caller
  only ever touches their tenant's data, and the tenant is put into MDC + used in cache keys.
- **Authorities** map from realm/client roles to Spring authorities.

## Structured JSON logging + trace correlation
- Logs are JSON (Logback encoder), one event per line, machine-parseable.
- An **MDC `traceId`** is set per request (from a `traceparent`/`X-Request-Id` header or generated)
  and **propagated across threads** — including the `@Async` audit path and any executor — so a
  request's logs, audit row and metrics all correlate. This is the observability rule of the
  shared [standards](../../ENGINEERING-STANDARDS.md).

## Migrations
Flyway owns the schema (`V1__init.sql`, …); `ddl-auto=validate`. Tenancy columns and their indexes
are part of the migration, not an afterthought.

## Testcontainers
Integration tests bring up real **PostgreSQL + Redis + Keycloak**. A test obtains a real token
from the Keycloak container and calls the API with it — the auth path is exercised end to end, not
mocked.
