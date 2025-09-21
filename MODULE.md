# Module Argos

Argos is a Kotlin Multiplatform command-line argument parsing library that provides a type-safe DSL for defining CLI interfaces.

This library enables developers to create sophisticated command-line applications using Kotlin's property delegation and type system. It supports multiple platforms including JVM, Linux (x64/ARM64), and Windows.

Key features include type-safe DSL, multiplatform support, rich validation, advanced features like subcommands and shell completions, flexible output formatting, and zero external dependencies.

Basic usage example:

```kotlin
class MyArgs : Arguments() {
    val verbose by option("-v", "--verbose").bool()
    val count by option("-c", "--count").int().default(1)
    val input by positional()
}
```

The library consists of two main packages:
- onl.ycode.argos - Core DSL classes and parsing engine
- onl.ycode.argos.terminal - Terminal output formatting system