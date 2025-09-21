/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos.demo

import onl.ycode.argos.Arguments
import onl.ycode.argos.parse
import onl.ycode.argos.keyvalue
import onl.ycode.argos.list
import onl.ycode.argos.set
import onl.ycode.argos.required

// Demo application to test KeyValue set behavior
class KeyValueSetDemo : Arguments(
    appName = "keyvalue-set-demo",
    appDescription = "Demo showing KeyValue set behavior - should replace duplicate keys with latest values"
) {

    val help by help()

    // List behavior: keeps all duplicates
    val configList by option("--config-list").keyvalue().list()

    // Set behavior: should replace duplicate keys with latest values (our fix)
    val configSet by option("--config-set").keyvalue().set()

    // Required set for testing
    val settings by option("--setting").keyvalue().set().required()
}

fun main(args: Array<String>) {
    println("=== KeyValue Set Behavior Demo ===")
    println("Try these commands:")
    println("  # Test list behavior (keeps duplicates):")
    println("  --config-list host=localhost --config-list port=8080 --config-list host=example.com --setting test=value")
    println()
    println("  # Test set behavior (should replace duplicates):")
    println("  --config-set host=localhost --config-set port=8080 --config-set host=example.com --setting test=value")
    println()

    if (args.isEmpty()) {
        println("Usage: --setting key=value [--config-list key=value...] [--config-set key=value...]")
        return
    }

    val parsed = KeyValueSetDemo().parse(args) ?: return

    println("=== Results ===")
    println("Config List (preserves duplicates):")
    parsed.configList.forEach { kv ->
        println("  ${kv.key} = ${kv.value}")
    }

    println("\nConfig Set (should replace duplicates):")
    parsed.configSet.forEach { kv ->
        println("  ${kv.key} = ${kv.value}")
    }

    println("\nSettings (required set):")
    parsed.settings.forEach { kv ->
        println("  ${kv.key} = ${kv.value}")
    }

    // Show the difference
    if (parsed.configList.isNotEmpty() && parsed.configSet.isNotEmpty()) {
        val listMap = parsed.configList.associate { it.key to it.value }
        val setMap = parsed.configSet.associate { it.key to it.value }

        println("\n=== Comparison ===")
        println("List as Map (last value wins manually): $listMap")
        println("Set as Map (should auto-replace): $setMap")

        if (listMap == setMap) {
            println("✅ Both approaches give same result - KeyValue set replacement is working!")
        } else {
            println("❌ Different results - KeyValue set replacement is NOT working yet")
            println("   This means we're still getting standard set behavior (first value wins)")
        }
    }
}