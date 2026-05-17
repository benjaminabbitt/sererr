// sererr — root Gradle settings.
//
// Defines the JVM multi-project for the Java + Kotlin packages so they
// can cross-reference via `project(":packages:java")` /
// `project(":packages:kotlin")`. The conformance runners live in their
// own Gradle projects under `tests/conformance/runners/{java,kotlin}`
// and consume these via `includeBuild` (composite build).

rootProject.name = "sererr"

include(":packages:java")
include(":packages:kotlin")
include(":tests:conformance:runners:java")
include(":tests:conformance:runners:kotlin")

// Map logical paths to physical directories (packages live under
// `packages/<lang>/`, not `:packages:java/`).
project(":packages:java").projectDir = file("packages/java")
project(":packages:kotlin").projectDir = file("packages/kotlin")
project(":tests:conformance:runners:java").projectDir = file("tests/conformance/runners/java")
project(":tests:conformance:runners:kotlin").projectDir = file("tests/conformance/runners/kotlin")
