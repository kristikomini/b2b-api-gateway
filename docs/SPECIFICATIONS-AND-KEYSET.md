# Dynamic filtering (Specifications) + keyset pagination

## Specifications, not string SQL or 15 query methods

A B2B search endpoint takes many optional filters (status, date range, tenant, amount, text).
The wrong answers are string-concatenated SQL (injection + unmaintainable) or a `@Query` per
combination (combinatorial explosion). The right answer is **JPA Criteria via
`Specification<T>`**: each filter is a composable `Specification`, combined with `and()`/`or()`
only for the filters actually present.

```java
Specification<Order> spec = Specification.where(null);
if (status != null)      spec = spec.and(OrderSpecs.hasStatus(status));
if (from != null)        spec = spec.and(OrderSpecs.createdAfter(from));
if (text != null)        spec = spec.and(OrderSpecs.descriptionContains(text));
```

## Keyset (cursor) pagination, not OFFSET

`OFFSET 1_000_000 LIMIT 20` makes the database scan and discard a million rows — deep pages get
linearly slower, and rows shift under concurrent inserts. **Keyset pagination** instead carries a
cursor (the last seen sort key) and asks for rows *after* it:

```sql
WHERE (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT 20
```

Constant time per page, stable under inserts. Requires an index on the sort key and a strict total
order (hence the tiebreak on `id`).

## Benchmark (fill after building)

| Page | OFFSET p95 | Keyset p95 |
|------|-----------|-----------|
| page 1 | `<N>` ms | `<N>` ms |
| page 50,000 | `<N>` ms | `<N>` ms |

## Test
Seed `<N>` rows; assert keyset paging returns every row exactly once with no gaps/dupes even when
rows are inserted mid-pagination, and that deep-page latency stays flat.
