package it.kristikomini.b2b.audit;

import it.kristikomini.b2b.order.Order;
import it.kristikomini.b2b.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reflection snapshot/diff is the generic core of {@code @AuditTrail}. Pure unit test —
 * no Spring, no DB.
 */
class FieldSnapshotterTest {

    @Test
    void snapshotsScalarFieldsAndSkipsTheVersionCounter() {
        Order order = new Order("acme", "C1", new BigDecimal("10.00"), "first order");

        Map<String, String> snap = FieldSnapshotter.snapshot(order);

        assertThat(snap).containsEntry("tenant", "acme")
                .containsEntry("customerRef", "C1")
                .containsEntry("status", "PENDING")
                .containsEntry("description", "first order");
        assertThat(snap).doesNotContainKey("version"); // optimistic-lock counter is not business state
    }

    @Test
    void diffReturnsOnlyChangedFieldsWithBeforeAndAfter() {
        Order order = new Order("acme", "C1", new BigDecimal("10.00"), "first order");
        Map<String, String> before = FieldSnapshotter.snapshot(order);

        order.setStatus(OrderStatus.CONFIRMED);
        Map<String, String> after = FieldSnapshotter.snapshot(order);

        Map<String, String[]> diff = FieldSnapshotter.diff(before, after);

        assertThat(diff).containsOnlyKeys("status");
        assertThat(diff.get("status")).containsExactly("PENDING", "CONFIRMED");
    }

    @Test
    void diffIsEmptyWhenNothingChanged() {
        Order order = new Order("acme", "C1", new BigDecimal("10.00"), "first order");
        Map<String, String> snap = FieldSnapshotter.snapshot(order);

        assertThat(FieldSnapshotter.diff(snap, snap)).isEmpty();
    }
}
