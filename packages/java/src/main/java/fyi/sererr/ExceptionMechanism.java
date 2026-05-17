package fyi.sererr;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Describes how an exception was captured / handled.
 *
 * <p>Field-compatible with {@code sererr.v1.ExceptionMechanism}. Mirrors
 * Sentry's {@code mechanism} object on the exception interface and
 * carries chain linkage via {@code exceptionId} / {@code parentId}.
 *
 * <p>The {@code data} map is a {@link SortedMap} (specifically a
 * {@link TreeMap}) — sorted-key iteration is required for byte-
 * deterministic proto encoding, which the cross-language conformance
 * corpus enforces.
 */
public record ExceptionMechanism(
        String type,
        String description,
        boolean handled,
        boolean synthetic,
        String helpLink,
        String source,
        int exceptionId,
        int parentId,
        boolean isExceptionGroup,
        SortedMap<String, String> data
) {
    public ExceptionMechanism {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(helpLink, "helpLink");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(data, "data");
        // Defensive copy into a TreeMap so map iteration is sorted on
        // any subsequent encode.
        var copy = new TreeMap<String, String>();
        copy.putAll(data);
        data = Collections.unmodifiableSortedMap(copy);
    }

    /** All-defaults constructor used for proto3 "zero value" semantics. */
    public static ExceptionMechanism empty() {
        return new ExceptionMechanism("", "", false, false, "", "", 0, 0, false,
                Collections.emptySortedMap());
    }

    /** Convenience: the producer-default mechanism ("generic", handled=true). */
    public static ExceptionMechanism generic() {
        return new ExceptionMechanism("generic", "", true, false, "", "", 0, 0, false,
                Collections.emptySortedMap());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String type = "";
        private String description = "";
        private boolean handled = false;
        private boolean synthetic = false;
        private String helpLink = "";
        private String source = "";
        private int exceptionId = 0;
        private int parentId = 0;
        private boolean isExceptionGroup = false;
        private SortedMap<String, String> data = new TreeMap<>();

        public Builder type(String v) { this.type = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder handled(boolean v) { this.handled = v; return this; }
        public Builder synthetic(boolean v) { this.synthetic = v; return this; }
        public Builder helpLink(String v) { this.helpLink = v; return this; }
        public Builder source(String v) { this.source = v; return this; }
        public Builder exceptionId(int v) { this.exceptionId = v; return this; }
        public Builder parentId(int v) { this.parentId = v; return this; }
        public Builder isExceptionGroup(boolean v) { this.isExceptionGroup = v; return this; }
        public Builder data(SortedMap<String, String> v) { this.data = new TreeMap<>(v); return this; }
        public Builder putData(String k, String v) { this.data.put(k, v); return this; }

        public ExceptionMechanism build() {
            return new ExceptionMechanism(type, description, handled, synthetic,
                    helpLink, source, exceptionId, parentId, isExceptionGroup, data);
        }
    }
}
