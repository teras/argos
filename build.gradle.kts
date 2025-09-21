/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

// Root build configuration for multi-module Argos project

plugins {
    kotlin("multiplatform") version "2.2.10" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false
}

group = "onl.ycode"
version = "1.0.0"

// License information
extra["license"] = "Apache-2.0"
extra["licenseName"] = "Apache License 2.0"
extra["licenseUrl"] = "http://www.apache.org/licenses/LICENSE-2.0.txt"

// Configure all subprojects
subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// Convenience tasks for the entire project
tasks.register("buildAll") {
    group = "build"
    description = "Build all modules"
    dependsOn(gradle.includedBuilds.map { it.task(":build") })
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("cleanAll") {
    group = "build"
    description = "Clean all modules"
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
    dependsOn(subprojects.map { "${it.path}:clean" })
}

tasks.register("testAll") {
    group = "verification"
    description = "Test all modules"
    dependsOn(subprojects.map { "${it.path}:test" })
}

// Convenience tasks for demo module
tasks.register("runDemo") {
    group = "application"
    description = "Run the JVM demo application"
    dependsOn(":argos-demo:runJvm")
}

tasks.register("fatJar") {
    group = "application"
    description = "Build demo fat JAR"
    dependsOn(":argos-demo:fatJar")
}