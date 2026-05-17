---
title: C#
description: Capture errors with the sererr C# package.
---

```csharp
using Sererr;

var chain = StackTrace.Capture(ex, release: RELEASE);
```

## Idiosyncracies

- `StackTrace.GetFrames()` returns frames **most-recent-first** —
  matches our convention.
- Chain walks via `Exception.InnerException`.
- `AggregateException.InnerExceptions` (plural — typically from
  `Task.WhenAll`) attach as siblings sharing the parent's `parent_id`.
- **Async state-machine frames** (`MoveNext`, `AsyncStateMachineBox`,
  `Resume`) are demystified via Ben.Demystifier so async traces show
  the awaited method names.
- **Line numbers in release builds require PDBs.** Use
  `<DebugType>embedded</DebugType>` in the producing project's csproj
  to embed PDBs in the assembly; otherwise `GetFileLineNumber()`
  returns 0 in release.

## Source bundling

```xml
<ItemGroup>
  <EmbeddedResource Include="src\**\*.cs" LogicalName="source/%(RecursiveDir)%(Filename)%(Extension)" />
</ItemGroup>
```

The library reads sources via
`Assembly.GetManifestResourceStream("source/path/to/file.cs")` at
capture time.

## Manual recipe

```csharp
using System;
using System.Collections.Generic;
using System.Diagnostics;
using Sererr;
using ProtoStackFrame = Sererr.StackFrame;
using SysStackFrame = System.Diagnostics.StackFrame;

public static class CaptureStackTrace
{
    public static List<CapturedError> Capture(Exception ex, string release)
    {
        var serverName = Environment.MachineName;

        var entries = new List<CapturedError>();
        Exception? current = ex;
        while (current != null)
        {
            var entry = new CapturedError
            {
                Type = current.GetType().FullName ?? current.GetType().Name,
                Message = current.Message,
                Release = release,
                ServerName = serverName,
            };

            var trace = new StackTrace(current, fNeedFileInfo: true);
            foreach (SysStackFrame frame in trace.GetFrames())
            {
                var method = frame.GetMethod();
                if (method is null) continue;
                var declaring = method.DeclaringType?.FullName ?? "";
                entry.Frames.Add(new ProtoStackFrame
                {
                    Function = $"{declaring}.{method.Name}",
                    Module = declaring,
                    File = frame.GetFileName() ?? "",
                    Line = (uint)Math.Max(0, frame.GetFileLineNumber()),
                    InApp = !(declaring.StartsWith("System.")
                              || declaring.StartsWith("Microsoft.")
                              || declaring.StartsWith("Internal.")),
                });
            }
            entries.Add(entry);
            current = current.InnerException;
        }

        entries.Reverse(); // most-causal-first
        for (int i = 0; i < entries.Count; i++)
        {
            entries[i].Mechanism = new ExceptionMechanism
            {
                Type = "generic",
                Handled = true,
                ExceptionId = (uint)i,
                ParentId = (uint)(i == 0 ? 0 : i - 1),
            };
        }
        return entries;
    }
}
```
