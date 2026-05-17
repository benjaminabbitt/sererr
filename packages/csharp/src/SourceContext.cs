namespace Sererr;

/// <summary>
/// Populates a <see cref="StackFrame"/>'s source-context fields from an
/// <see cref="ISourceProvider"/>. Records are immutable, so we return a
/// new frame rather than mutating in place.
/// </summary>
public static class SourceContext
{
    /// <summary>
    /// Returns a copy of <paramref name="frame"/> with
    /// <c>ContextLine</c>, <c>PreContext</c> and <c>PostContext</c>
    /// populated from <paramref name="provider"/>.
    ///
    /// No-op (returns <paramref name="frame"/> unchanged) when the
    /// provider has no source for the file, when the frame's line is 0,
    /// or when the line number exceeds the file's length.
    /// </summary>
    /// <param name="surrounding">
    /// Number of lines of pre/post context to capture (5 is a sensible
    /// default — matches Sentry's UI).
    /// </param>
    public static StackFrame PopulateContext(
        StackFrame frame,
        ISourceProvider provider,
        int surrounding)
    {
        if (frame.Line == 0)
        {
            return frame;
        }
        var source = provider.GetSource(frame.File);
        if (source is null)
        {
            return frame;
        }
        // Match Rust's `str::lines()` which strips trailing \n / \r\n
        // and drops a trailing empty line.
        var lines = SplitLines(source);
        var idx = checked((int)frame.Line) - 1;
        if (idx < 0 || idx >= lines.Count)
        {
            return frame;
        }
        var start = Math.Max(0, idx - surrounding);
        var endExclusive = Math.Min(lines.Count, idx + 1 + surrounding);
        return frame with
        {
            ContextLine = lines[idx],
            PreContext = lines.GetRange(start, idx - start),
            PostContext = lines.GetRange(idx + 1, endExclusive - (idx + 1)),
        };
    }

    private static List<string> SplitLines(string source)
    {
        // Rust's `str::lines()`:
        //   "a\nb\n".lines() -> ["a", "b"]
        //   "a\n".lines() -> ["a"]
        //   "".lines() -> []
        var result = new List<string>();
        var start = 0;
        for (var i = 0; i < source.Length; i++)
        {
            if (source[i] == '\n')
            {
                var lineEnd = i;
                if (lineEnd > start && source[lineEnd - 1] == '\r') lineEnd--;
                result.Add(source.Substring(start, lineEnd - start));
                start = i + 1;
            }
        }
        if (start < source.Length)
        {
            result.Add(source.Substring(start));
        }
        return result;
    }
}
