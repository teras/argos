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

// Add Dokka plugin dependencies
dependencies {
    dokkaHtmlPlugin("org.jetbrains.dokka:dokka-base:1.9.20")
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
        val nativeMain by getting
        val posixMain by creating { dependsOn(nativeMain) }
        val linuxMain by getting { dependsOn(posixMain) }

        // --- JVM tests only ---
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))                         // kotlin.test
                implementation("org.junit.jupiter:junit-jupiter:5.11.0") // API + engine
            }
        }
        // No native/mingw test sourceSets
    }
}

// Apply external build configurations
apply(from = "tools/test-config.gradle.kts")

// Documentation configuration (requires plugin context)
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))

    dokkaSourceSets {
        named("commonMain") {
            moduleName.set("Argos")
            moduleVersion.set(version.toString())
            includes.from("MODULE.md")
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(uri("https://github.com/teras/argos/tree/main/src/commonMain/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

// Configure Dokka plugin for custom logo
tasks.dokkaHtml.configure {
    // Configure plugin parameters for custom assets and logo
    // Use Argos.svg as the custom logo to replace the default Kotlin logo
    pluginsMapConfiguration.set(
        mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """
                {
                    "customAssets": ["${rootProject.file("docs/src/Argos.svg").absolutePath}"],
                    "footerMessage": "© 2025 Argos - Generated with Dokka",
                    "separateInheritedMembers": false,
                    "mergeImplicitExpectActualDeclarations": false
                }
            """.trimIndent()
        )
    )
}
