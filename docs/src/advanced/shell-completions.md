# Shell Completions

Argos can automatically generate shell completion scripts for Bash, Zsh, and Fish shells. This provides users with tab completion for your CLI options, arguments, and values.

## Overview

Shell completions improve the user experience by:

- **Tab completion** for options (`--verbose`, `-v`)
- **Value completion** for enum options and choices
- **Subcommand completion** for domains
- **File path completion** where appropriate
- **Context-aware suggestions** based on current input

## Generating Completion Scripts

Argos provides built-in functions to generate completion scripts:

```kotlin
import onl.ycode.argos.Completions

class MyApp : Arguments() {
    val verbose by option("--verbose", "-v")
    val format by option("--format").oneOf("json", "xml", "csv")
    val level by option("--level").enum<LogLevel>()

    // Add a hidden completion command
    val generateCompletions by domain("completions")
        .help("Generate shell completion scripts")

    val shell by option("--shell")
        .onlyInDomains(::generateCompletions)
        .oneOf("bash", "zsh", "fish")
        .required()
        .help("Target shell")
}

fun main(args: Array<String>) {
    val app = MyApp()

    when (app.parseOrExit(args)) {
        is Arguments.ParseResult.Success -> {
            when (app.selectedDomain()) {
                "completions" -> generateCompletionScript(app)
                else -> runMainApplication(app)
            }
        }
    }
}

fun generateCompletionScript(app: MyApp) {
    val script = when (app.shell) {
        "bash" -> Completions.toBashCompletion(app)
        "zsh" -> Completions.toZshCompletion(app)
        "fish" -> Completions.toFishCompletion(app)
        else -> error("Unsupported shell: ${app.shell}")
    }

    println(script)
}
```

## Bash Completions

### Generating Bash Script

```kotlin
val bashScript = Completions.toBashCompletion(myApp)
```

### Installing Bash Completions

Users can install the completion script:

```bash
# Generate and install completion
myapp completions --shell bash > /etc/bash_completion.d/myapp

# Or for user-specific installation
myapp completions --shell bash > ~/.bash_completion.d/myapp

# Reload bash completions
source ~/.bashrc
```

### Bash Completion Features

- **Option completion**: `-v`, `--verbose`, `--format`
- **Value completion**: For `oneOf()` and `enum()` options
- **Long option values**: `--format=json` style completion
- **Subcommand completion**: Available domains
- **Context-aware**: Only shows relevant options

Example bash completion behavior:
```bash
$ myapp <TAB>
build    test    deploy    help

$ myapp --<TAB>
--verbose    --format    --level    --help

$ myapp --format <TAB>
json    xml    csv

$ myapp --format=<TAB>
json    xml    csv
```

## Zsh Completions

### Generating Zsh Script

```kotlin
val zshScript = Completions.toZshCompletion(myApp)
```

### Installing Zsh Completions

```bash
# Create completion directory if it doesn't exist
mkdir -p ~/.zsh/completions

# Generate and install completion
myapp completions --shell zsh > ~/.zsh/completions/_myapp

# Add to .zshrc if not already present
echo 'fpath=(~/.zsh/completions $fpath)' >> ~/.zshrc
echo 'autoload -U compinit && compinit' >> ~/.zshrc

# Reload zsh
source ~/.zshrc
```

### Zsh Completion Features

- **Rich descriptions**: Shows help text for each option
- **Smart completion**: Groups related options
- **Multiple formats**: Supports both `-o` and `--option` styles
- **Value completion**: Context-aware value suggestions
- **Error handling**: Handles invalid combinations gracefully

Example zsh completion behavior:
```bash
$ myapp <TAB>
build   -- Build the project
test    -- Run tests
deploy  -- Deploy to environment

$ myapp --<TAB>
--verbose  -- Enable verbose output
--format   -- Output format
--level    -- Log level
```

## Fish Completions

### Generating Fish Script

```kotlin
val fishScript = Completions.toFishCompletion(myApp)
```

### Installing Fish Completions

```bash
# Create completion directory
mkdir -p ~/.config/fish/completions

# Generate and install completion
myapp completions --shell fish > ~/.config/fish/completions/myapp.fish

# Fish automatically loads completions
```

### Fish Completion Features

- **Real-time completion**: Updates as you type
- **Description display**: Shows option descriptions
- **Value completion**: For enums and restricted choices
- **Subcommand support**: Domain completion with descriptions
- **Intelligent filtering**: Only shows relevant options

## Advanced Completion Examples

### Complex Application with Subcommands

```kotlin
enum class Environment { DEV, STAGING, PROD }
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

class DeployTool : Arguments() {
    // Domains
    val build by domain("build").help("Build the application")
    val test by domain("test").help("Run test suite")
    val deploy by domain("deploy").help("Deploy to environment")
    val status by domain("status").help("Check deployment status")

    // Global options
    val verbose by option("--verbose", "-v").help("Verbose output")
    val config by option("--config").help("Configuration file")

    // Build options
    val optimize by option("--optimize")
        .onlyInDomains(::build)
        .help("Enable optimizations")

    val target by option("--target")
        .oneOf("debug", "release")
        .onlyInDomains(::build)
        .default("debug")
        .help("Build target")

    // Test options
    val coverage by option("--coverage")
        .onlyInDomains(::test)
        .help("Generate coverage report")

    val parallel by option("--parallel")
        .int()
        .onlyInDomains(::test)
        .default(1)
        .help("Number of parallel test runners")

    // Deploy options
    val environment by option("--environment", "-e")
        .enum<Environment>()
        .onlyInDomains(::deploy)
        .required()
        .help("Target environment")

    val force by option("--force")
        .onlyInDomains(::deploy)
        .help("Force deployment")

    val rollback by option("--rollback")
        .onlyInDomains(::deploy)
        .help("Rollback to previous version")

    // Status options
    val format by option("--format")
        .oneOf("table", "json", "yaml")
        .onlyInDomains(::status)
        .default("table")
        .help("Output format")

    val watch by option("--watch")
        .onlyInDomains(::status)
        .help("Watch for changes")
}
```

This generates intelligent completions:

```bash
# Top-level commands
$ deploytool <TAB>
build    test    deploy    status

# Build-specific options
$ deploytool build --<TAB>
--optimize    --target    --verbose    --config

# Target values
$ deploytool build --target <TAB>
debug    release

# Deploy with environment
$ deploytool deploy --environment <TAB>
DEV    STAGING    PROD

# Status format options
$ deploytool status --format <TAB>
table    json    yaml
```

### Custom Value Completion

For advanced use cases, you can create completion scripts that handle custom value completion:

```kotlin
class GitTool : Arguments() {
    val branch by option("--branch")
        .help("Git branch name")

    val remote by option("--remote")
        .help("Git remote name")

    val tag by option("--tag")
        .help("Git tag name")
}

// You can customize the completion script to add git-specific completions
fun customGitCompletion(baseScript: String): String {
    return baseScript + """
# Custom git completions
_git_branches() {
    git branch --format='%(refname:short)' 2>/dev/null
}

_git_remotes() {
    git remote 2>/dev/null
}

_git_tags() {
    git tag 2>/dev/null
}

# Override completions for specific options
case "$prev" in
    --branch)
        COMPREPLY=( $(compgen -W "$(_git_branches)" -- "$cur") )
        return 0
        ;;
    --remote)
        COMPREPLY=( $(compgen -W "$(_git_remotes)" -- "$cur") )
        return 0
        ;;
    --tag)
        COMPREPLY=( $(compgen -W "$(_git_tags)" -- "$cur") )
        return 0
        ;;
esac
"""
}
```

## Integrating Completions into Your Application

### Built-in Completion Command

Add a completion subcommand to your application:

```kotlin
class MyApp : Arguments() {
    // Regular application domains
    val start by domain("start").help("Start the server")
    val stop by domain("stop").help("Stop the server")

    // Hidden completion domain
    val completions by domain("completions")
        .help("Generate shell completion scripts")

    val shell by option("--shell")
        .onlyInDomains(::completions)
        .oneOf("bash", "zsh", "fish")
        .required()
        .help("Target shell for completion")

    val install by option("--install")
        .onlyInDomains(::completions)
        .help("Install completion script automatically")
}

fun handleCompletions(app: MyApp) {
    val script = when (app.shell) {
        "bash" -> Completions.toBashCompletion(app)
        "zsh" -> Completions.toZshCompletion(app)
        "fish" -> Completions.toFishCompletion(app)
        else -> error("Unsupported shell")
    }

    if (app.install) {
        installCompletionScript(app.shell, script)
        println("✅ Completion script installed for ${app.shell}")
    } else {
        println(script)
    }
}

fun installCompletionScript(shell: String, script: String) {
    val home = System.getProperty("user.home")
    val file = when (shell) {
        "bash" -> File("$home/.bash_completion.d/myapp")
        "zsh" -> File("$home/.zsh/completions/_myapp")
        "fish" -> File("$home/.config/fish/completions/myapp.fish")
        else -> error("Unknown shell: $shell")
    }

    file.parentFile.mkdirs()
    file.writeText(script)
}
```

### Installation Instructions

Provide users with clear installation instructions:

```kotlin
fun showCompletionHelp() {
    println("""
Shell Completion Installation:

Bash:
  myapp completions --shell bash > ~/.bash_completion.d/myapp
  source ~/.bashrc

Zsh:
  mkdir -p ~/.zsh/completions
  myapp completions --shell zsh > ~/.zsh/completions/_myapp
  # Add to ~/.zshrc: fpath=(~/.zsh/completions $fpath)
  # Add to ~/.zshrc: autoload -U compinit && compinit

Fish:
  myapp completions --shell fish > ~/.config/fish/completions/myapp.fish

Or use the automatic installer:
  myapp completions --shell bash --install
  myapp completions --shell zsh --install
  myapp completions --shell fish --install
""")
}
```

## Testing Completions

### Manual Testing

Test your completions manually:

```bash
# Generate completion script
myapp completions --shell bash > /tmp/myapp_completion

# Source it in current shell
source /tmp/myapp_completion

# Test completions
myapp <TAB>
myapp --<TAB>
myapp --format <TAB>
```

### Automated Testing

Create tests to verify completion scripts:

```kotlin
@Test
fun testBashCompletionGeneration() {
    val app = MyApp()
    val script = Completions.toBashCompletion(app)

    // Verify script contains expected elements
    assertTrue(script.contains("--verbose"))
    assertTrue(script.contains("--format"))
    assertTrue(script.contains("json xml csv"))
}

@Test
fun testZshCompletionGeneration() {
    val app = MyApp()
    val script = Completions.toZshCompletion(app)

    // Verify zsh-specific elements
    assertTrue(script.contains("#compdef"))
    assertTrue(script.contains("_arguments"))
}
```

## Best Practices

### 1. Clear Option Names
Use descriptive option names that are easy to complete:
```kotlin
// Good: Clear, unambiguous
val outputFormat by option("--output-format")
val maxRetries by option("--max-retries")

// Avoid: Ambiguous abbreviations
val of by option("--of")
val mr by option("--mr")
```

### 2. Consistent Naming
Use consistent patterns across your application:
```kotlin
// Good: Consistent patterns
val inputFile by option("--input-file")
val outputFile by option("--output-file")
val configFile by option("--config-file")

// Avoid: Inconsistent naming
val input by option("--input")
val out by option("--output")
val configuration by option("--config")
```

### 3. Useful Value Completion
Provide completion for common values:
```kotlin
// Good: Enum completion
enum class LogLevel { DEBUG, INFO, WARN, ERROR }
val level by option("--level").enum<LogLevel>()

// Good: Restricted choices
val format by option("--format").oneOf("json", "xml", "csv", "yaml")

// Good: Boolean with explicit values
val ssl by option("--ssl").oneOf("true", "false", "auto")
```

### 4. Help Text for Completion
Write clear help text that appears in completions:
```kotlin
val threads by option("--threads")
    .int()
    .help("Number of worker threads (default: CPU count)")

val timeout by option("--timeout")
    .int()
    .help("Request timeout in seconds")
```

Shell completions make your CLI feel professional and user-friendly. Users can discover options naturally and work more efficiently with tab completion support.