package it.kristikomini.b2b.order;

import it.kristikomini.b2b.order.dto.CreateOrderRequest;
import it.kristikomini.b2b.order.dto.OrderResponse;
import it.kristikomini.b2b.order.dto.SearchResponse;
import it.kristikomini.b2b.order.dto.UpdateStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The B2B order API. Every endpoint requires a valid JWT (see {@code SecurityConfig}); the
 * tenant is taken from the token, never from the request, so there is no tenant parameter to
 * tamper with.
 *
 * <p>{@code POST} is idempotent (send an {@code Idempotency-Key} header); status changes are
 * audited; search is filtered + keyset-paginated.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderSearchService searchService;

    public OrderController(OrderService orderService, OrderSearchService searchService) {
        this.orderService = orderService;
        this.searchService = searchService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.place(request);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable long id,
                                                      @Valid @RequestBody UpdateStatusRequest request) {
        Order updated = orderService.updateStatus(id, request.status());
        return ResponseEntity.ok(OrderResponse.from(updated));
    }

    @GetMapping
    public SearchResponse search(@RequestParam(required = false) OrderStatus status,
                                 @RequestParam(required = false) Instant from,
                                 @RequestParam(required = false) BigDecimal minAmount,
                                 @RequestParam(required = false) String text,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(required = false) Integer limit) {
        return searchService.search(status, from, minAmount, text, cursor, limit);
    }
}
