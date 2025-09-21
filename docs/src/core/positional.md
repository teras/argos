# Positional Arguments

Positional arguments are command-line parameters specified by their position rather than by name. They're commonly used for essential inputs like file names, commands, or primary data.

## Basic Positional Arguments

Create positional arguments using the `positional()` function:

```kotlin
class MyApp : Arguments() {
    // Single positional argument (nullable)
    val inputFile by positional()                        // String?

    // Required positional argument
    val outputFile by positional().required()            // String

    // Positional with default value
    val configFile by positional().default("config.json") // String
}
```

## Usage Examples

```bash
# Single positional
myapp input.txt

# Multiple positionals (order matters)
myapp input.txt output.txt config.json
#     ^^^^^^^^^  ^^^^^^^^^^  ^^^^^^^^^^^
#     first      second      third
```

## Collection Positionals

### List of Positional Arguments

```kotlin
class MyApp : Arguments() {
    // Collects all remaining positional arguments
    val files by positional().list()                     // List<String>

    // List with validation
    val inputFiles by positional().list()
        .validate("File must exist") { File(it).exists() }
}
```

```bash
# Collects multiple files
myapp file1.txt file2.txt file3.txt dir/file4.txt
```

### Set of Positional Arguments

```kotlin
class MyApp : Arguments() {
    // Unique positional arguments (duplicates removed)
    val sources by positional().set()                    // Set<String>
}
```

## Positional Argument Order

Positional arguments are processed in the order they're defined:

```kotlin
class FileProcessor : Arguments() {
    val command by positional().required()               // First
    val inputFile by positional().required()             // Second
    val outputFile by positional().required()            // Third
    val additionalFiles by positional().list()           // Last - consumes rest
}
```

```bash
# Command line mapping
fileprocessor convert input.txt output.txt extra1.txt extra2.txt
#             ^^^^^^^  ^^^^^^^^^  ^^^^^^^^^^  ^^^^^^^^^ ^^^^^^^^^
#             command  inputFile  outputFile  additionalFiles...
```

**Important:** Positional lists (`.list()`) must always be the last positional argument since they consume all remaining arguments.

## Type Conversions

Positional arguments support the same type conversions as options:

```kotlin
class MathApp : Arguments() {
    val operation by positional().oneOf("add", "subtract", "multiply")
    val firstNumber by positional().int().required()
    val secondNumber by positional().int().required()
}
```

```bash
mathapp add 42 58
```

## Validation

Apply validation to positional arguments:

```kotlin
class FileApp : Arguments() {
    val inputFile by positional().required()
        .validate("File must exist") { File(it).exists() }
        .validate("File must be readable") { File(it).canRead() }

    val outputFormat by positional().default("json")
        .validate("Format must be supported") { it in listOf("json", "xml", "yaml") }
}
```

## Help Text

Add descriptive help for positional arguments:

```kotlin
class MyApp : Arguments() {
    val inputFile by positional().required()
        .help("Path to the input file to process")

    val outputDir by positional().default("./output")
        .help("Directory where processed files will be saved")

    val extraFiles by positional().list()
        .help("Additional files to include in processing")
}
```

## Common Patterns

### File Processing Pattern

```kotlin
class FileProcessor : Arguments() {
    // Primary input (required)
    val inputFile by positional().required()
        .validate("Input file must exist") { File(it).exists() }
        .help("Input file to process")

    // Optional output (defaults to input + suffix)
    val outputFile by positional()
        .help("Output file (default: input file with .processed suffix)")

    // Additional files for batch processing (must be last)
    val additionalFiles by positional().list()
        .help("Additional files to process in batch")
}
```

```bash
# Process single file with default output
processor input.txt

# Process with custom output
processor input.txt custom-output.txt

# Batch processing
processor main.txt output.txt file1.txt file2.txt file3.txt
```

### Command Pattern

```kotlin
class GitLikeTool : Arguments() {
    val command by positional().required()
        .oneOf("init", "add", "commit", "push", "pull")
        .help("Git command to execute")

    val target by positional()
        .help("Target for the command (file, branch, etc.)")

    val additionalArgs by positional().list()
        .help("Additional arguments for the command (must be last)")
}
```

```bash
# Git-like usage
gittool init
gittool add file.txt
gittool commit "Initial commit"
gittool push origin main
```

### Calculator Pattern

```kotlin
class Calculator : Arguments() {
    val operation by positional().required()
        .oneOf("add", "sub", "mul", "div", "pow")
        .help("Mathematical operation to perform")

    val numbers by positional().double().list()
        .validate("At least two numbers required") { true }
        .validateCollection("Need at least 2 numbers") { it.size >= 2 }
        .help("Numbers to operate on (must be last)")
}
```

```bash
calc add 1.5 2.3 4.7 8.1
calc mul 3 4 5
calc pow 2 8
```

## Advanced Features

### Custom Transformations

```kotlin
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class PathProcessor : Arguments() {
    val inputPath by positional().required()
        .map("valid file path") { path ->
            val file = File(path)
            if (file.exists()) file.absolutePath else null
        }
        .help("Input file or directory path")

    val outputPath by positional()
        .map("output directory") { path ->
            if (path != null) {
                Paths.get(path).toAbsolutePath().toString()
            } else null
        }
        .help("Output directory path")
}
```

### Environment Variable Fallbacks

```kotlin
class DeployTool : Arguments() {
    val environment by positional()
        .fromEnv("DEPLOY_ENV")
        .default("development")
        .oneOf("development", "staging", "production")
        .help("Deployment environment")

    val version by positional()
        .fromEnv("DEPLOY_VERSION")
        .help("Version to deploy")
}
```

### Conditional Requirements

```kotlin
class BackupTool : Arguments() {
    val sourceDir by positional().required()
        .help("Source directory to backup")

    val backupDir by positional()
        .help("Backup destination directory")

    val archiveName by positional()
        .requireIfAnyPresent(::backupDir)  // Only required if backupDir specified
        .help("Archive file name")
}
```

## Complete Examples

### File Converter

```kotlin
enum class Format { JSON, XML, YAML, CSV }

class FileConverter : Arguments(
    appName = "converter",
    appDescription = "Convert files between different formats"
) {
    val inputFile by positional().required()
        .validate("Input file must exist") { File(it).exists() }
        .validate("Input file must be readable") { File(it).canRead() }
        .help("Input file to convert")

    val outputFile by positional()
        .help("Output file (default: input file with new extension)")

    val sourceFormat by positional()
        .enum<Format>()
        .help("Source format (auto-detected if not specified)")

    val targetFormat by positional().required()
        .enum<Format>()
        .help("Target format for conversion")

    val options by positional().list()
        .help("Additional conversion options (must be last)")

    val help by help()
}
```

```bash
# Basic conversion
converter data.json output.xml JSON XML

# Auto-detect source format
converter data.json output.yaml XML

# With conversion options
converter data.csv output.json CSV JSON --header --quote-all
```

### Image Processor

```kotlin
class ImageProcessor : Arguments(
    appName = "imgproc",
    appDescription = "Process and transform images"
) {
    val operation by positional().required()
        .oneOf("resize", "crop", "rotate", "filter", "convert")
        .help("Image operation to perform")

    val inputImage by positional().required()
        .validate("Image file must exist") { File(it).exists() }
        .validate("Must be image file") {
            it.lowercase().endsWith(".jpg") ||
            it.lowercase().endsWith(".png") ||
            it.lowercase().endsWith(".gif")
        }
        .help("Input image file")

    val outputImage by positional()
        .help("Output image file (default: input + _processed)")

    val parameters by positional().list()
        .help("Operation-specific parameters (must be last)")

    val help by help()
    val version by version("1.0.0")
}
```

```bash
# Resize image
imgproc resize photo.jpg photo_small.jpg 800 600

# Crop with parameters
imgproc crop image.png cropped.png 100 100 400 300

# Convert format
imgproc convert photo.jpg photo.png
```

## Best Practices

### 1. Order by Importance

```kotlin
// Good: Most important arguments first, list last
val command by positional().required()        // Essential
val target by positional().required()         // Primary input
val options by positional().list()            // Must be last - consumes rest

// WRONG: List before other positionals
val options by positional().list()            // This would consume everything!
val required by positional().required()       // Never reached - ERROR!
```

### 2. Provide Sensible Defaults

```kotlin
// Good: Useful defaults for optional positionals
val outputFile by positional().default("output.txt")
val format by positional().default("json")

// Consider: When should positionals be nullable vs have defaults?
val optionalTarget by positional()            // String? = null
```

### 3. Use Clear Help Text

```kotlin
// Good: Describes what the argument is and how it's used
val inputFile by positional().required()
    .help("Path to the input file to process (must exist and be readable)")

// Avoid: Vague descriptions
val file by positional().help("A file")
```

### 4. Validate Appropriately

```kotlin
// Good: Validate what matters for your application
val inputFile by positional().required()
    .validate("File must exist") { File(it).exists() }
    .validate("File must not be empty") { File(it).length() > 0 }

// Don't over-validate: Let the application handle business logic
val configFile by positional()
    // Let the app decide what to do with missing config files
```

Positional arguments provide a clean, intuitive interface for essential inputs. Use them for primary data that users expect to specify without flags.