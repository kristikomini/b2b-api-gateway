# Dynamic filtering + keyset pagination

## Dynamic filtering: one parameterized query, not string SQL or 15 query methods

A B2B search endpoint takes many optional filters (status, date range, amount, text). The wrong
answers are string-concatenated SQL (injection + unmaintainable) or a `@Query` per combination
(combinatorial explosion). The query is instead **assembled from the filters that are present** —
each supplied filter appends one `and …` clause whose value is always a **bound parameter**:

```java
StringBuilder hql = new StringBuilder("select o from Order o where o.tenant = :tenant");
if (status != null)    hql.append(" and o.status = :status");
if (from != null)      hql.append(" and o.createdAt >= :from");
if (text != null)      hql.append(" and lower(o.description) like lower(concat('%', :text, '%'))");
// … then bind only the parameters whose clause was added, and setMaxResults(pageSize)
```

Clause *structure* is composed; **values are never concatenated** (no injection surface), and there
is no method-per-combination.

> This started out using JPA **Criteria `Specification`s**. The keyset work below forced the change:
> Criteria cannot express a row-value comparison, so the pagination is built with the
> `EntityManager` instead. Criteria is great for optional-predicate filters; it just can't do the
> one thing keyset needs.

## Keyset (cursor) pagination, not OFFSET

`OFFSET 1_000_000 LIMIT 20` makes the database **scan and discard a million rows** — deep pages get
linearly slower, and rows shift under concurrent inserts. **Keyset pagination** carries a cursor
(the last seen sort key) and asks for rows *after* it, ordered by a strict total order:

```sql
WHERE tenant = ? AND (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT 20
```

Constant time per page, stable under inserts. Requires an index on the sort key
(`idx_orders_tenant_keyset (tenant, created_at DESC, id DESC)`) and a strict total order — hence the
`id` tiebreak.

### The subtlety that the benchmark caught: row-value, not `OR`

The predicate **must** be written as a PostgreSQL **row-value comparison** `(created_at, id) < (:c, :id)`.
The logically-identical rewrite `created_at < :c OR (created_at = :c AND id < :id)` is what the
Criteria API produces — and under a parameterized/generic plan Postgres does **not** turn it into an
index range scan; it sorts the whole tenant partition. `EXPLAIN` on the seeded 1M-row table:

```
-- row-value form
Limit -> Index Only Scan using idx_orders_tenant_keyset (actual rows=20)
           Index Cond: (tenant = 'acme' AND ROW(created_at, id) < ROW($1, $2))
-- OR form
Limit -> Sort
           -> (scans the whole tenant partition, then sorts)
```

## Benchmark (measured)

PostgreSQL 16, 1,000,000 rows for one tenant, page size 20, p50/p95 over 80 iterations
(`clock_timestamp()` around the query). Deep page = the last page (offset ≈ 1,000,000):

| Page | OFFSET p95 | Keyset p95 (row-value) | Rows scanned (OFFSET → keyset) |
|------|-----------|------------------------|-------------------------------|
| page 1 (shallow) | 0.139 ms | 0.070 ms | 20 → ~20 |
| deep (~1,000,000) | **121.3 ms** | **0.069 ms** | **1,000,000 → ~20** |

Keyset is **constant time**: the deep page (0.069 ms) is as fast as the first page, while OFFSET
degrades to ~121 ms because it walks a million index entries to throw them away — about **1,750×**
slower at the deep page. (The `OR`-form keyset, before the fix, measured ~180 ms at depth — *worse*
than OFFSET — which is exactly why the row-value form matters.)

## Test
`OrderApiIntegrationTest` asserts keyset paging returns every row exactly once across pages with no
gaps or duplicates (Testcontainers Postgres). The latency numbers above are reproduced with the
`bench()` SQL function on a 1M-row seed.
