-- B2B schema. Tenancy columns and their indexes are part of the migration, not an afterthought.

CREATE TABLE orders (
    id           BIGSERIAL      PRIMARY KEY,
    tenant       TEXT           NOT NULL,
    customer_ref TEXT           NOT NULL,
    status       TEXT           NOT NULL,
    amount       NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    description  TEXT,
    created_at   TIMESTAMPTZ    NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0
);

-- Keyset pagination reads WHERE tenant = ? ORDER BY created_at DESC, id DESC: this composite
-- index makes each page an index range scan (constant time), never an OFFSET row-count scan.
CREATE INDEX idx_orders_tenant_keyset ON orders (tenant, created_at DESC, id DESC);

CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    tenant      TEXT        NOT NULL,
    entity_name TEXT        NOT NULL,
    entity_id   TEXT        NOT NULL,
    changes     TEXT        NOT NULL,
    trace_id    TEXT,
    at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_entity ON audit_log (entity_name, entity_id);
