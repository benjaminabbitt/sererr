// sererr — Kotlin package
// Thin Kotlin layer over the Java package; publishes fyi.sererr:sererr-kotlin.

plugins {
    kotlin("jvm") version "2.0.20"
    `maven-publish`
    jacoco                                              // coverage
    id("info.solidsoft.pitest") version "1.15.0"         // mutation testing
}

group = "fyi.sererr"
version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        // Compile to Java 17 bytecode; runtime is on JDK 17.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":packages:java"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Override jar archive base name (default would be project name "kotlin").
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("sererr-kotlin")
}

// Coverage + mutation
jacoco {
    toolVersion = "0.8.12"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(listOf("fyi.sererr.kotlin.*"))
    mutationThreshold.set(85)
    timestampedReports.set(false)
    outputFormats.set(listOf("HTML", "XML"))
    // Forward the same coroutine-debug flags to pitest's mutant JVMs so
    // the reflective stack-trace-recovery branch in `Capture.kt` actually
    // does something observable. Without these, tests asserting
    // `recovered !== throwable` would pass on the original AND every
    // mutant (both produce identity), making those mutants unkillable.
    jvmArgs.set(listOf(
        "-Dkotlinx.coroutines.debug=on",
        "-Dkotlinx.coroutines.stacktrace.recovery=true",
    ))
    // Do not mutate calls to compiler-emitted `kotlin.jvm.internal.*`
    // runtime helpers. The Kotlin compiler injects calls like
    // `Intrinsics.checkNotNullParameter` /
    // `Intrinsics.checkNotNullExpressionValue` at the entry of every
    // public function with a non-nullable parameter or non-nullable
    // expression result. Pitest's `VoidMethodCallMutator` removes those
    // calls, but the resulting mutants are mathematically equivalent:
    // the values are guaranteed non-null by Kotlin's type system, so
    // the check is a no-op at runtime, and removing it cannot change
    // observable behaviour. This is the same category as the
    // auto-generated `fyi/sererr/v1/**` proto code (which the parent
    // pitest config already excludes): not user-authored logic under
    // test. The commercial `arcmutate-kotlin` plugin handles this
    // automatically (as the pitest output itself warns); vanilla
    // pitest requires it to be configured by hand.
    avoidCallsTo.set(setOf("kotlin.jvm.internal.Intrinsics"))
}

tasks.test {
    useJUnitPlatform()
    // Enable kotlinx-coroutines stack-trace recovery so tests can
    // exercise the `recoverCoroutineFrames` branch end-to-end. These
    // system properties are read once, in `kotlinx.coroutines.DebugKt`'s
    // static initializer, so they must be present before any test code
    // touches the coroutines library. Setting them on the JVM command
    // line is the only ordering-safe way to do that under pitest, which
    // spawns a fresh JVM per mutant and may invoke `recoverStackTrace`
    // before the first test class's `@BeforeAll` runs.
    systemProperty("kotlinx.coroutines.debug", "on")
    systemProperty("kotlinx.coroutines.stacktrace.recovery", "true")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sererr-kotlin"
            from(components["kotlin"])
            pom {
                name = "sererr-kotlin"
                description = "Kotlin API for the sererr error-capture proto."
                url = "https://sererr.fyi"
                licenses {
                    license { name = "MIT"; url = "https://opensource.org/licenses/MIT" }
                    license { name = "BSD-3-Clause"; url = "https://opensource.org/licenses/BSD-3-Clause" }
                }
            }
        }
    }
}
