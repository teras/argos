# Argos vs Alternatives

Brief comparison between Argos and other popular command-line parsing libraries.

| | Argos | PicoCLI | Clikt |
|---------|-------|---------|-------|
| **LIBRARY FUNDAMENTALS** | | | |
| **JVM/Kotlin Support** | Native Kotlin DSL | Java with Kotlin compatibility | Native Kotlin DSL |
| **Design** | Type-safe DSL with property delegation | Annotation-driven | Command class inheritance + property delegation |
| **Linux Native** | Full native support | ❌ | Full native support |
| **Windows Native** | Full native support | ❌ | Full native support |
| **Zero Dependencies** | Yes | Yes | ❌ |
| | | | |
| **CORE PARSING FEATURES** | | | |
| **Options & Flags** | Property delegation DSL | Annotation-driven | Property delegation with commands |
| **Type Conversion** | Fluent method chaining | Built-in converters | Type-safe conversions |
| **Collections** | List and set builders | Arrays and collections | Multiple value handling |
| **Arity Support** | Fixed arity with collections | Fixed and variable arity | Fixed arity support |
| **Validation** | Inline validation functions | Custom validators | Lambda-based validation |
| **Option Groups** | Constraint-based grouping | Mutually exclusive groups | Parameter groups |
| **Subcommands** | Domain system | Nested commands | Command inheritance |
| | | | |
| **INPUT SOURCES** | | | |
| **Environment Variables** | Environment fallback | Built-in env support | Environment integration |
| **Password Input** | Secure password prompts | Interactive input | Hidden input prompts |
| **Argument Files** | File argument expansion | Argument file support | File-based arguments |
| **Map Support** | KeyValue pairs | Direct map handling | Associate function |
| | | | |
| **OUTPUT & USER EXPERIENCE** | | | |
| **Help Generation** | Auto-generated with colors | ANSI formatted help | Rich help formatting |
| **Color Support** | Terminal abstraction | ANSI color support | Color-aware output |
| **TAB Completion** | Shell script generation | Built-in completion | Shell completion |
| **"Did You Mean" Suggestions** | Edit distance suggestions | Typo correction | Built-in suggestions |
| **Error Aggregation** | Multiple error collection | Lenient parsing mode | MultiUsageError exception |
| **Internationalization** | ❌ | Built-in i18n | Localization interface |
| | | | |
| **ADVANCED ARCHITECTURE** | | | |
| **Context Objects** | ❌ | ❌ | Parent-child data sharing |
| **Domain Fragments** | Reusable constraint templates | Mixin components | ❌ |
| **Domain-scoped Constraints** | Context-aware validation | ArgGroup with inheritance | ❌ |
| **Exit Code Management** | ❌ | Automatic exit codes | Built-in exit handling |
| **Testing Utilities** | Basic terminal infrastructure | Comprehensive test support | Testing framework |
| **Man Page Generation** | ❌ | Documentation generation | ❌ |
| **Best For** | Kotlin Multiplatform, Complex constraints | Java projects, Dependency injection | Kotlin-first, Node.js, Async operations |