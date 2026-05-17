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
}

tasks.test {
    useJUnitPlatform()
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
