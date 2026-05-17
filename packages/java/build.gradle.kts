// sererr — Java package
// Publishes fyi.sererr:sererr to Maven Central.

plugins {
    `java-library`
    `maven-publish`
    jacoco                                              // coverage
    id("info.solidsoft.pitest") version "1.15.0"         // mutation testing
    id("com.google.protobuf") version "0.9.4"
}

group = "fyi.sererr"
version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

repositories {
    mavenCentral()
}

java {
    // Container image ships JDK 17 (eclipse-temurin:17-jdk). When
    // building locally on a JDK > 17, set source/target compatibility
    // so consumers on JDK 17 can still ingest the artifacts.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api("com.google.protobuf:protobuf-java:3.25.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.1" }
}

sourceSets {
    main {
        proto {
            srcDir("../../proto")
        }
    }
}

// ----------------------------------------------------------------------
// Proto-generated source rewrite.
//
// The proto file pins `option java_package = "fyi.sererr"` which would
// collide with our hand-rolled plain types (StackFrame, CapturedError,
// ExceptionMechanism). We CAN'T modify proto/. So we post-process the
// protoc output: shift generated classes from `fyi.sererr.*` to
// `fyi.sererr.v1.*` (and from `com.google.rpc.*` to
// `com.google.rpc.*` unchanged — it already lives in its own package).
//
// The transform: (a) rewrite the `package fyi.sererr;` declaration,
// (b) rewrite any `outer_class` reference inside that file (none in
// our schema), (c) move the file into `fyi/sererr/v1/`.
// ----------------------------------------------------------------------

val rewriteProtoPackage = tasks.register("rewriteProtoPackage") {
    dependsOn("generateProto")
    val srcDir = layout.buildDirectory.dir("generated/source/proto/main/java").get().asFile
    val dstDir = layout.buildDirectory.dir("generated/source/proto-rewritten/main/java").get().asFile
    inputs.dir(srcDir)
    outputs.dir(dstDir)
    // Two substitutions are needed:
    //   (a) the package declaration line — `package fyi.sererr;` →
    //       `package fyi.sererr.v1;`
    //   (b) every fully-qualified reference like `fyi.sererr.Foo` →
    //       `fyi.sererr.v1.Foo`. The negative lookahead avoids a
    //       second `v1` when the substitution is idempotently run on
    //       already-rewritten input.
    val packageDecl = Regex("""\bpackage\s+fyi\.sererr\s*;""")
    val refPattern = Regex("""\bfyi\.sererr\.(?!v1\b)([A-Z])""")
    doLast {
        // Wipe the destination so renames in the rewritten output don't
        // leave stale files behind.
        dstDir.deleteRecursively()
        dstDir.mkdirs()
        srcDir.walkTopDown().filter { it.isFile }.forEach { src ->
            val relPath = src.relativeTo(srcDir).path
            val outRel: String
            val content: String
            if (relPath.startsWith("fyi/sererr/") && !relPath.startsWith("fyi/sererr/v1/")) {
                outRel = relPath.replaceFirst("fyi/sererr/", "fyi/sererr/v1/")
                content = src.readText()
                    .let { packageDecl.replace(it, "package fyi.sererr.v1;") }
                    .let { refPattern.replace(it, "fyi.sererr.v1.$1") }
            } else {
                outRel = relPath
                content = src.readText()
            }
            val out = dstDir.resolve(outRel)
            out.parentFile.mkdirs()
            out.writeText(content)
        }
    }
}

// Wire the rewritten proto sources into the main source set. The
// protobuf-gradle-plugin auto-registers the *raw* generated dir; we
// detach it (`setSrcDirs(emptyList())` on the proto source set won't
// work because the plugin re-adds it) and instead add our rewritten
// dir to the Java source set. The raw dir is then excluded from
// compilation in `afterEvaluate` (a no-op exclude in source-set config
// doesn't survive the protobuf plugin's late wiring).
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/source/proto-rewritten/main/java"))
        }
    }
}

afterEvaluate {
    tasks.named<JavaCompile>("compileJava") {
        dependsOn(rewriteProtoPackage)
        val rawProto = layout.buildDirectory.dir("generated/source/proto/main/java").get().asFile
        exclude { details ->
            details.file.absolutePath.startsWith(rawProto.absolutePath)
        }
    }
    // Gradle 8 validation: any task consuming the rewritten dir
    // (sourcesJar packs all Java sources) must declare the dependency
    // explicitly. Same for the proto-raw consumer (javadocJar).
    listOf("sourcesJar", "javadocJar").forEach { name ->
        tasks.findByName(name)?.dependsOn(rewriteProtoPackage)
    }
}

// Override the jar artifact base name. By default Gradle uses the
// project name ("java" — the directory name), which would publish as
// `java-0.1.0.jar`. We want `sererr-0.1.0.jar`.
//
// duplicatesStrategy = EXCLUDE because the protobuf-gradle-plugin
// auto-registers both the raw generated dir AND we add the
// rewritten dir; sourcesJar walks both. The rewritten copy wins; the
// raw copy is harmless because compileJava already excludes it.
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("sererr")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ----------------------------------------------------------------------
// Coverage (JaCoCo) + mutation testing (pitest).
// ----------------------------------------------------------------------

jacoco {
    toolVersion = "0.8.12"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)   // for CI / Codecov
        html.required.set(true)
        // Exclude buf-generated proto code (auto-rewritten under fyi.sererr.v1).
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude("fyi/sererr/v1/**") }
            })
        )
    }
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(listOf("fyi.sererr.*"))
    // Exclude the rewritten proto-generated package from mutation.
    excludedClasses.set(listOf("fyi.sererr.v1.*"))
    mutationThreshold.set(85)    // native fail-below-85% gate
    timestampedReports.set(false)
    outputFormats.set(listOf("HTML", "XML"))
}

tasks.test {
    useJUnitPlatform()
    // Forward fixture-dir env var if present (so the fixture
    // byte-equivalence test can find the .pb files).
    System.getenv("SERERR_FIXTURES_DIR")?.let { environment("SERERR_FIXTURES_DIR", it) }
    // Default to the in-repo fixtures path if the var isn't set.
    if (System.getenv("SERERR_FIXTURES_DIR") == null) {
        environment("SERERR_FIXTURES_DIR", file("../../tests/conformance/fixtures").absolutePath)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sererr"
            from(components["java"])
            pom {
                name = "sererr"
                description = "Sentry-compat structured stack-trace + error-chain capture."
                url = "https://sererr.fyi"
                licenses {
                    license { name = "MIT"; url = "https://opensource.org/licenses/MIT" }
                    license { name = "BSD-3-Clause"; url = "https://opensource.org/licenses/BSD-3-Clause" }
                }
            }
        }
    }
}
