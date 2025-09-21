plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow") version "9.1.0"
}

dependencies {
    implementation("info.picocli:picocli:4.7.5")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}