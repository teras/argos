/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.completion.demo

import onl.ycode.argos.Arguments
import onl.ycode.argos.completion.Completions
import onl.ycode.argos.completion.generateCompletion
import onl.ycode.argos.parse

/**
 * Demo application showcasing shell completion generation.
 *
 * This demo creates a sample CLI with various options and domains,
 * then demonstrates how to generate completion scripts for bash, zsh, and fish.
 */
class CompletionDemo : Arguments(
    appName = "completion-demo",
    appDescription = "Demo application for Argos shell completion generation"
) {
    // Global options
    val verbose by option("-v", "--verbose")
        .help("Enable verbose output")

    val config by option("-c", "--config")
        .help("Path to configuration file")

    val output by option("-o", "--output")
        .help("Output file path")

    // Completion option
    val completion by option("--completion")
        .help("Generate shell completion script (bash, zsh, or fish)")

    // Build domain
    val buildDomain by domain("build")
        .label("Build the project")
        .help("Compile and package the project")
        .aliases("compile")

    val target by option("-t", "--target")
        .help("Build target")
        .onlyInDomains(::buildDomain)

    val optimize by option("--optimize")
        .help("Enable optimizations")
        .onlyInDomains(::buildDomain)

    // Test domain
    val testDomain by domain("test")
        .label("Run tests")
        .help("Execute the test suite")

    val coverage by option("--coverage")
        .help("Generate coverage report")
        .onlyInDomains(::testDomain)

    val parallel by option("-p", "--parallel")
        .help("Number of parallel test workers")
        .onlyInDomains(::testDomain)

    // Help option
    val help by help("-h", "--help")
}

fun main(args: Array<String>) {
    // Check if user wants to generate completions (before parsing to avoid domain requirement)
    if (args.size == 2 && args[0] == "--completion") {
        val shell = args[1]
        val demo = CompletionDemo()
        val generator = when (shell.lowercase()) {
            "bash" -> Completions.bash()
            "zsh" -> Completions.zsh()
            "fish" -> Completions.fish()
            else -> error("Unsupported shell: $shell")
        }
        val completionScript = demo.generateCompletion(generator)
        println(completionScript)
        return
    }

    val demo = CompletionDemo().parse(args) ?: return

    // Check if completion was requested through normal parsing
    val completionShell = demo.completion
    if (completionShell != null) {
        val generator = when (completionShell.lowercase()) {
            "bash" -> Completions.bash()
            "zsh" -> Completions.zsh()
            "fish" -> Completions.fish()
            else -> error("Unsupported shell: $completionShell")
        }

        val completionScript = demo.generateCompletion(generator)
        println(completionScript)
        return
    }

    // Normal application logic
    println("Completion Demo Application")
    println("===========================")
    println()

    val verboseVal = demo.verbose
    if (verboseVal != null) {
        println("Verbose mode enabled")
    }

    val configVal = demo.config
    if (configVal != null) {
        println("Config file: $configVal")
    }

    val outputVal = demo.output
    if (outputVal != null) {
        println("Output file: $outputVal")
    }

    when (demo.selectedDomain()) {
        "build" -> {
            println("\nBuilding project...")
            println("Target: ${demo.target}")
            println("Optimize: ${demo.optimize}")
        }
        "test" -> {
            println("\nRunning tests...")
            println("Coverage: ${demo.coverage}")
            val parallelVal = demo.parallel
            if (parallelVal != null) {
                println("Parallel workers: $parallelVal")
            }
        }
        else -> {
            println("\nNo domain selected. Use --help for usage information.")
            println()
            println("To generate shell completions, use:")
            println("  --completion bash   # Generate Bash completion")
            println("  --completion zsh    # Generate Zsh completion")
            println("  --completion fish   # Generate Fish completion")
            println()
            println("Installation examples:")
            println("  Bash: completion-demo --completion bash > /etc/bash_completion.d/completion-demo")
            println("  Zsh:  completion-demo --completion zsh > ~/.zsh/completions/_completion-demo")
            println("  Fish: completion-demo --completion fish > ~/.config/fish/completions/completion-demo.fish")
        }
    }
}
