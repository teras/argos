plugins {
    kotlin("multiplatform")
    id("com.gradleup.shadow") version "9.1.0"
}

kotlin {
    jvmToolchain(11)

    jvm()

    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    mingwX64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":argos"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    dependsOn("jvmJar")
    from(kotlin.targets.getByName("jvm").compilations.getByName("main").output)
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}