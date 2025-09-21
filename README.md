# Argos - Kotlin Multiplatform CLI Argument Parser

<img align="left" width="120" height="120" src="docs/src/Argos.svg" alt="Argos Logo" style="margin-right: 20px;">

Argos is a lightweight yet feature-full Kotlin Multiplatform command-line argument parsing library that provides a type-safe DSL for defining CLI interfaces. Despite its small footprint and zero dependencies, it delivers sophisticated features through Kotlin's property delegation and type system, enabling developers to create complex command-line applications with elegant, maintainable code.

<br clear="left">

<br>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7f52ff.svg)](https://kotlinlang.org/)
[![Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-orange.svg)](https://kotlinlang.org/docs/multiplatform.html)

## ✨ Features

- **🪶 Minimalistic Size**: Incredibly small footprint with maximum functionality
- **🎯 Type-Safe DSL**: Leverage Kotlin's type system for compile-time safety
- **🌐 Multiplatform**: Native support for JVM, Linux (x64/ARM64), and Windows
- **🔧 Zero Dependencies**: Self-contained library with no external runtime dependencies
- **🎨 Rich Terminal Support**: ANSI colors, plain text, and Markdown output formats
- **🏗️ Advanced Domains**: Sophisticated subcommand system with constraint inheritance
- **🔒 Security First**: Built-in password prompts and secure input handling
- **⚡ Flexible Validation**: Comprehensive validation system with custom rules
- **🌍 Environment Integration**: Seamless environment variable support
- **📖 Automatic Help**: Beautiful, context-aware help generation
- **🔀 Collection Types**: Native support for lists, sets, and count options

## 🚀 Quick Start

### Basic Example

```kotlin
import onl.ycode.argos.*

class MyApp : Arguments() {
    val input by option("--input", "-i")
        .required()
        .help("Input file to process")

    val output by option("--output", "-o")
        .default("output.txt")
        .help("Output file location")

    val verbose by option("--verbose", "-v")
        .bool()
        .help("Enable verbose logging")

    val count by option("--count", "-c")
        .int()
        .default(1)
        .validate("Must be positive") { it > 0 }
        .help("Number of iterations")

    val help by help()
    val version by version("1.0.0")
}

fun main(args: Array<String>) {
    val app = MyApp().parse(args) ?: return

    println("Processing ${app.input} -> ${app.output}")
    if (app.verbose) println("Verbose mode enabled")
    println("Running ${app.count} iterations")
}
```

### Advanced Multi-Domain Example

```kotlin
class GitTool : Arguments() {
    // Global options
    val verbose by option("--verbose", "-v")
        .bool()
        .help("Enable verbose output")

    // Subcommands (domains)
    val commitDomain by domain("commit")
        .label("Commit Changes")
        .help("Create a new commit")
        .required(::message)

    val branchDomain by domain("branch")
        .label("Branch Operations")
        .help("Manage branches")
        .exactlyOne(::create, ::delete, ::list)

    // Domain-specific options
    val message by option("--message", "-m")
        .onlyInDomains(::commitDomain)
        .help("Commit message")

    val create by option("--create")
        .bool()
        .onlyInDomains(::branchDomain)
        .help("Create new branch")

    val delete by option("--delete")
        .bool()
        .onlyInDomains(::branchDomain)
        .help("Delete branch")

    val list by option("--list")
        .bool()
        .onlyInDomains(::branchDomain)
        .help("List branches")

    val help by help()
    val version by version("2.0.0")
}
```

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("onl.ycode:argos:0.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'onl.ycode:argos:0.1.0'
}
```

## 📚 Documentation

Comprehensive documentation is available at **[argos.yot.is](https://argos.yot.is)**:

- **[Core Concepts](https://argos.yot.is/guide/basic-usage/)**: Arguments, options, types, and collections
- **[Features](https://argos.yot.is/features/validation/)**: Validation, constraints, environment variables
- **[Domain System](https://argos.yot.is/guide/subcommands/)**: Subcommands and advanced patterns
- **[Examples](https://argos.yot.is/examples/build-tool/)**: Real-world application examples

### Key Documentation Sections

| Section | Description |
|---------|-------------|
| [Arguments Class](https://argos.yot.is/core/arguments-class/) | Configuration and constructor options |
| [Options](https://argos.yot.is/core/options/) | All option types and configurations |
| [Validation](https://argos.yot.is/features/validation/) | Validation system and custom rules |
| [Constraints](https://argos.yot.is/features/constraints/) | Requirements and group constraints |
| [Domains](https://argos.yot.is/domains/overview/) | Subcommand system and inheritance |
| [Examples](https://argos.yot.is/examples/) | Build tools, deployment, and migration examples |

## Building and Running

### Library Module (argos)

```bash
# Build clean library (no demo code)
./gradlew :argos:build

# Run tests
./gradlew :argos:test

# Publish to local Maven repository
./gradlew :argos:publishToMavenLocal
```

### Demo Module (argos-demo)

```bash
# Run JVM demo
./gradlew :argos-demo:runJvm

# Build demo fat JAR
./gradlew :argos-demo:fatJar
java -jar argos-demo/build/libs/argos-demo-0.1.0-fat.jar --help

# Run native Linux demo
./gradlew :argos-demo:runDebugExecutableLinuxX64

# Build native Windows executable
./gradlew :argos-demo:linkDebugExecutableMingwX64
```

### Convenience Tasks (from root)

```bash
# Build everything
./gradlew buildAll

# Run demo (JVM)
./gradlew runDemo

# Build demo fat JAR
./gradlew fatJar

# Clean everything
./gradlew cleanAll

# Test everything
./gradlew testAll
```

## Key Benefits of Multi-Module Approach

### ✅ Clean Library Distribution
- Published library contains **zero** demo or test code
- Library artifacts are completely clean and professional
- Demo applications are in a separate module

### ✅ Clear Separation of Concerns
- **argos**: Pure library code with comprehensive tests
- **argos-demo**: Demo applications that showcase library features

### ✅ Multi-Platform Demo Support
- Single demo source that works across JVM, Linux, and Windows
- Fat JAR for easy distribution
- Native executables for platform-specific deployment

### ✅ Professional Development Workflow
- Library development isolated from demo applications
- Easy to add new demo scenarios without affecting library
- Clean dependency management between modules

## 📄 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

See the [library documentation](https://argos.yot.is) and [detailed API reference](https://argos.yot.is/dokka/) for development guidelines.
