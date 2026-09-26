package it.kristikomini.b2b.audit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads an entity's scalar fields into a {@code name -> value} map by <b>reflection</b>,
 * so audit works for <i>any</i> entity type without hand-written per-entity code — the point
 * the {@code @AuditTrail} aspect is demonstrating.
 *
 * <p>Only scalar, self-describing fields are captured (strings, numbers, booleans, enums,
 * dates, UUIDs). Associations, collections and {@code static}/{@code transient}/{@code version}
 * fields are skipped: they either are not the audited state or would drag in lazy-loading and
 * infinite graphs.
 */
public final class FieldSnapshotter {

    /** Snapshot the scalar fields of {@code entity} (including inherited ones) as strings. */
    public static Map<String, String> snapshot(Object entity) {
        Map<String, String> out = new LinkedHashMap<>();
        if (entity == null) {
            return out;
        }
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!isAuditable(f)) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object value = f.get(entity);
                    out.put(f.getName(), value == null ? null : String.valueOf(value));
                } catch (IllegalAccessException e) {
                    // A field we cannot read is simply not audited; never fail the business call for it.
                    out.put(f.getName(), "<unreadable>");
                }
            }
        }
        return out;
    }

    /**
     * Field-by-field diff of two snapshots. Returns only the fields whose value changed,
     * each mapped to {@code [before, after]}.
     */
    public static Map<String, String[]> diff(Map<String, String> before, Map<String, String> after) {
        Map<String, String[]> changes = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            String b = before.get(key);
            String a = after.get(key);
            if (!Objects.equals(b, a)) {
                changes.put(key, new String[]{b, a});
            }
        }
        return changes;
    }

    private static boolean isAuditable(Field f) {
        int mod = f.getModifiers();
        if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
            return false;
        }
        if ("version".equals(f.getName())) {
            return false; // optimistic-lock counter, not business state
        }
        Class<?> t = f.getType();
        return t.isPrimitive()
                || t.isEnum()
                || CharSequence.class.isAssignableFrom(t)
                || Number.class.isAssignableFrom(t)
                || t == Boolean.class
                || t == BigDecimal.class
                || t == UUID.class
                || Temporal.class.isAssignableFrom(t);
    }

    private FieldSnapshotter() {
    }
}
