package it.kristikomini.b2b.order.dto;

import java.util.List;

/**
 * A page of results plus the cursor for the next page ({@code null} when the last page is
 * reached). The client passes {@code nextCursor} straight back as {@code ?cursor=...}.
 */
public record SearchResponse(List<OrderResponse> items, String nextCursor) {
}
