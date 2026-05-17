package fyi.sererr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A captured error.
 *
 * <p>Field-compatible with {@code sererr.v1.CapturedError}. A cause
 * chain is represented as a {@code List<CapturedError>}, most-causal-
 * first — the originating caught error is the LAST element. Chain
 * position is identified by {@link ExceptionMechanism#exceptionId()} /
 * {@link ExceptionMechanism#parentId()}.
 */
public record CapturedError(
        String type,
        String message,
        List<StackFrame> frames,
        Optional<ExceptionMechanism> mechanism,
        String release,
        String serverName
) {
    public CapturedError {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(serverName, "serverName");
        frames = List.copyOf(frames);
    }

    /** All-defaults captured error (all zero-value fields, no mechanism). */
    public static CapturedError empty() {
        return new CapturedError("", "", List.of(), Optional.empty(), "", "");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String type = "";
        private String message = "";
        private List<StackFrame> frames = List.of();
        private Optional<ExceptionMechanism> mechanism = Optional.empty();
        private String release = "";
        private String serverName = "";

        public Builder type(String v) { this.type = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder frames(List<StackFrame> v) { this.frames = v; return this; }
        public Builder mechanism(ExceptionMechanism v) { this.mechanism = Optional.ofNullable(v); return this; }
        public Builder mechanism(Optional<ExceptionMechanism> v) { this.mechanism = v; return this; }
        public Builder release(String v) { this.release = v; return this; }
        public Builder serverName(String v) { this.serverName = v; return this; }

        public CapturedError build() {
            return new CapturedError(type, message, frames, mechanism, release, serverName);
        }
    }
}
