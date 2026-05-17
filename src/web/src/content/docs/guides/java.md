---
title: Java
description: Capture errors with the sererr Java package.
---

```java
import fyi.sererr.Capture;

List<CapturedError> chain = Capture.capture(throwable, release, serverName);
```

## Idiosyncracies

- `Throwable.getStackTrace()` returns frames **most-recent-first** —
  matches our convention, no reversal.
- Chain walks via `getCause()`. Suppressed exceptions
  (`Throwable.getSuppressed()` — try-with-resources) attach as
  siblings sharing the parent's `parent_id`.
- `StackTraceElement.getFileName()` is `null` for synthetic / lambda
  frames; the library substitutes the declaring class's resource path.
- Builders are required (protobuf-java messages are immutable). The
  capture function hides the boilerplate.

## Source bundling

Maven (or Gradle) with `maven-source-plugin`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-source-plugin</artifactId>
  <executions>
    <execution>
      <id>attach-sources</id>
      <goals><goal>jar-no-fork</goal></goals>
    </execution>
  </executions>
</plugin>
```

The library reads `ClassLoader.getResourceAsStream(...)` from the
sources JAR at runtime.

## Manual recipe

```java
import fyi.sererr.CapturedError;
import fyi.sererr.StackFrame;
import fyi.sererr.ExceptionMechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CaptureStackTrace {

    public static List<CapturedError> capture(Throwable t, String release, String serverName) {
        List<CapturedError.Builder> builders = new ArrayList<>();
        Throwable current = t;
        while (current != null) {
            CapturedError.Builder b = CapturedError.newBuilder()
                .setType(current.getClass().getName())
                .setMessage(current.getMessage() == null ? "" : current.getMessage())
                .setRelease(release)
                .setServerName(serverName);

            for (StackTraceElement f : current.getStackTrace()) {
                b.addFrames(StackFrame.newBuilder()
                    .setFunction(f.getClassName() + "." + f.getMethodName())
                    .setModule(f.getClassName())
                    .setFile(f.getFileName() == null ? "" : f.getFileName())
                    .setLine(Math.max(0, f.getLineNumber()))
                    .setInApp(isAppFrame(f.getClassName())));
            }
            builders.add(b);
            current = current.getCause();
        }

        Collections.reverse(builders); // most-causal-first

        List<CapturedError> chain = new ArrayList<>(builders.size());
        for (int i = 0; i < builders.size(); i++) {
            ExceptionMechanism mech = ExceptionMechanism.newBuilder()
                .setType("generic")
                .setHandled(true)
                .setExceptionId(i)
                .setParentId(i == 0 ? 0 : i - 1)
                .build();
            chain.add(builders.get(i).setMechanism(mech).build());
        }
        return chain;
    }

    private static boolean isAppFrame(String className) {
        return !className.startsWith("java.")
            && !className.startsWith("javax.")
            && !className.startsWith("jdk.")
            && !className.startsWith("sun.")
            && !className.startsWith("com.sun.");
    }

    private CaptureStackTrace() {}
}
```
