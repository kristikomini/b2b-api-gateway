package it.kristikomini.b2b.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@code JpaSpecificationExecutor} is what lets the search endpoint compose filters as
 * {@code Specification<Order>} predicates instead of a bespoke {@code @Query} per filter
 * combination — the difference between one composable query builder and a combinatorial
 * explosion of query methods.
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
}
