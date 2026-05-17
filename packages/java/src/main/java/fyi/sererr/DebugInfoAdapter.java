package fyi.sererr;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that renders a sererr capture into the {@link DebugInfo}
 * shape (wire-compatible with {@code google.rpc.DebugInfo}).
 *
 * <p>{@code stackEntries} carries one line per frame, formatted as
 * {@code "  at <function> (<file>:<line>)"}, most-recent-call-first
 * within each chain entry, separated by {@code "Caused by: <type>: <message>"}
 * header lines across the chain.
 *
 * <p>{@code detail} joins {@code "<type>: <message>"} across the chain
 * with {@code "\nCaused by: "} separators.
 *
 * <p>The chain is walked outermost-first (it's stored most-causal-first;
 * this iterates in reverse) so the outermost-caught error appears first
 * in the DebugInfo output — that's the Sentry/Python convention.
 */
public final class DebugInfoAdapter {
    private DebugInfoAdapter() {}

    /** Render a {@link CapturedError} chain into a {@link DebugInfo}. */
    public static DebugInfo toDebugInfo(List<CapturedError> chain) {
        if (chain == null || chain.isEmpty()) {
            return DebugInfo.empty();
        }
        var stackEntries = new ArrayList<String>();
        var detailParts = new ArrayList<String>();
        // chain is most-causal-first; render outermost-first.
        for (int i = chain.size() - 1, idx = 0; i >= 0; i--, idx++) {
            CapturedError entry = chain.get(i);
            String header = entry.type() + ": " + entry.message();
            if (idx == 0) {
                detailParts.add(header);
                stackEntries.add(header);
            } else {
                detailParts.add("Caused by: " + header);
                stackEntries.add("Caused by: " + header);
            }
            for (StackFrame frame : entry.frames()) {
                stackEntries.add(formatFrameLine(frame));
            }
        }
        return new DebugInfo(stackEntries, String.join("\n", detailParts));
    }

    static String formatFrameLine(StackFrame frame) {
        String location;
        if (frame.file().isEmpty()) {
            location = "";
        } else if (frame.line() > 0) {
            location = " (" + frame.file() + ":" + frame.line() + ")";
        } else {
            location = " (" + frame.file() + ")";
        }
        String fn = frame.function().isEmpty() ? "<unknown>" : frame.function();
        return "  at " + fn + location;
    }
}
