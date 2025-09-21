# Basic CLI Example

This example demonstrates building a simple yet complete command-line application using Argos. We'll create a file management tool that showcases the most common CLI patterns.

## Overview

We'll build **FileTool** - a utility for basic file operations:

- 📁 List directory contents
- 📋 Copy files with options
- 🗑️ Delete files safely
- 📊 Show file statistics
- 🔍 Search file contents

## Complete Implementation

```kotlin
import onl.ycode.argos.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

enum class Operation { LIST, COPY, DELETE, STATS, SEARCH }
enum class SizeUnit { BYTES, KB, MB, GB }

class FileTool : Arguments() {
    // Main operation
    val operation by positional("operation")
        .enum<Operation>()
        .help("Operation to perform: list, copy, delete, stats, search")

    // File paths
    val source by positional("source")
        .help("Source file or directory")

    val destination by positional("destination")
        .optional()
        .help("Destination path (required for copy operation)")

    // Options
    val recursive by option("--recursive", "-r")
        .help("Operate recursively on directories")

    val force by option("--force", "-f")
        .help("Force operation without confirmation")

    val verbose by option("--verbose", "-v")
        .help("Verbose output")

    val quiet by option("--quiet", "-q")
        .help("Suppress output except errors")

    val pattern by option("--pattern", "-p")
        .help("Search pattern (for search operation)")

    val sizeUnit by option("--size-unit")
        .enum<SizeUnit>()
        .default(SizeUnit.BYTES)
        .help("Unit for size display")

    val maxDepth by option("--max-depth")
        .int()
        .help("Maximum directory depth for recursive operations")

    val includeHidden by option("--include-hidden")
        .help("Include hidden files (starting with .)")

    init {
        settings {
            appName = "filetool"
            appDescription = "A comprehensive file management utility"
        }

        constraints {
            // Copy operation requires destination
            "destination" requiredIf { operation == Operation.COPY }

            // Search operation requires pattern
            "pattern" requiredIf { operation == Operation.SEARCH }

            // Cannot be both quiet and verbose
            atMostOne("--quiet", "--verbose")

            // Max depth only makes sense with recursive
            "maxDepth" requiredIf { recursive }

            // Validation for max depth
            "maxDepth" requiredMin 1
        }
    }
}

fun main(args: Array<String>) {
    val tool = FileTool()

    when (tool.parseOrExit(args)) {
        is Arguments.ParseResult.Success -> {
            try {
                executeTool(tool)
            } catch (e: Exception) {
                if (!tool.quiet) {
                    println("❌ Error: ${e.message}")
                }
                exitProcess(1)
            }
        }
    }
}

fun executeTool(tool: FileTool) {
    val sourceFile = File(tool.source)

    // Validate source exists
    if (!sourceFile.exists()) {
        throw IllegalArgumentException("Source path does not exist: ${tool.source}")
    }

    when (tool.operation) {
        Operation.LIST -> listFiles(sourceFile, tool)
        Operation.COPY -> copyFiles(sourceFile, File(tool.destination!!), tool)
        Operation.DELETE -> deleteFiles(sourceFile, tool)
        Operation.STATS -> showStats(sourceFile, tool)
        Operation.SEARCH -> searchFiles(sourceFile, tool.pattern!!, tool)
    }
}

fun listFiles(source: File, tool: FileTool) {
    if (!tool.quiet) {
        println("📁 Listing: ${source.path}")
        println()
    }

    val files = if (tool.recursive) {
        collectFiles(source, tool)
    } else {
        source.listFiles()?.toList() ?: emptyList()
    }

    val filteredFiles = if (tool.includeHidden) {
        files
    } else {
        files.filter { !it.name.startsWith(".") }
    }

    for (file in filteredFiles.sortedBy { it.name }) {
        val icon = when {
            file.isDirectory -> "📁"
            file.extension.lowercase() in listOf("txt", "md", "log") -> "📄"
            file.extension.lowercase() in listOf("jpg", "png", "gif") -> "🖼️"
            file.extension.lowercase() in listOf("zip", "tar", "gz") -> "📦"
            else -> "📄"
        }

        val size = if (file.isFile) {
            " (${formatSize(file.length(), tool.sizeUnit)})"
        } else {
            ""
        }

        val path = if (tool.verbose) {
            file.absolutePath
        } else {
            file.name
        }

        println("$icon $path$size")
    }

    if (!tool.quiet) {
        val fileCount = filteredFiles.count { it.isFile }
        val dirCount = filteredFiles.count { it.isDirectory }
        println()
        println("Total: $fileCount files, $dirCount directories")
    }
}

fun copyFiles(source: File, destination: File, tool: FileTool) {
    if (!tool.quiet) {
        println("📋 Copying: ${source.path} → ${destination.path}")
    }

    if (destination.exists() && !tool.force) {
        print("Destination exists. Overwrite? (y/N): ")
        val response = readlnOrNull()?.lowercase()
        if (response != "y" && response != "yes") {
            println("Copy cancelled.")
            return
        }
    }

    when {
        source.isFile -> {
            copyFile(source, destination, tool)
        }
        source.isDirectory && tool.recursive -> {
            copyDirectory(source, destination, tool)
        }
        source.isDirectory -> {
            throw IllegalArgumentException("Source is a directory. Use --recursive to copy directories.")
        }
    }

    if (!tool.quiet) {
        println("✅ Copy completed successfully")
    }
}

fun copyFile(source: File, destination: File, tool: FileTool) {
    if (tool.verbose) {
        println("  Copying file: ${source.name}")
    }

    destination.parentFile?.mkdirs()
    Files.copy(
        source.toPath(),
        destination.toPath(),
        StandardCopyOption.REPLACE_EXISTING
    )
}

fun copyDirectory(source: File, destination: File, tool: FileTool) {
    if (tool.verbose) {
        println("  Creating directory: ${destination.name}")
    }

    destination.mkdirs()

    val files = collectFiles(source, tool, depth = 1)
    for (file in files) {
        val relativePath = source.toPath().relativize(file.toPath())
        val targetFile = destination.toPath().resolve(relativePath).toFile()

        if (file.isDirectory) {
            if (tool.verbose) {
                println("  Creating directory: $relativePath")
            }
            targetFile.mkdirs()
        } else {
            if (tool.verbose) {
                println("  Copying file: $relativePath")
            }
            copyFile(file, targetFile, tool)
        }
    }
}

fun deleteFiles(source: File, tool: FileTool) {
    val files = if (tool.recursive && source.isDirectory) {
        collectFiles(source, tool).sortedByDescending { it.path.length }
    } else {
        listOf(source)
    }

    if (!tool.force && !tool.quiet) {
        println("🗑️  About to delete ${files.size} items:")
        files.take(5).forEach { println("  ${it.path}") }
        if (files.size > 5) {
            println("  ... and ${files.size - 5} more")
        }
        print("Continue? (y/N): ")
        val response = readlnOrNull()?.lowercase()
        if (response != "y" && response != "yes") {
            println("Delete cancelled.")
            return
        }
    }

    var deletedCount = 0
    for (file in files) {
        try {
            if (tool.verbose) {
                println("  Deleting: ${file.path}")
            }
            if (file.delete()) {
                deletedCount++
            }
        } catch (e: Exception) {
            if (!tool.quiet) {
                println("⚠️  Failed to delete: ${file.path} (${e.message})")
            }
        }
    }

    if (!tool.quiet) {
        println("✅ Deleted $deletedCount items")
    }
}

fun showStats(source: File, tool: FileTool) {
    if (!tool.quiet) {
        println("📊 Statistics for: ${source.path}")
        println()
    }

    val files = if (tool.recursive && source.isDirectory) {
        collectFiles(source, tool)
    } else {
        listOf(source)
    }

    val filesByType = files.filter { it.isFile }
        .groupBy { it.extension.lowercase().ifEmpty { "no extension" } }

    val totalSize = files.filter { it.isFile }.sumOf { it.length() }
    val fileCount = files.count { it.isFile }
    val dirCount = files.count { it.isDirectory }

    println("📈 Summary:")
    println("  Files: $fileCount")
    println("  Directories: $dirCount")
    println("  Total size: ${formatSize(totalSize, tool.sizeUnit)}")
    println()

    if (filesByType.isNotEmpty()) {
        println("📋 Files by type:")
        filesByType.toList()
            .sortedByDescending { it.second.size }
            .take(10)
            .forEach { (extension, files) ->
                val size = files.sumOf { it.length() }
                println("  $extension: ${files.size} files (${formatSize(size, tool.sizeUnit)})")
            }
        println()
    }

    if (tool.verbose && fileCount > 0) {
        val largest = files.filter { it.isFile }.maxByOrNull { it.length() }
        val smallest = files.filter { it.isFile }.minByOrNull { it.length() }

        println("🔍 Details:")
        largest?.let {
            println("  Largest file: ${it.name} (${formatSize(it.length(), tool.sizeUnit)})")
        }
        smallest?.let {
            println("  Smallest file: ${it.name} (${formatSize(it.length(), tool.sizeUnit)})")
        }
    }
}

fun searchFiles(source: File, pattern: String, tool: FileTool) {
    if (!tool.quiet) {
        println("🔍 Searching for: '$pattern' in ${source.path}")
        println()
    }

    val files = if (source.isDirectory) {
        collectFiles(source, tool).filter { it.isFile }
    } else {
        listOf(source)
    }

    val regex = if (tool.force) {
        // Force flag enables regex search
        Regex(pattern)
    } else {
        // Simple text search
        Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
    }

    var matchCount = 0
    var fileCount = 0

    for (file in files) {
        try {
            val lines = file.readLines()
            val matches = mutableListOf<Pair<Int, String>>()

            lines.forEachIndexed { index, line ->
                if (regex.containsMatchIn(line)) {
                    matches.add(index + 1 to line.trim())
                }
            }

            if (matches.isNotEmpty()) {
                fileCount++
                matchCount += matches.size

                println("📄 ${file.path}:")
                matches.take(if (tool.verbose) Int.MAX_VALUE else 3).forEach { (lineNum, line) ->
                    println("  $lineNum: $line")
                }
                if (!tool.verbose && matches.size > 3) {
                    println("  ... and ${matches.size - 3} more matches")
                }
                println()
            }
        } catch (e: Exception) {
            if (tool.verbose && !tool.quiet) {
                println("⚠️  Could not search ${file.path}: ${e.message}")
            }
        }
    }

    if (!tool.quiet) {
        println("✅ Found $matchCount matches in $fileCount files")
    }
}

// Utility functions

fun collectFiles(
    root: File,
    tool: FileTool,
    currentDepth: Int = 0,
    depth: Int = currentDepth
): List<File> {
    val maxDepth = tool.maxDepth ?: Int.MAX_VALUE
    if (currentDepth >= maxDepth) return emptyList()

    val result = mutableListOf<File>()

    if (root.isFile) {
        result.add(root)
    } else if (root.isDirectory) {
        result.add(root)
        root.listFiles()?.forEach { child ->
            if (tool.includeHidden || !child.name.startsWith(".")) {
                result.addAll(collectFiles(child, tool, currentDepth + 1, depth))
            }
        }
    }

    return result
}

fun formatSize(bytes: Long, unit: SizeUnit): String {
    return when (unit) {
        SizeUnit.BYTES -> "$bytes B"
        SizeUnit.KB -> "${bytes / 1024} KB"
        SizeUnit.MB -> "${bytes / (1024 * 1024)} MB"
        SizeUnit.GB -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
```

## Usage Examples

### Basic Operations

```bash
# List current directory
./filetool list .

# List recursively with verbose output
./filetool list /home/user/documents --recursive --verbose

# Copy a file
./filetool copy source.txt backup.txt

# Copy directory recursively
./filetool copy /source/dir /backup/dir --recursive --verbose

# Delete with confirmation
./filetool delete oldfile.txt

# Force delete without confirmation
./filetool delete /tmp/cache --recursive --force
```

### Advanced Features

```bash
# Show statistics with different size units
./filetool stats /home/user --recursive --size-unit MB

# Search for text in files
./filetool search /project --pattern "TODO" --recursive

# Search with regex (using --force flag)
./filetool search . --pattern "function\s+\w+" --force --recursive

# Include hidden files
./filetool list . --recursive --include-hidden

# Limit recursion depth
./filetool stats /large/directory --recursive --max-depth 3
```

### Error Handling Examples

```bash
# Missing required destination for copy
./filetool copy source.txt
# Error: Missing required option destination

# Missing pattern for search
./filetool search /project
# Error: Missing required option --pattern

# Conflicting options
./filetool list . --quiet --verbose
# Error: At most one of --quiet, --verbose allowed

# Invalid operation
./filetool invalid-op source.txt
# Error: Invalid value 'invalid-op' for operation (expected one of: list, copy, delete, stats, search)
```

## Key Features Demonstrated

### 1. Enum Positional Arguments
```kotlin
val operation by positional("operation")
    .enum<Operation>()
    .help("Operation to perform: list, copy, delete, stats, search")
```

### 2. Conditional Requirements
```kotlin
constraints {
    "destination" requiredIf { operation == Operation.COPY }
    "pattern" requiredIf { operation == Operation.SEARCH }
}
```

### 3. Option Conflicts
```kotlin
constraints {
    atMostOne("--quiet", "--verbose")
}
```

### 4. Type-Safe Enums
```kotlin
enum class SizeUnit { BYTES, KB, MB, GB }
val sizeUnit by option("--size-unit")
    .enum<SizeUnit>()
    .default(SizeUnit.BYTES)
```

### 5. Complex Validation
```kotlin
constraints {
    "maxDepth" requiredIf { recursive }
    "maxDepth" requiredMin 1
}
```

### 6. User-Friendly Output
The application provides:
- Progress indicators with emojis
- Colored output for different file types
- Confirmation prompts for destructive operations
- Verbose and quiet modes
- Clear error messages

## Building and Running

1. **Create the project**:
```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.2.10"
    application
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("onl.ycode:argos:1.0.0")
            }
        }
    }
}

application {
    mainClass.set("FileToolKt")
}
```

2. **Build the application**:
```bash
./gradlew build
```

3. **Run with arguments**:
```bash
./gradlew run --args="list . --recursive --verbose"
```

4. **Create a native binary** (optional):
```bash
./gradlew linkReleaseExecutableLinuxX64
```

## Best Practices Shown

1. **Clear Help Text**: Every option has descriptive help text
2. **Input Validation**: Comprehensive validation with helpful error messages
3. **User Confirmation**: Asks for confirmation on destructive operations
4. **Flexible Output**: Supports quiet, normal, and verbose output modes
5. **Error Handling**: Graceful error handling with meaningful messages
6. **Type Safety**: Uses enums and type conversions for safety
7. **Modular Design**: Separate functions for each operation

This example demonstrates how Argos enables building sophisticated command-line tools with minimal boilerplate while maintaining type safety and providing excellent user experience.