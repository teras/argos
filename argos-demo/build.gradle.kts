/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.10"
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

    // Demo targets with executables
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
        binaries {
            executable {
                mainClass.set("onl.ycode.argos.demo.TestKt")
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "onl.ycode.argos.demo.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                entryPoint = "onl.ycode.argos.demo.main"
            }
        }
    }
    mingwX64 {
        binaries {
            executable {
                entryPoint = "onl.ycode.argos.demo.main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":argos"))
            }
        }
    }
}

// Demo fat JAR configuration
tasks.register<Jar>("fatJar") {
    group = "application"
    description = "Creates a demo fat JAR with all dependencies for manual testing"
    archiveBaseName.set("argos-demo")
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(mapOf("Main-Class" to "onl.ycode.argos.demo.TestKt"))
    }

    from(kotlin.jvm().compilations.getByName("main").output)
    dependsOn(kotlin.jvm().compilations.getByName("main").compileTaskProvider)

    from({
        kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
}
