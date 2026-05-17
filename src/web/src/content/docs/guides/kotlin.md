---
title: Kotlin
description: Capture errors with the sererr Kotlin package.
---

```kotlin
import fyi.sererr.kotlin.capture

val chain = capture(t, release = RELEASE, serverName = SERVER_NAME)
```

Thin wrapper over the Java package with idiomatic Kotlin sugar.

## Idiosyncracies

- All Java semantics apply: `Throwable.stackTrace` is
  most-recent-first; chain via `cause`; `suppressedExceptions` attach
  as siblings.
- **Coroutine state-machine frames** (`invokeSuspend`, `resumeWith`,
  `BaseContinuationImpl`, `DispatchedTask`) are demystified using
  `kotlinx-coroutines-debug`'s `StackTraceRecovery` — async traces show
  the suspending function names, not the compiler-generated continuation
  machinery.
- For projects not using `kotlinx-coroutines-debug`, the library falls
  back to raw frames; suspend-function context is lost.

## Source bundling

Same as Java — `maven-source-plugin` (Gradle: `withSourcesJar()`).
Kotlin sources end up in the same JAR as Java sources.

## Manual recipe

```kotlin
import fyi.sererr.CapturedError
import fyi.sererr.StackFrame
import fyi.sererr.ExceptionMechanism

private val frameworkPrefixes = listOf("java.", "javax.", "jdk.", "sun.", "com.sun.", "kotlin.", "kotlinx.")

fun capture(t: Throwable, release: String, serverName: String): List<CapturedError> {
    val builders = mutableListOf<CapturedError.Builder>()
    var current: Throwable? = t
    while (current != null) {
        val b = CapturedError.newBuilder()
            .setType(current.javaClass.name)
            .setMessage(current.message.orEmpty())
            .setRelease(release)
            .setServerName(serverName)
        current.stackTrace.forEach { f ->
            b.addFrames(StackFrame.newBuilder()
                .setFunction("${f.className}.${f.methodName}")
                .setModule(f.className)
                .setFile(f.fileName.orEmpty())
                .setLine(maxOf(0, f.lineNumber))
                .setInApp(frameworkPrefixes.none { f.className.startsWith(it) }))
        }
        builders += b
        current = current.cause
    }

    builders.reverse() // most-causal-first
    return builders.mapIndexed { i, b ->
        val mech = ExceptionMechanism.newBuilder()
            .setType("generic")
            .setHandled(true)
            .setExceptionId(i)
            .setParentId(if (i == 0) 0 else i - 1)
            .build()
        b.setMechanism(mech).build()
    }
}
```
