namespace Sererr;

/// <summary>
/// Source provider for capture-time <c>ContextLine</c> /
/// <c>PreContext</c> / <c>PostContext</c> population.
///
/// Implementors return the contents of a source file by path (the
/// <see cref="StackFrame.File"/> value). Producers shipping embedded
/// source as a .NET resource implement this against
/// <c>Assembly.GetManifestResourceStream</c>; producers without
/// embedded source return <c>null</c> and let the consumer resolve
/// lazily from the release SHA.
/// </summary>
public interface ISourceProvider
{
    /// <summary>
    /// Returns the source contents for <paramref name="file"/>, or
    /// <c>null</c> when not available.
    /// </summary>
    string? GetSource(string file);
}
