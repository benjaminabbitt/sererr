package fyi.sererr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks a JVM {@link Throwable} into a sererr capture.
 *
 * <p>The returned chain is <strong>most-causal-first</strong> — the
 * originating caught error is the LAST element. Each entry carries its
 * own frames (extracted from {@link Throwable#getStackTrace()}, which
 * is already most-recent-first per the JVM contract — no reversal
 * required). Chain position is identified by
 * {@link ExceptionMechanism#exceptionId()}; the leaf cause has
 * {@code exceptionId = 0} and {@code parentId = 0}.
 *
 * <p>Try-with-resources {@linkplain Throwable#getSuppressed() suppressed}
 * exceptions are flattened into the chain as siblings of the throwable
 * they were attached to: they share the same {@code parentId}.
 *
 * <p>The default mechanism is {@code type="generic"}, {@code handled=true}.
 */
public final class Capture {
    private Capture() {}

    /**
     * Walk a {@link Throwable} chain into a sererr capture.
     *
     * @param throwable  the outermost (caught) error
     * @param typeName   class/type name to stamp on the LEAF entry
     *                   (Sentry-compat); usually the caught throwable's
     *                   class name
     * @param release    build identifier (git SHA or {@code name@semver})
     * @param serverName producing host / pod name
     * @return chain, most-causal-first
     */
    public static List<CapturedError> capture(
            Throwable throwable, String typeName, String release, String serverName) {
        if (throwable == null) {
            return List.of();
        }
        // Walk cause chain outer→inner, then suppressed siblings.
        var entries = new ArrayList<CapturedError>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        walk(throwable, typeName, release, serverName, entries, visited, true);
        // entries is outermost-first; reverse to most-causal-first.
        Collections.reverse(entries);
        // Stamp exception_id / parent_id positionally.
        return stampIds(entries);
    }

    /**
     * Extract frames from a throwable's {@link Throwable#getStackTrace()}.
     *
     * <p>The JVM already returns frames most-recent-first; no reversal.
     */
    public static List<StackFrame> captureFrames(Throwable throwable) {
        if (throwable == null) {
            return List.of();
        }
        StackTraceElement[] trace = throwable.getStackTrace();
        var frames = new ArrayList<StackFrame>(trace.length);
        for (StackTraceElement element : trace) {
            frames.add(frameFor(element));
        }
        return frames;
    }

    // ---- internals ----

    /**
     * Recursive walk: appends an entry for {@code t} and recurses into
     * its cause and suppressed siblings. Suppressed siblings share the
     * caller's chain depth (they're peers of {@code t}'s parent in the
     * tree but flattened out).
     */
    private static void walk(
            Throwable t,
            String leafType,
            String release,
            String serverName,
            List<CapturedError> entries,
            Set<Throwable> visited,
            boolean isLeafTypeOverride) {
        if (t == null || !visited.add(t)) {
            return;
        }
        String type = isLeafTypeOverride ? leafType : t.getClass().getName();
        entries.add(CapturedError.builder()
                .type(type)
                .message(messageOf(t))
                .frames(captureFrames(t))
                .mechanism(ExceptionMechanism.generic())
                .release(release)
                .serverName(serverName)
                .build());
        // Recurse into the cause first (the chain spine).
        if (t.getCause() != null && t.getCause() != t) {
            walk(t.getCause(), leafType, release, serverName, entries, visited, false);
        }
        // Then flatten any suppressed exceptions. Their cause subtrees
        // are walked too; they appear after the spine for stable order.
        for (Throwable s : t.getSuppressed()) {
            walk(s, leafType, release, serverName, entries, visited, false);
        }
    }

    private static String messageOf(Throwable t) {
        String m = t.getLocalizedMessage();
        return m == null ? "" : m;
    }

    /**
     * Build a {@link StackFrame} from a {@link StackTraceElement}.
     * Handles {@code getFileName() == null} (lambdas, synthetic frames)
     * by substituting the declaring class's slash-separated resource
     * path.
     */
    static StackFrame frameFor(StackTraceElement element) {
        String function = element.getMethodName();
        String declaringClass = element.getClassName();
        String module = declaringClass;
        String file = element.getFileName();
        if (file == null || file.isEmpty()) {
            // Substitute the declaring class's resource path (slash form).
            int dollar = declaringClass.indexOf('$');
            String outer = dollar < 0 ? declaringClass : declaringClass.substring(0, dollar);
            file = outer.replace('.', '/') + ".java";
        }
        int line = Math.max(0, element.getLineNumber());
        boolean inApp = isAppFrame(declaringClass);
        return StackFrame.builder()
                .function(function)
                .module(module)
                .file(file)
                .line(line)
                .inApp(inApp)
                .build();
    }

    /**
     * Default {@code in_app} heuristic; producers with a better signal
     * should not rely on this. Frames whose declaring class starts
     * with {@code java.}, {@code javax.}, {@code jdk.}, {@code sun.},
     * {@code kotlin.}, {@code kotlinx.} are treated as framework.
     */
    public static boolean isAppFrame(String declaringClass) {
        if (declaringClass == null || declaringClass.isEmpty()) {
            return true;
        }
        for (String prefix : NON_APP_PREFIXES) {
            if (declaringClass.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static final String[] NON_APP_PREFIXES = {
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "kotlin.", "kotlinx.",
            "org.junit.", "org.gradle.", "org.testng.",
            "fyi.sererr.",
    };

    /** Stamp {@code exceptionId = i}, {@code parentId = max(0, i-1)} on each entry. */
    private static List<CapturedError> stampIds(List<CapturedError> entries) {
        var stamped = new ArrayList<CapturedError>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            CapturedError e = entries.get(i);
            int exceptionId = i;
            int parentId = i == 0 ? 0 : i - 1;
            ExceptionMechanism updated = e.mechanism()
                    .map(m -> ExceptionMechanism.builder()
                            .type(m.type())
                            .description(m.description())
                            .handled(m.handled())
                            .synthetic(m.synthetic())
                            .helpLink(m.helpLink())
                            .source(m.source())
                            .exceptionId(exceptionId)
                            .parentId(parentId)
                            .isExceptionGroup(m.isExceptionGroup())
                            .data(m.data())
                            .build())
                    .orElseGet(() -> ExceptionMechanism.builder()
                            .type("generic")
                            .handled(true)
                            .exceptionId(exceptionId)
                            .parentId(parentId)
                            .build());
            stamped.add(new CapturedError(
                    e.type(), e.message(), e.frames(), Optional.of(updated),
                    e.release(), e.serverName()));
        }
        return List.copyOf(stamped);
    }
}
