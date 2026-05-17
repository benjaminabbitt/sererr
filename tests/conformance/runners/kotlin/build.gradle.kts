// sererr — Kotlin conformance runner.
//
// Mirrors the Java conformance runner but exercises the Kotlin
// surface (:packages:kotlin). Step definitions live in Kotlin and
// invoke the Kotlin `capture` / `toCapturedErrorChain` / `toDebugInfo`
// functions rather than the Java statics — so this suite is what
// catches regressions in the Kotlin layer specifically.

plugins {
    kotlin("jvm") version "2.0.20"
}

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(project(":packages:kotlin"))
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("io.cucumber:cucumber-picocontainer:7.18.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Mirror the Java runner's env-var contract; matches Rust.
    System.getenv("SERERR_FEATURES_DIR")?.let { environment("SERERR_FEATURES_DIR", it) }
    System.getenv("SERERR_FIXTURES_DIR")?.let { environment("SERERR_FIXTURES_DIR", it) }
    if (System.getenv("SERERR_FEATURES_DIR") == null) {
        environment("SERERR_FEATURES_DIR",
            file("../../../../tests/conformance/features").absolutePath)
    }
    if (System.getenv("SERERR_FIXTURES_DIR") == null) {
        environment("SERERR_FIXTURES_DIR",
            file("../../../../tests/conformance/fixtures").absolutePath)
    }
    systemProperty("cucumber.features", environment["SERERR_FEATURES_DIR"]!!)
    systemProperty("cucumber.glue", "fyi.sererr.conformance")
    systemProperty("cucumber.publish.quiet", "true")
    testLogging {
        events("passed", "skipped", "failed")
    }
}
