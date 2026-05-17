package fyi.sererr;

import java.util.List;
import java.util.Objects;

/**
 * Plain Java struct mirroring {@code google.rpc.DebugInfo}.
 *
 * <p>Use {@link DebugInfoAdapter#toDebugInfo(java.util.List)} to render
 * a captured-error chain into this shape. The corresponding proto
 * conversion lives in {@code fyi.sererr.proto.ProtoAdapter}.
 */
public record DebugInfo(
        List<String> stackEntries,
        String detail
) {
    public DebugInfo {
        Objects.requireNonNull(stackEntries, "stackEntries");
        Objects.requireNonNull(detail, "detail");
        stackEntries = List.copyOf(stackEntries);
    }

    /** All-defaults DebugInfo (empty entries, empty detail). */
    public static DebugInfo empty() {
        return new DebugInfo(List.of(), "");
    }
}
