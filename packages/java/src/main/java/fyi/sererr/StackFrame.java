package fyi.sererr;

import java.util.List;
import java.util.Objects;

/**
 * A single frame in a captured stack trace.
 *
 * <p>Field-compatible with the {@code sererr.v1.StackFrame} proto
 * definition. All fields use proto3 "zero value = unknown" semantics:
 * the empty string and the {@code 0} number mean "not populated."
 *
 * <p>Frames are ordered most-recent-call-first within a captured error,
 * matching the Sentry convention and Python's {@code traceback.format_exception}.
 *
 * <p>Records are immutable — to populate source-context fields after
 * capture, use {@link SourceContext#populateContext(StackFrame, SourceProvider, int)}
 * which returns a new frame with the populated fields.
 */
public record StackFrame(
        String function,
        String module,
        String pkg,
        String file,
        String absPath,
        int line,
        String contextLine,
        List<String> preContext,
        List<String> postContext,
        String sourceLink,
        boolean inApp
) {
    /** All-defaults constructor used for proto3 "zero value" semantics. */
    public static StackFrame empty() {
        return new StackFrame("", "", "", "", "", 0, "", List.of(), List.of(), "", false);
    }

    public StackFrame {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(pkg, "pkg");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(absPath, "absPath");
        Objects.requireNonNull(contextLine, "contextLine");
        Objects.requireNonNull(preContext, "preContext");
        Objects.requireNonNull(postContext, "postContext");
        Objects.requireNonNull(sourceLink, "sourceLink");
        preContext = List.copyOf(preContext);
        postContext = List.copyOf(postContext);
    }

    /** Builder helper for incrementally constructing frames in tests and adapters. */
    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder; not thread-safe. Call {@link #build()} to produce an immutable frame. */
    public static final class Builder {
        private String function = "";
        private String module = "";
        private String pkg = "";
        private String file = "";
        private String absPath = "";
        private int line = 0;
        private String contextLine = "";
        private List<String> preContext = List.of();
        private List<String> postContext = List.of();
        private String sourceLink = "";
        private boolean inApp = false;

        public Builder function(String v) { this.function = v; return this; }
        public Builder module(String v) { this.module = v; return this; }
        public Builder pkg(String v) { this.pkg = v; return this; }
        public Builder file(String v) { this.file = v; return this; }
        public Builder absPath(String v) { this.absPath = v; return this; }
        public Builder line(int v) { this.line = v; return this; }
        public Builder contextLine(String v) { this.contextLine = v; return this; }
        public Builder preContext(List<String> v) { this.preContext = v; return this; }
        public Builder postContext(List<String> v) { this.postContext = v; return this; }
        public Builder sourceLink(String v) { this.sourceLink = v; return this; }
        public Builder inApp(boolean v) { this.inApp = v; return this; }

        public StackFrame build() {
            return new StackFrame(function, module, pkg, file, absPath, line,
                    contextLine, preContext, postContext, sourceLink, inApp);
        }
    }
}
