package it.kristikomini.b2b.order;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Basic CRUD; the search (dynamic filters + keyset) is built in {@link OrderSearchService} with
 * the {@code EntityManager}, because its row-value cursor predicate cannot be expressed via the
 * Criteria API or a static {@code @Query}.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
