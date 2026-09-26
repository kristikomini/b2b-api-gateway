package it.kristikomini.b2b.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Carries the submitting thread's MDC (the request's {@code traceId}, tenant, …) onto the
 * async worker thread, then restores the worker's own context afterwards. Without this, logs
 * and audit rows written on the async audit thread would lose the request correlation — the
 * classic "traceId disappears once you go async" bug.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (captured != null) {
                MDC.setContextMap(captured);
            }
            try {
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
