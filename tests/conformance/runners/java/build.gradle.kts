// sererr — Java conformance runner.
//
// Cucumber-JVM suite that exercises the shared
// tests/conformance/features/ corpus against the Java implementation
// (:packages:java). Mirrors the Rust runner one step at a time.

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(project(":packages:java"))
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("io.cucumber:cucumber-picocontainer:7.18.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.platform:junit-platform-suite:1.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Required env vars for the runner; matched against the Rust
    // runner so cross-language behaviour stays in lockstep.
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
