/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KeyValueTest {

    // Test Arguments classes for different scenarios
    class ScalarKeyValueArgs : Arguments() {
        val config by option("--config").keyvalue()         // KeyValue?
        val database by option("--db").keyvalue().required() // KeyValue
        val endpoint by option("--endpoint").keyvalue().default(KeyValue("host", "localhost")) // KeyValue
    }

    class ListKeyValueArgs : Arguments() {
        val properties by option("--prop").keyvalue().list()          // List<KeyValue>
        val settings by option("--setting").keyvalue().list().atLeast(1) // List<KeyValue>
    }

    class SetKeyValueArgs : Arguments() {
        val configs by option("--config").keyvalue().set()            // Set<KeyValue>
        val parameters by option("--param").keyvalue().set().atLeast(1) // Set<KeyValue>
    }

    class CustomSeparatorArgs : Arguments() {
        val colonSeparated by option("--colon").keyvalue(":")         // KeyValue?
        val arrowSeparated by option("--arrow").keyvalue("->")        // KeyValue?
        val pipeSeparated by option("--pipe").keyvalue("|")           // KeyValue?
        val colonList by option("--clist").keyvalue(":").list()       // List<KeyValue>
        val arrowSet by option("--aset").keyvalue("->").set()         // Set<KeyValue>
    }

    class ValidationKeyValueArgs : Arguments() {
        val validated by option("--validated").keyvalue()
            .validate("Key must start with 'app'") { it?.key?.startsWith("app") ?: true }

        val validatedList by option("--vlist").keyvalue().list()
            .validate("Each key must be alphanumeric") { it.key.all { c -> c.isLetterOrDigit() } }
            .validateCollection("Must have 1-3 entries") { it.size in 1..3 }
    }

    class MapConversionArgs : Arguments() {
        val properties by option("--prop").keyvalue().list()     // List<KeyValue>
        val settings by option("--setting").keyvalue().set().required() // Set<KeyValue>
    }

    @Test
    fun keyValue_equality_onlyConsidersKey() {
        val kv1 = KeyValue("host", "localhost")
        val kv2 = KeyValue("host", "example.com")
        val kv3 = KeyValue("port", "8080")

        assertEquals(kv1, kv2)  // Same key, different value
        assertFalse(kv1 == kv3) // Different key
        assertEquals(kv1.hashCode(), kv2.hashCode()) // Same hash for same key
    }

    @Test
    fun keyValue_toString_usesEqualsSeparator() {
        val kv = KeyValue("host", "localhost")
        assertEquals("host=localhost", kv.toString())
    }

    @Test
    fun scalarKeyValue_nullable_basicFunctionality() {
        val args = ScalarKeyValueArgs()

        // Test not provided (null)
        args.parseWithException(arrayOf("--db", "url=postgresql://localhost"))
        assertNull(args.config)

        // Test provided
        args.parseWithException(arrayOf("--config", "debug=true", "--db", "url=postgresql://localhost"))
        assertEquals("debug", args.config!!.key)
        assertEquals("true", args.config!!.value)
    }

    @Test
    fun scalarKeyValue_required_basicFunctionality() {
        val args = ScalarKeyValueArgs()

        // Test required option missing
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }

        // Test required option provided
        args.parseWithException(arrayOf("--db", "url=postgresql://localhost"))
        assertEquals("url", args.database.key)
        assertEquals("postgresql://localhost", args.database.value)
    }

    @Test
    fun scalarKeyValue_default_basicFunctionality() {
        val args = ScalarKeyValueArgs()

        // Test default value used
        args.parseWithException(arrayOf("--db", "url=test"))
        assertEquals("host", args.endpoint.key)
        assertEquals("localhost", args.endpoint.value)

        // Test override default
        args.parseWithException(arrayOf("--db", "url=test", "--endpoint", "service=api.example.com"))
        assertEquals("service", args.endpoint.key)
        assertEquals("api.example.com", args.endpoint.value)
    }

    @Test
    fun scalarKeyValue_malformedInput_failsValidation() {
        val args = ScalarKeyValueArgs()

        // Test malformed input (no separator) - should fail validation
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--config", "noseparator", "--db", "url=test"))
        }
    }

    @Test
    fun scalarKeyValue_malformedInput_requiredFailsValidation() {
        val args = ScalarKeyValueArgs()

        // Test malformed required input fails
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--db", "noseparator"))
        }
    }

    @Test
    fun listKeyValue_basicFunctionality() {
        val args = ListKeyValueArgs()

        // Test empty list (but settings is required)
        args.parseWithException(arrayOf("--setting", "cache=true"))
        assertTrue(args.properties.isEmpty())

        // Test single item
        args.parseWithException(arrayOf("--prop", "host=localhost", "--setting", "cache=true"))
        assertEquals(1, args.properties.size)
        assertEquals("host", args.properties[0].key)
        assertEquals("localhost", args.properties[0].value)

        // Test multiple items (preserves order and duplicates)
        args.parseWithException(arrayOf(
            "--prop", "host=localhost",
            "--prop", "port=5432",
            "--prop", "host=example.com",  // Duplicate key, different value
            "--setting", "cache=true"
        ))
        assertEquals(3, args.properties.size)
        assertEquals("host", args.properties[0].key)
        assertEquals("localhost", args.properties[0].value)
        assertEquals("port", args.properties[1].key)
        assertEquals("5432", args.properties[1].value)
        assertEquals("host", args.properties[2].key)
        assertEquals("example.com", args.properties[2].value)
    }

    @Test
    fun listKeyValue_atLeast_constraint() {
        val args = ListKeyValueArgs()

        // Test atLeast constraint failure
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())  // settings requires atLeast(1)
        }

        // Test atLeast constraint success
        args.parseWithException(arrayOf("--setting", "cache=true"))
        assertEquals(1, args.settings.size)
        assertEquals("cache", args.settings[0].key)
        assertEquals("true", args.settings[0].value)
    }

    @Test
    fun setKeyValue_mapLikeBehavior() {
        val args = SetKeyValueArgs()

        // Test basic set functionality
        args.parseWithException(arrayOf("--param", "host=localhost"))
        assertEquals(1, args.parameters.size)
        val param = args.parameters.first()
        assertEquals("host", param.key)
        assertEquals("localhost", param.value)

        // Test set behavior with key-only equality (last value wins for duplicates)
        args.parseWithException(arrayOf(
            "--config", "host=localhost",
            "--config", "port=5432",
            "--config", "host=example.com",  // Duplicate key, replaces previous value
            "--config", "debug=true",
            "--param", "test=value"
        ))

        assertEquals(3, args.configs.size) // host, port, debug (duplicate host replaced)
        val hostConfig = args.configs.find { it.key == "host" }
        assertEquals("example.com", hostConfig!!.value) // Last value wins (KeyValueSet behavior)

        val portConfig = args.configs.find { it.key == "port" }
        assertEquals("5432", portConfig!!.value)

        val debugConfig = args.configs.find { it.key == "debug" }
        assertEquals("true", debugConfig!!.value)
    }

    @Test
    fun setKeyValue_atLeast_constraint() {
        val args = SetKeyValueArgs()

        // Test atLeast constraint failure
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--config", "test=value"))  // parameters requires atLeast(1)
        }

        // Test atLeast constraint success
        args.parseWithException(arrayOf("--config", "test=value", "--param", "cache=true"))
        assertEquals(1, args.parameters.size)
    }

    @Test
    fun customSeparators_basicFunctionality() {
        val args = CustomSeparatorArgs()

        // Test colon separator
        args.parseWithException(arrayOf("--colon", "database:postgresql"))
        assertEquals("database", args.colonSeparated!!.key)
        assertEquals("postgresql", args.colonSeparated!!.value)

        // Test arrow separator
        args.parseWithException(arrayOf("--arrow", "input->output.txt"))
        assertEquals("input", args.arrowSeparated!!.key)
        assertEquals("output.txt", args.arrowSeparated!!.value)

        // Test pipe separator
        args.parseWithException(arrayOf("--pipe", "name|value"))
        assertEquals("name", args.pipeSeparated!!.key)
        assertEquals("value", args.pipeSeparated!!.value)
    }

    @Test
    fun customSeparators_withCollections() {
        val args = CustomSeparatorArgs()

        // Test colon separator with list
        args.parseWithException(arrayOf(
            "--clist", "host:localhost",
            "--clist", "port:5432"
        ))
        assertEquals(2, args.colonList.size)
        assertEquals("host", args.colonList[0].key)
        assertEquals("localhost", args.colonList[0].value)
        assertEquals("port", args.colonList[1].key)
        assertEquals("5432", args.colonList[1].value)

        // Test arrow separator with set (key-only equality, last value wins)
        args.parseWithException(arrayOf(
            "--aset", "cache->true",
            "--aset", "debug->false",
            "--aset", "cache->false"  // Duplicate key, replaces previous value
        ))
        assertEquals(2, args.arrowSet.size) // cache, debug
        val cacheEntry = args.arrowSet.find { it.key == "cache" }
        assertEquals("false", cacheEntry!!.value) // Last value wins (KeyValueSet behavior)
    }

    @Test
    fun customSeparators_malformedInput() {
        val args = CustomSeparatorArgs()

        // Test wrong separator (using = when expecting :) - should fail validation
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--colon", "database=postgresql"))
        }

        // Test no separator - should fail validation
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--arrow", "noseparator"))
        }
    }

    @Test
    fun validation_singleValue() {
        val args = ValidationKeyValueArgs()

        // Test validation success
        args.parseWithException(arrayOf("--validated", "appconfig=true", "--vlist", "test=value"))
        assertEquals("appconfig", args.validated!!.key)
        assertEquals("true", args.validated!!.value)

        // Test validation failure
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--validated", "wrongkey=value", "--vlist", "test=value"))
        }
    }

    @Test
    fun validation_listWithElementAndCollectionValidation() {
        val args = ValidationKeyValueArgs()

        // Test element validation success
        args.parseWithException(arrayOf("--vlist", "host=localhost", "--vlist", "port=5432"))
        assertEquals(2, args.validatedList.size)

        // Test element validation failure
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--vlist", "host-name=localhost")) // hyphen not alphanumeric
        }

        // Test collection validation failure (too many items)
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf(
                "--vlist", "host=localhost",
                "--vlist", "port=5432",
                "--vlist", "ssl=true",
                "--vlist", "cache=false"  // 4 items, max is 3
            ))
        }

        // Test collection validation success (within range)
        args.parseWithException(arrayOf(
            "--vlist", "host=localhost",
            "--vlist", "port=5432",
            "--vlist", "ssl=true"  // 3 items, within range
        ))
        assertEquals(3, args.validatedList.size)
    }

    @Test
    fun mapConversion_fromList() {
        val args = MapConversionArgs()

        // Test conversion to map
        args.parseWithException(arrayOf(
            "--prop", "host=localhost",
            "--prop", "port=5432",
            "--prop", "host=example.com",  // Duplicate key
            "--setting", "cache=true"
        ))

        // List preserves all entries, manual conversion takes last occurrence
        val configMap = args.properties.associate { it.key to it.value }
        assertEquals(2, configMap.size)
        assertEquals("example.com", configMap["host"]) // Last value wins in map
        assertEquals("5432", configMap["port"])
    }

    @Test
    fun mapConversion_fromSet() {
        val args = MapConversionArgs()

        // Test conversion from set to map
        args.parseWithException(arrayOf(
            "--setting", "host=localhost",
            "--setting", "port=5432",
            "--setting", "host=example.com",  // Replaces previous value in set
            "--prop", "dummy=value"
        ))

        // Set with key-only equality (last value wins), easy conversion to map
        val settingsMap = args.settings.associate { it.key to it.value }
        assertEquals(2, settingsMap.size)
        assertEquals("example.com", settingsMap["host"]) // Last value kept by set
        assertEquals("5432", settingsMap["port"])
    }

    @Test
    fun edgeCases_emptyKeyOrValue() {
        class EdgeCaseArgs : Arguments() {
            val test by option("--test").keyvalue()
        }

        val args = EdgeCaseArgs()

        // Test empty key
        args.parseWithException(arrayOf("--test", "=value"))
        assertEquals("", args.test!!.key)
        assertEquals("value", args.test!!.value)

        // Test empty value
        args.parseWithException(arrayOf("--test", "key="))
        assertEquals("key", args.test!!.key)
        assertEquals("", args.test!!.value)

        // Test both empty
        args.parseWithException(arrayOf("--test", "="))
        assertEquals("", args.test!!.key)
        assertEquals("", args.test!!.value)
    }

    @Test
    fun edgeCases_multipleSeparatorsInValue() {
        class EdgeCaseArgs : Arguments() {
            val test by option("--test").keyvalue()
            val colon by option("--colon").keyvalue(":")
        }

        val args = EdgeCaseArgs()

        // Test multiple separators in value (split with limit=2)
        args.parseWithException(arrayOf("--test", "url=http://user:pass@host:port/path"))
        assertEquals("url", args.test!!.key)
        assertEquals("http://user:pass@host:port/path", args.test!!.value) // Value contains separators

        // Test with custom separator
        args.parseWithException(arrayOf("--colon", "path:/home/user:/usr/bin:/usr/local/bin"))
        assertEquals("path", args.colon!!.key)
        assertEquals("/home/user:/usr/bin:/usr/local/bin", args.colon!!.value)
    }

    @Test
    fun edgeCases_specialCharactersInKeyValue() {
        class EdgeCaseArgs : Arguments() {
            val test by option("--test").keyvalue()
        }

        val args = EdgeCaseArgs()

        // Test special characters
        args.parseWithException(arrayOf("--test", "database.host.name=my-server.example.com"))
        assertEquals("database.host.name", args.test!!.key)
        assertEquals("my-server.example.com", args.test!!.value)

        // Test spaces (would need quoting in shell)
        args.parseWithException(arrayOf("--test", "display name=My Application"))
        assertEquals("display name", args.test!!.key)
        assertEquals("My Application", args.test!!.value)
    }
}