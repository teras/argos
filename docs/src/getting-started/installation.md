# Installation

This guide will help you add Argos to your Kotlin project and get up and running quickly.

## Requirements

- **Kotlin**: 2.2.10 or later
- **Gradle**: 7.0 or later (recommended: 8.0+)
- **JVM**: Java 11 or later (for JVM targets)
- **Native**: Appropriate toolchain for your target platform

## Gradle Setup

### Kotlin Multiplatform

Add Argos to your `build.gradle.kts` file:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("onl.ycode:argos:0.1.0")
            }
        }
    }
}
```

### JVM Only

For JVM-only projects:

```kotlin
dependencies {
    implementation("onl.ycode:argos:0.1.0")
}
```

!!! note "Multi-Module Project"
    Argos is organized as a multi-module project:

    - **argos**: The core library (use this in your projects)
    - **argos-demo**: Demo applications for testing and examples

### Complete Multiplatform Example

Here's a complete `build.gradle.kts` for a multiplatform CLI application:

```kotlin
plugins {
    kotlin("multiplatform") version "2.2.10"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("onl.ycode:argos:0.1.0")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

## Maven Setup

For Maven projects, add to your `pom.xml`:

```xml
<dependency>
    <groupId>onl.ycode</groupId>
    <artifactId>argos</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Platform-Specific Notes

### JVM

No additional setup required. Argos works out of the box on any JVM 11+ environment.

```kotlin
kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
}
```

### Native Linux

Supported architectures:
- **x64** (Intel/AMD 64-bit)
- **ARM64** (ARM 64-bit)

```kotlin
kotlin {
    linuxX64() // For x64 systems
    linuxArm64() // For ARM64 systems (e.g., Raspberry Pi 4)
}
```

### Windows

Supported via MinGW toolchain:

```kotlin
kotlin {
    mingwX64() // Windows 64-bit
}
```

!!! note "Windows Requirements"
    You'll need to install the MinGW-w64 toolchain. The easiest way is through:
    - [MSYS2](https://www.msys2.org/) (recommended)
    - [TDM-GCC](https://jmeubank.github.io/tdm-gcc/)
    - [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022)

## Version Compatibility

| Argos Version | Kotlin Version | Status |
|---------------|----------------|---------|
| 0.1.x | 2.2.10+ | Current |

## IDE Setup

### IntelliJ IDEA

1. **Import Project**: Use "Import from existing sources" or open the `build.gradle.kts` file
2. **Enable Kotlin Plugin**: Should be enabled by default in recent versions
3. **Gradle Sync**: Let IDEA download dependencies and set up the project
4. **Multiplatform Support**: IDEA will automatically detect multiplatform configuration

### VS Code

1. **Kotlin Extension**: Install the official Kotlin extension
2. **Gradle Extension**: Install the Gradle extension for build support
3. **Open Folder**: Open your project folder in VS Code

## Verify Installation

Create a simple test file to verify everything is working:

=== "src/commonMain/kotlin/Main.kt"

    ```kotlin
    import onl.ycode.argos.Arguments
    import onl.ycode.argos.parse

    class TestApp : Arguments(
        appName = "test",
        appDescription = "Test Argos installation"
    ) {
        val verbose by option("--verbose", "-v").bool().default(false)
            .help("Enable verbose output")
        val help by help()
    }

    fun main(args: Array<String>) {
        val app = TestApp()

        app.parse(args,
            onSuccess = {
                println("✅ Argos is working!")
                println("Verbose mode: ${app.verbose}")
            },
            onError = { error, _ ->
                println("Error: ${error.message}")
            }
        )
    }
    ```

### Build and Test

```bash
# Build the project
./gradlew build

# Run with help flag
./gradlew run --args="--help"

# Run with verbose flag
./gradlew run --args="--verbose"
```

Expected output:
```
✅ Argos is working!
Verbose mode: true
```

## Troubleshooting

### Common Issues

#### Kotlin Version Mismatch
```
The Kotlin version used in dependencies (2.2.10) differs from the version used in project (2.1.0)
```

**Solution**: Update your Kotlin version to 2.2.10 or later:
```kotlin
plugins {
    kotlin("multiplatform") version "2.2.10"
}
```

#### Native Toolchain Missing
```
Could not find Native distribution. Run ':downloadKotlinNativeCompiler' or use 'kotlin.native.distribution' property
```

**Solution**: Let Gradle download the native compiler:
```bash
./gradlew downloadKotlinNativeCompiler
```

#### Dependency Resolution Issues
```
Could not resolve onl.ycode:argos:1.0.0
```

**Solution**: Ensure you have `mavenCentral()` in your repositories:
```kotlin
repositories {
    mavenCentral()
}
```

#### Windows MinGW Issues
```
Program "gcc" not found in PATH
```

**Solution**: Install MinGW-w64 and add to PATH, or use MSYS2:
```bash
# Using MSYS2
pacman -S mingw-w64-x86_64-toolchain
```

### Getting Help

If you encounter issues:

1. **Check Version Compatibility**: Ensure you're using compatible Kotlin/Gradle versions
2. **Clean Build**: Run `./gradlew clean build` to clear any cached artifacts
3. **Update Dependencies**: Make sure you're using the latest Argos version
4. **GitHub Issues**: Search [existing issues](https://github.com/teras/argos/issues) or create a new one

## Next Steps

Now that you have Argos installed, you can:

- [Create your first application](quick-start.md)
- [Follow the detailed tutorial](first-app.md)
- [Read the user guide](../guide/basic-usage.md)
- [Explore examples](../examples/basic-cli.md)