/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.demo

import onl.ycode.argos.*

fun main(args: Array<String>) {
    // Test {count} template support in collection validation
    class Args : Arguments(appName = "test", useANSITerminal = false) {
        val files by option("--file").list()
            .validateCollection("Must specify at least 2 files, got {count}") { it.isEmpty() || it.size >= 2 }
        val tags by option("--tag").set()
            .validateCollection("Must have exactly 2 unique tags, got {count}") { it.size == 2 }

        val ko by option("--ko").input()
    }

    println("=== Testing {count} template in collection validation ===")

    val testArgs1 = Args()
    try {
        testArgs1.parseWithException(arrayOf("--file", "a.txt"))
        println("❌ Should have failed with too few files")
    } catch (e: ParseError) {
        println("✅ List validation with {count} works:")
        println("   \"${e.message}\"")
        println("   Contains expected: ${e.message!!.contains("Must specify at least 2 files, got 1")}")
    }

    val testArgs2 = Args()
    try {
        testArgs2.parseWithException(arrayOf("--tag", "urgent", "--tag", "important", "--tag", "critical"))
        println("❌ Should have failed with too many tags")
    } catch (e: ParseError) {
        println("✅ Set validation with {count} works:")
        println("   \"${e.message}\"")
        println("   Contains expected: ${e.message!!.contains("Must have exactly 2 unique tags, got 3")}")
    }
}