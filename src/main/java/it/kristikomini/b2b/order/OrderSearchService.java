package it.kristikomini.b2b.order;

import it.kristikomini.b2b.order.dto.OrderResponse;
import it.kristikomini.b2b.order.dto.SearchResponse;
import it.kristikomini.b2b.security.TenantResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read side of the API: composable filtering via {@link OrderSpecs} and <b>keyset</b>
 * pagination. The sort is fixed to {@code (createdAt DESC, id DESC)} so the cursor predicate
 * and the ordering agree — that agreement is what keeps paging correct and constant-time.
 */
@Service
public class OrderSearchService {

    private static final int MAX_LIMIT = 100;
    private static final Sort KEYSET_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository repository;
    private final TenantResolver tenantResolver;

    public OrderSearchService(OrderRepository repository, TenantResolver tenantResolver) {
        this.repository = repository;
        this.tenantResolver = tenantResolver;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(OrderStatus status, Instant from, BigDecimal minAmount,
                                 String text, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);

        // Tenant scope is not optional — it is the first, always-applied predicate.
        Specification<Order> spec = OrderSpecs.forTenant(tenantResolver.currentTenant());
        if (status != null) {
            spec = spec.and(OrderSpecs.hasStatus(status));
        }
        if (from != null) {
            spec = spec.and(OrderSpecs.createdAfter(from));
        }
        if (minAmount != null) {
            spec = spec.and(OrderSpecs.amountAtLeast(minAmount));
        }
        if (text != null && !text.isBlank()) {
            spec = spec.and(OrderSpecs.descriptionContains(text));
        }
        if (cursor != null && !cursor.isBlank()) {
            KeysetCursor c = KeysetCursor.decode(cursor);
            spec = spec.and(OrderSpecs.keysetBefore(c.createdAt(), c.id()));
        }

        // Page 0 of the keyset window: the cursor, not an OFFSET, is what advances the window.
        List<Order> rows = repository.findAll(spec, PageRequest.of(0, pageSize, KEYSET_SORT)).getContent();

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
