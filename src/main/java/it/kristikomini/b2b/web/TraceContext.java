package it.kristikomini.b2b.web;

/** Shared MDC key names, so the filter, the aspects and the log pattern all agree. */
public final class TraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT = "tenant";

    private TraceContext() {
    }
}
