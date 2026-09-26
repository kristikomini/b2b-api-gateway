package it.kristikomini.b2b.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A B2B order. Every row carries its owning {@code tenant}: the API only ever exposes rows
 * for the caller's tenant (enforced in the query layer), so multi-tenancy is a data
 * property, not just a UI filter.
 *
 * <p>The {@code (created_at, id)} pair is the stable sort key for keyset pagination — a
 * strict total order (the {@code id} tiebreak) so no row is skipped or repeated across pages.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenant;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Order() {
        // for JPA
    }

    public Order(String tenant, String customerRef, BigDecimal amount, String description) {
        this.tenant = tenant;
        this.customerRef = customerRef;
        this.amount = amount;
        this.description = description;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTenant() { return tenant; }
    public String getCustomerRef() { return customerRef; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
