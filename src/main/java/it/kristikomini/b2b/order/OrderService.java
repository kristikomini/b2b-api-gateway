package it.kristikomini.b2b.order;

import it.kristikomini.b2b.annotation.AuditTrail;
import it.kristikomini.b2b.annotation.IdempotentRequest;
import it.kristikomini.b2b.order.dto.CreateOrderRequest;
import it.kristikomini.b2b.order.dto.OrderResponse;
import it.kristikomini.b2b.security.TenantResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order use-cases. Two methods carry the custom infrastructure annotations:
 * {@link #place} is idempotent, {@link #updateStatus} is audited. Both are invoked from the
 * controller <i>through the proxy</i>, which is what lets the aspects fire.
 */
@Service
public class OrderService {

    /** Legal status transitions; anything else is a 409. */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private final OrderRepository repository;
    private final TenantResolver tenantResolver;

    public OrderService(OrderRepository repository, TenantResolver tenantResolver) {
        this.repository = repository;
        this.tenantResolver = tenantResolver;
    }

    @IdempotentRequest(ttlSeconds = 300)
    @Transactional
    public OrderResponse place(CreateOrderRequest request) {
        Order order = new Order(tenantResolver.currentTenant(),
                request.customerRef(), request.amount(), request.description());
        return OrderResponse.from(repository.save(order));
    }

    /**
     * Returns the {@link Order} entity (not a DTO) so the audit aspect can snapshot its
     * after-state by reflection; the controller maps it to a response.
     */
    @AuditTrail(entity = "Order")
    @Transactional
    public Order updateStatus(Long id, OrderStatus newStatus) {
        Order order = repository.findById(id)
                .filter(o -> o.getTenant().equals(tenantResolver.currentTenant()))
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (!TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).contains(newStatus)) {
            throw new IllegalStateException(
                    "illegal transition " + order.getStatus() + " -> " + newStatus);
        }
        order.setStatus(newStatus);
        return repository.save(order);
    }
}
