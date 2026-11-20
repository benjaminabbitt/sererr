---
title: Go
description: Capture errors with the sererr Go package.
---

```go
import "sererr.fyi/sererr/packages/go"

frames := sererr.CaptureFrames()  // MUST be called at originating site
chain := sererr.Capture(err, frames, release)
```

## Idiosyncracies

- Go has no exception model; everything is a returned `error`.
- **Frames must be captured at the originating error site.**
  `runtime.Callers` reads the calling goroutine's stack — moving the
  call later loses the failure frames. Wrapping with
  `fmt.Errorf("...%w", err)` preserves the chain but not the
  trace; capture frames at the leaf.
- Chain walks via `errors.Unwrap`. For `pkg/errors`-style wrapping,
  the library also tries `Causer.Cause()` if present.
- `runtime.Callers` frame order is **most-recent-first** — matches
  our convention, no reversal needed for frames.
- `AggregateException`-equivalents (e.g. `errors.Join`) are flattened
  into siblings sharing the parent's `parent_id`.

## Source bundling

```go
import "embed"

//go:embed *.go
var sourceFS embed.FS
```

The library's source-context helper reads from this `embed.FS`.

## Manual recipe

```go
package mypkg

import (
    "errors"
    "os"
    "reflect"
    "runtime"
    "strings"

    sererr "sererr.fyi/sererr/packages/go"
)

func Capture(err error, frames []*sererr.StackFrame, release string) []*sererr.CapturedError {
    serverName, _ := os.Hostname()

    var chain []*sererr.CapturedError
    current := err
    for current != nil {
        chain = append(chain, &sererr.CapturedError{
            Type:       typeName(current),
            Message:    current.Error(),
            Frames:     frames,
            Mechanism:  &sererr.ExceptionMechanism{Type: "generic", Handled: true},
            Release:    release,
            ServerName: serverName,
        })
        current = errors.Unwrap(current)
    }

    for i, j := 0, len(chain)-1; i < j; i, j = i+1, j-1 {
        chain[i], chain[j] = chain[j], chain[i]
    }
    for i, entry := range chain {
        entry.Mechanism.ExceptionId = uint32(i)
        if i > 0 {
            entry.Mechanism.ParentId = uint32(i - 1)
        }
    }
    return chain
}

func CaptureFrames() []*sererr.StackFrame {
    pcs := make([]uintptr, 64)
    n := runtime.Callers(2, pcs)
    pcs = pcs[:n]
    iter := runtime.CallersFrames(pcs)
    var out []*sererr.StackFrame
    for {
        f, more := iter.Next()
        out = append(out, &sererr.StackFrame{
            Function: f.Function,
            File:     f.File,
            Line:     uint32(f.Line),
            InApp: !strings.HasPrefix(f.Function, "runtime.") &&
                   !strings.HasPrefix(f.Function, "reflect."),
        })
        if !more {
            break
        }
    }
    return out
}

func typeName(err error) string {
    t := reflect.TypeOf(err)
    if t == nil {
        return ""
    }
    if t.Kind() == reflect.Ptr {
        t = t.Elem()
    }
    return t.PkgPath() + "." + t.Name()
}
```
