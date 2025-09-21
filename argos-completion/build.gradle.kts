/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.10"
    id("org.jetbrains.dokka") version "1.9.20"
    `maven-publish`
}

group = "onl.ycode"
version = "1.0.0"

// License information
extra["license"] = "Apache-2.0"
extra["licenseName"] = "Apache License 2.0"
extra["licenseUrl"] = "http://www.apache.org/licenses/LICENSE-2.0.txt"

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(11)

    // Library targets (no executables - clean library only)
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Depend on the base argos library
                implementation(project(":argos"))
            }
        }

        val nativeMain by getting
        val posixMain by creating { dependsOn(nativeMain) }
        val linuxMain by getting { dependsOn(posixMain) }

        // --- JVM tests only ---
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter:5.11.0")
            }
        }
    }
}

// Documentation configuration
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))

    dokkaSourceSets {
        named("commonMain") {
            moduleName.set("Argos Completion")
            moduleVersion.set(version.toString())
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(uri("https://github.com/teras/argos/tree/main/argos-completion/src/commonMain/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

// Task to run JVM demo
tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "Run the completion demo on JVM"
    val mainCompilation = kotlin.jvm().compilations["main"]
    classpath = mainCompilation.output.classesDirs + mainCompilation.runtimeDependencyFiles
    mainClass.set("onl.ycode.argos.completion.demo.CompletionDemoKt")
}

// Task to create a fat JAR with demo
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Create a fat JAR with demo and all dependencies"
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "onl.ycode.argos.completion.demo.CompletionDemoKt"
    }
    from(kotlin.jvm().compilations["main"].output)
    from(configurations.getByName("jvmRuntimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
}
