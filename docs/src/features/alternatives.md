# Argos vs Alternatives

Honest comparison between Argos and other popular command-line parsing libraries based on their current capabilities (2025).

## Feature Comparison Table

| | Argos | picocli | Clikt |
|---------|-------|---------|-------|
| **LIBRARY FUNDAMENTALS** | | | |
| **Primary Language** | Kotlin | Java (Kotlin-compatible) | Kotlin |
| **Design Philosophy** | Property delegation DSL | Annotation-driven | Command class + property delegation |
| **Multiplatform Support** | ✅ | ❌ | ✅ |
| **Zero External Dependencies** | ✅ | ✅ | ❌ (markdown dependency) |
| **License** | Apache 2.0 | Apache 2.0 | Apache 2.0 |
| **Fat JAR Overhead** | +281 KB | +404 KB | +10,140 KB |
| **Native Binary Overhead** | +414 KB | ❌ | +2,440 KB |
| | | | |
| **CORE PARSING FEATURES** | | | |
| **Options & Flags** | Property delegation | Annotations on fields | Property delegation |
| **Short Options** | ✅ With clustering | ✅ With clustering | ✅ With clustering |
| **Type Conversion** | Fluent builders (.int(), .bool(), etc) | Built-in + custom converters | Extension functions |
| **Lists/Arrays** | List/Set builders with validation | Arrays, Lists, Maps | Counted/multiple values |
| **Arity Support** | ✅ Fixed arity with collections | ✅ Fixed + variable arity | ✅ Fixed + variable arity |
| **KeyValue/Map Options** | ✅ KeyValue type + lists/sets | ✅ Direct Map<K,V> support | ✅ associate() function |
| **Positional Arguments** | ✅ With type safety | ✅ Strongly typed | ✅ With type safety |
| **Subcommands** | Domain system (nestable) | Nested @Command classes | CliktCommand inheritance |
| **Validation** | Per-value + collection validators | Built-in + custom | Lambda-based validators |
| | | | |
| **CONSTRAINTS & GROUPS** | | | |
| **Required Options** | ✅ .required() | ✅ required=true | ✅ .required() |
| **Conditional Requirements** | ✅ Comprehensive (if present/absent/value) | ⚠️ Limited (via custom validation) | ⚠️ Limited (manual validation) |
| **Option Groups** | ✅ exactlyOne, atMostOne, atLeastOne | ✅ @ArgGroup with exclusive/validate | ✅ mutuallyExclusiveOptions, cooccurringOptions |
| **Conflicts** | ✅ Built-in conflictsWith() | ⚠️ Via custom validation | ⚠️ Manual validation |
| **Domain-Scoped Constraints** | ✅ Constraints active only in specific domains | ❌ | ❌ |
| **Domain Fragments** | ✅ Reusable constraint templates | ⚠️ Mixin annotations | ❌ |
| | | | |
| **INPUT SOURCES** | | | |
| **Environment Variables** | ✅ fromEnv() | ✅ defaultValue="${ENV_VAR}" | ✅ envvar() |
| **Interactive Prompts** | ✅ input() with hidden flag | ✅ interactive=true | ✅ prompt() for any type |
| **Password Input** | ✅ Hidden input | ✅ Hidden input | ✅ Hidden input |
| **Argument Files** | ✅ @file support | ✅ @file support | ✅ File-based arguments |
| **Config File Defaults** | ❌ | ❌ | ✅ ValueSource (properties, JSON, etc.) |
| | | | |
| **OUTPUT & USER EXPERIENCE** | | | |
| **Help Generation** | ✅ Auto-generated | ✅ Auto-generated | ✅ Auto-generated |
| **ANSI Colors** | ✅ Terminal abstraction (Plain/ANSI/Markdown) | ✅ ANSI color support | ✅ Color-aware output |
| **Help Formatting** | ✅ Customizable | ✅ Highly customizable | ✅ Customizable |
| **Shell Completion** | ✅ Bash/Zsh/Fish generation | ✅ Built-in comprehensive | ✅ Bash/Zsh/Fish |
| **"Did You Mean"** | ✅ Edit distance suggestions | ✅ Typo suggestions | ✅ Suggestion engine |
| **Error Aggregation** | ✅ Show multiple errors at once | ✅ Lenient mode | ✅ MultiUsageError |
| **Internationalization (i18n)** | ✅ ArgosI18n registry (all strings) | ✅ Resource bundles (all strings) | ⚠️ Localization interface (framework strings only, not user help text) |
| | | | |
| **ADVANCED FEATURES** | | | |
| **Context Objects** | ❌ | ❌ | ✅ Context sharing in command hierarchy |
| **Exit Code Control** | ✅ parseOrExit() with configurable exit codes | ✅ Automatic exit codes | ✅ ProgramResult exceptions |
| **Testing Utilities** | ⚠️ Basic (terminal mocking) | ✅ Comprehensive test API | ✅ Built-in test framework |
| **GraalVM Native** | ✅ Works with K/N | ✅ Excellent support + annotation processor | ✅ Works well |
| **Documentation Generation** | ❌ | ✅ Man pages, HTML, PDF | ❌ |
| **Dependency Injection** | ❌ | ✅ Guice, Spring, Micronaut | ❌ |
| **No Special Annotations Required** | ✅ | ❌ (Annotation-based) | ✅ |
| **Value Source Tracking** | ✅ | ⚠️ Limited | ⚠️ Limited |
| | | | |