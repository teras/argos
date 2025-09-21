# Module Argos

**Links**: [Project Website](https://argos.yot.is) | [Source Repository](https://github.com/teras/argos)

Argos is a lightweight yet feature-full type-safe command-line argument parsing library for Kotlin Multiplatform projects. Despite its small footprint and zero dependencies, it provides a declarative DSL for defining CLI interfaces while supporting advanced features like subcommands, validation rules, and automatic help generation.

## Key Features

- **Minimalistic Size**: Incredibly small footprint with maximum functionality
- **Type Safety**: Leverage Kotlin's type system for compile-time argument validation
- **Multiplatform**: Native support for JVM, Linux Native (x64/ARM64), and Windows
- **Zero Dependencies**: No external runtime dependencies - completely self-contained
- **Rich DSL**: Intuitive domain-specific language that reads like natural language
- **Advanced Constraints**: Built-in constraints, custom validation, and conditional requirements
- **Subcommands**: Full support for git-style subcommand interfaces
- **Terminal Support**: ANSI colors, plain text, and Markdown output formats

## Architecture

The library uses property delegation to create a clean, type-safe DSL where options and positional arguments are defined as delegated properties that automatically register themselves with the Arguments class.

## Usage

```kotlin
class MyApp : Arguments() {
    val verbose by option("--verbose", "-v").bool().default(false)
    val inputFile by positional().required()
    val help by help()
}
```

For detailed documentation and examples, visit the [Argos Documentation](https://argos.yot.is).