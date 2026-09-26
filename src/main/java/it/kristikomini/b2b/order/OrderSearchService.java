package it.kristikomini.b2b.order;

import it.kristikomini.b2b.order.dto.OrderResponse;
import it.kristikomini.b2b.order.dto.SearchResponse;
import it.kristikomini.b2b.security.TenantResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read side of the API: dynamic filtering + <b>keyset</b> pagination.
 *
 * <p>The query is <b>assembled from the filters that are present</b> — each optional filter adds
 * one {@code and …} clause, its value always a <i>bound parameter</i> (never string-concatenated).
 * That gives the composability of "many optional filters" without a query method per combination
 * and without the SQL-injection surface of building predicates from values.
 *
 * <p>Pagination is keyset via PostgreSQL <b>row-value comparison</b>
 * {@code (o.createdAt, o.id) < (:cAt, :cId)}: with the sort fixed to
 * {@code (createdAt DESC, id DESC)} it becomes an index range scan of ~{@code pageSize} rows at
 * <i>any</i> depth. The logically-equivalent {@code createdAt < c OR (createdAt = c AND id < i)}
 * rewrite degrades to a full sort of the tenant's rows under a parameterized plan — the benchmark
 * in {@code docs/} measures the gap. Row-value comparison is why this is built here rather than
 * with the Criteria API, which cannot express it.
 */
@Service
public class OrderSearchService {

    private static final int MAX_LIMIT = 100;

    private final EntityManager entityManager;
    private final TenantResolver tenantResolver;

    public OrderSearchService(EntityManager entityManager, TenantResolver tenantResolver) {
        this.entityManager = entityManager;
        this.tenantResolver = tenantResolver;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(OrderStatus status, Instant from, BigDecimal minAmount,
                                 String text, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);

        // A clause is appended only for a filter that is present, and its value is bound below —
        // so an absent filter contributes neither a clause nor a (null, untyped) parameter.
        StringBuilder hql = new StringBuilder("select o from Order o where o.tenant = :tenant");

        if (status != null) {
            hql.append(" and o.status = :status");
        }
        if (from != null) {
            hql.append(" and o.createdAt >= :from");
        }
        if (minAmount != null) {
            hql.append(" and o.amount >= :minAmount");
        }
        boolean hasText = text != null && !text.isBlank();
        if (hasText) {
            hql.append(" and lower(o.description) like lower(concat('%', :text, '%'))");
        }
        KeysetCursor c = (cursor == null || cursor.isBlank()) ? null : KeysetCursor.decode(cursor);
        if (c != null) {
            hql.append(" and (o.createdAt, o.id) < (:cAt, :cId)");
        }
        hql.append(" order by o.createdAt desc, o.id desc");

        TypedQuery<Order> query = entityManager.createQuery(hql.toString(), Order.class);
        query.setParameter("tenant", tenantResolver.currentTenant());
        if (status != null) {
            query.setParameter("status", status);
        }
        if (from != null) {
            query.setParameter("from", from);
        }
        if (minAmount != null) {
            query.setParameter("minAmount", minAmount);
        }
        if (hasText) {
            query.setParameter("text", text);
        }
        if (c != null) {
            query.setParameter("cAt", c.createdAt());
            query.setParameter("cId", c.id());
        }
        query.setMaxResults(pageSize);

        List<Order> rows = query.getResultList();
        List<OrderResponse> items = rows.stream().map(OrderResponse::from).toList();
        // A full page implies there may be more; hand back the last row's key as the next cursor.
        String nextCursor = rows.size() == pageSize
                ? KeysetCursor.from(rows.get(rows.size() - 1)).encode()
                : null;
        return new SearchResponse(items, nextCursor);
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
