// Loads a CapturedError from the conformance fixture JSON shape.
//
// The conformance fixtures use snake_case JSON keys (cross-language) and
// a top-level `captured_error` envelope.

using System.Text.Json;
using Sererr;

namespace Sererr.Tests;

internal static class FixtureLoader
{
    public static CapturedError LoadCapturedError(string json)
    {
        using var doc = JsonDocument.Parse(json);
        var captured = doc.RootElement.GetProperty("captured_error");
        return ReadCapturedError(captured);
    }

    private static CapturedError ReadCapturedError(JsonElement el)
    {
        var frames = new List<StackFrame>();
        if (TryGet(el, "frames", out var framesEl) && framesEl.ValueKind == JsonValueKind.Array)
        {
            foreach (var fe in framesEl.EnumerateArray())
            {
                frames.Add(ReadFrame(fe));
            }
        }

        ExceptionMechanism? mech = null;
        if (TryGet(el, "mechanism", out var mechEl) && mechEl.ValueKind == JsonValueKind.Object)
        {
            mech = ReadMechanism(mechEl);
        }

        return new CapturedError
        {
            Type = GetString(el, "type"),
            Message = GetString(el, "message"),
            Frames = frames,
            Mechanism = mech,
            Release = GetString(el, "release"),
            ServerName = GetString(el, "server_name"),
        };
    }

    private static StackFrame ReadFrame(JsonElement el)
    {
        return new StackFrame
        {
            Function = GetString(el, "function"),
            Module = GetString(el, "module"),
            Package = GetString(el, "package"),
            File = GetString(el, "file"),
            AbsPath = GetString(el, "abs_path"),
            Line = GetUInt(el, "line"),
            ContextLine = GetString(el, "context_line"),
            PreContext = GetStringList(el, "pre_context"),
            PostContext = GetStringList(el, "post_context"),
            SourceLink = GetString(el, "source_link"),
            InApp = GetBool(el, "in_app"),
        };
    }

    private static ExceptionMechanism ReadMechanism(JsonElement el)
    {
        var data = new SortedDictionary<string, string>(StringComparer.Ordinal);
        if (TryGet(el, "data", out var dataEl) && dataEl.ValueKind == JsonValueKind.Object)
        {
            foreach (var prop in dataEl.EnumerateObject())
            {
                data[prop.Name] = prop.Value.GetString() ?? "";
            }
        }

        return new ExceptionMechanism
        {
            Type = GetString(el, "type"),
            Description = GetString(el, "description"),
            Handled = GetBool(el, "handled"),
            Synthetic = GetBool(el, "synthetic"),
            HelpLink = GetString(el, "help_link"),
            Source = GetString(el, "source"),
            ExceptionId = GetUInt(el, "exception_id"),
            ParentId = GetUInt(el, "parent_id"),
            IsExceptionGroup = GetBool(el, "is_exception_group"),
            Data = data,
        };
    }

    private static bool TryGet(JsonElement el, string name, out JsonElement value)
    {
        return el.TryGetProperty(name, out value);
    }

    private static string GetString(JsonElement el, string name)
    {
        return TryGet(el, name, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString() ?? ""
            : "";
    }

    private static uint GetUInt(JsonElement el, string name)
    {
        return TryGet(el, name, out var v) && v.ValueKind == JsonValueKind.Number
            ? v.GetUInt32()
            : 0u;
    }

    private static bool GetBool(JsonElement el, string name)
    {
        return TryGet(el, name, out var v)
               && (v.ValueKind == JsonValueKind.True || v.ValueKind == JsonValueKind.False)
               && v.GetBoolean();
    }

    private static List<string> GetStringList(JsonElement el, string name)
    {
        var list = new List<string>();
        if (TryGet(el, name, out var v) && v.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in v.EnumerateArray())
            {
                list.Add(item.GetString() ?? "");
            }
        }
        return list;
    }
}
