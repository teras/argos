/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.ArgosI18n.resetMemory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import onl.ycode.argos.terminal.StringTerminal

class TranslationSinglePassTest {

    @BeforeTest
    fun setUp() {
        resetMemory()
    }

    @AfterTest
    fun tearDown() {
        resetMemory()
    }

    @Test
    fun `built-in option required message translates exactly once`() {
        val key = "Option {option} is required"
        val (once, twice) = registerDoubleTranslation(key, "builtin")

        class Args : Arguments() {
            val foo by option("--foo").required()
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(emptyArray(), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected an error message")
        assertTrue(message.contains("[once-builtin]"), "Expected single-pass translation, got: $message")
        assertFalse(message.contains("[twice-builtin]"), "Detected double translation in: $message")
    }

    @Test
    fun `custom validation message translates exactly once`() {
        val key = "Value {value} must be positive"
        val (once, twice) = registerDoubleTranslation(key, "validation")

        class Args : Arguments() {
            val number by option("--number").int().validate(key) { it != null && it > 0 }
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--number", "-1"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected validation failure")
        assertTrue(message.contains("[once-validation]"), "Expected translated validation message, got: $message")
        assertFalse(
            message.contains("[twice-validation]"),
            "Detected double translation in validation message: $message"
        )
    }

    @Test
    fun `help output translates developer strings exactly once`() {
        val helpKey = "Shows the current status"
        val descKey = "Application overview"
        val (helpOnce, helpTwice) = registerDoubleTranslation(helpKey, "help")
        val (descOnce, descTwice) = registerDoubleTranslation(descKey, "description")

        val terminal = StringTerminal(infoColumn = null)

        class HelpArgs(term: StringTerminal) :
            Arguments(appName = "double-check", appDescription = descKey, terminal = term) {
            val status by option("--status").help(helpKey)
            val help by help()
        }

        HelpArgs(terminal).printUsage()
        val output = terminal.toString()

        assertTrue(output.contains("[once-help]"), "Expected translated help text, got: $output")
        assertFalse(output.contains("[twice-help]"), "Detected double translation of help text: $output")
        assertTrue(output.contains("[once-description]"), "Expected translated description, got: $output")
        assertFalse(output.contains("[twice-description]"), "Detected double translation of description: $output")
    }

    @Test
    fun `error messages translate only once even with translatable option names`() {
        // Use option name that looks like a translatable key
        val key = "Unknown option {option} at position {position}"
        val (once, twice) = registerDoubleTranslation(key, "error")

        class Args : Arguments() {
            val option by option("--option").required()  // option name that could be double-translated
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--unknown"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected an error message")
        assertTrue(message.contains("[once-error]"), "Expected single-pass translation, got: $message")
        assertFalse(message.contains("[twice-error]"), "Detected double translation in: $message")
    }

    @Test
    fun `validation messages translate only once with value placeholder names`() {
        val key = "Invalid value: {value}"
        val (once, twice) = registerDoubleTranslation(key, "value-validation")

        class Args : Arguments() {
            val value by option("--value").int().validate(key) { it != null && it > 0 }
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--value", "-1"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected validation failure")
        assertTrue(message.contains("[once-value-validation]"), "Expected translated validation message, got: $message")
        assertFalse(message.contains("[twice-value-validation]"), "Detected double translation in validation: $message")
    }

    @Test
    fun `domain constraint messages translate only once`() {
        val key = "At least one of ({options}) is required"  // Match actual message
        val (once, twice) = registerDoubleTranslation(key, "constraint")

        class Args : Arguments() {
            val buildDomain by domain("build").atLeastOne(::option, ::reference)
            val option by option("--option")
            val reference by option("--reference")  // reference is a translation placeholder name
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("build"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected constraint violation")
        assertTrue(message.contains("[once-constraint]"), "Expected translated constraint message, got: $message")
        assertFalse(message.contains("[twice-constraint]"), "Detected double translation in constraint: $message")
    }

    @Test
    fun `missing value messages translate only once with option token names`() {
        val key = "Missing value for {option} at position {position}"
        val (once, twice) = registerDoubleTranslation(key, "missing")

        class Args : Arguments() {
            val optionToken by option("--option-token").required()  // name matches placeholder
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--option-token"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected missing value error")
        assertTrue(message.contains("[once-missing]"), "Expected single-pass translation, got: $message")
        assertFalse(message.contains("[twice-missing]"), "Detected double translation in: $message")
    }

    @Test
    fun `conflict messages translate only once with options placeholder`() {
        val key = "Conflicting options: {options}"
        val (once, twice) = registerDoubleTranslation(key, "conflict")

        class Args : Arguments() {
            val options by option("--options").conflictsWith(::choices)  // name matches placeholder
            val choices by option("--choices")  // choices is often used in translations
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--options", "val1", "--choices", "val2"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected conflict error")
        assertTrue(message.contains("[once-conflict]"), "Expected translated conflict message, got: $message")
        assertFalse(message.contains("[twice-conflict]"), "Detected double translation in conflict: $message")
    }

    @Test
    fun `help metadata translates only once with translatable field names`() {
        val defaultKey = "default: {value}"
        val envKey = "env: {value}"
        val requiredKey = "required"
        val choicesKey = "choices: {choices}"

        val (defaultOnce, defaultTwice) = registerDoubleTranslation(defaultKey, "default")
        val (envOnce, envTwice) = registerDoubleTranslation(envKey, "env")
        val (requiredOnce, requiredTwice) = registerDoubleTranslation(requiredKey, "required")
        val (choicesOnce, choicesTwice) = registerDoubleTranslation(choicesKey, "choices")

        val terminal = StringTerminal(infoColumn = null)

        class MetaArgs(term: StringTerminal) : Arguments(terminal = term) {
            val value by option("--value").default("default").fromEnv("VALUE")  // 'value' is a placeholder name
            val required by option("--required").required()  // 'required' is a translation key
            val choices by option("--choices").oneOf("choice1", "choice2")  // 'choices' is a placeholder name
            val help by help()
        }

        MetaArgs(terminal).printUsage()
        val output = terminal.toString()

        assertTrue(output.contains("[once-default]"), "Expected translated default metadata, got: $output")
        assertFalse(output.contains("[twice-default]"), "Detected double translation of default: $output")
        assertTrue(output.contains("[once-env]"), "Expected translated env metadata, got: $output")
        assertFalse(output.contains("[twice-env]"), "Detected double translation of env: $output")
        assertTrue(output.contains("[once-required]"), "Expected translated required metadata, got: $output")
        assertFalse(output.contains("[twice-required]"), "Detected double translation of required: $output")
        assertTrue(output.contains("[once-choices]"), "Expected translated choices metadata, got: $output")
        assertFalse(output.contains("[twice-choices]"), "Detected double translation of choices: $output")
    }

    @Test
    fun `domain error messages translate only once with domain names`() {
        val unknownDomainKey = "Unknown domain '{domain}' at position {position}. Expected: {expected}"
        val domainRequiredKey = "A domain is required. Choose one of: {domains}"

        val (unknownOnce, unknownTwice) = registerDoubleTranslation(unknownDomainKey, "unknown-domain")
        val (requiredOnce, requiredTwice) = registerDoubleTranslation(domainRequiredKey, "domain-required")

        class Args : Arguments() {
            val domain by domain("domain")  // domain name matches placeholder
            val domains by domain("domains")  // domains name matches placeholder
            val help by help()
        }

        val args1 = Args()
        var error1: String? = null
        args1.parse(arrayOf("unknown"), onError = { err, _ -> error1 = err.message })

        val message1 = assertNotNull(error1, "Expected unknown domain error")
        assertTrue(
            message1.contains("[once-unknown-domain]"),
            "Expected translated unknown domain message, got: $message1"
        )
        assertFalse(
            message1.contains("[twice-unknown-domain]"),
            "Detected double translation in unknown domain: $message1"
        )

        val args2 = Args()
        var error2: String? = null
        args2.parse(emptyArray(), onError = { err, _ -> error2 = err.message })

        val message2 = assertNotNull(error2, "Expected domain required error")
        assertTrue(
            message2.contains("[once-domain-required]"),
            "Expected translated domain required message, got: $message2"
        )
        assertFalse(
            message2.contains("[twice-domain-required]"),
            "Detected double translation in domain required: $message2"
        )
    }

    @Test
    fun `arity validation messages translate only once`() {
        val key = "Value {value} must be positive"
        val (once, twice) = registerDoubleTranslation(key, "arity-validation")

        class Args : Arguments() {
            val coords by option("--coords").int().arity(2).validate(key) { pair -> pair.all { it > 0 } }
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--coords", "1", "-1"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected arity validation failure")
        assertTrue(
            message.contains("[once-arity-validation]"),
            "Expected translated arity validation message, got: $message"
        )
        assertFalse(
            message.contains("[twice-arity-validation]"),
            "Detected double translation in arity validation: $message"
        )
    }

    @Test
    fun `constraint template messages translate only once with argument names`() {
        // NOTE: This test reveals that this message is NOT currently translatable
        // The message "Option {option} requires at least {min} occurrences (got {count})"
        // in Arguments.kt:2111 is not wrapped with translate() - this might be a bug!
        val key = "Option {option} requires at least {min} occurrences (got {count})"  // Match actual message
        val (once, twice) = registerDoubleTranslation(key, "template")

        class Args : Arguments() {
            val option by option("--option").list().atLeast(2)  // 'option' matches placeholder name
            val min by option("--min").int()  // 'min' matches placeholder name
            val count by option("--count").int()  // 'count' matches placeholder name
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--option", "once"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected constraint template error")
        // Since this message is NOT currently translated, we verify it doesn't accidentally get double-translated
        // if someone later adds translation support
        assertFalse(message.contains("[twice-template]"), "Detected double translation in template: $message")
        // We expect the raw message since it's not currently translatable
        assertTrue(
            message.contains("Option --option requires at least 2 occurrences"),
            "Expected raw constraint message, got: $message"
        )
    }

    @Test
    fun `positional argument errors translate only once`() {
        val unexpectedKey = "Unexpected positional argument '{argument}' at position {position}"
        val unexpectedArgsKey = "Unexpected positional arguments: {arguments}"

        val (unexpectedOnce, unexpectedTwice) = registerDoubleTranslation(unexpectedKey, "unexpected")
        val (argsOnce, argsTwice) = registerDoubleTranslation(unexpectedArgsKey, "arguments")

        class Args : Arguments() {
            val argument by option("--argument")  // matches placeholder name
            val arguments by option("--arguments")  // matches placeholder name
            val help by help()
        }

        val args1 = Args()
        var error1: String? = null
        args1.parse(arrayOf("unexpected"), onError = { err, _ -> error1 = err.message })

        val message1 = assertNotNull(error1, "Expected unexpected argument error")
        assertTrue(message1.contains("[once-unexpected]"), "Expected translated unexpected message, got: $message1")
        assertFalse(message1.contains("[twice-unexpected]"), "Detected double translation in unexpected: $message1")
    }

    @Test
    fun `translation keys that equal reserved names translate only once`() {
        // Test the case where a user-provided translation key exactly matches a reserved translation key
        val reservedKey = "Option {option} is required"  // This is a built-in message

        // Register a translation where the translated text looks like a reserved translation key
        val translatedToReserved = "required"  // This looks like another translation key
        val (reservedOnce, reservedTwice) = registerDoubleTranslation("required", "reserved-name")

        // Also register a direct translation for our test message
        ArgosI18n.register(reservedKey to translatedToReserved)

        class Args : Arguments() {
            val required by option("--required").required()  // name matches the translated value
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(emptyArray(), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected a required option error")
        // The message should be translated to "required" but not further translated
        assertTrue(message.contains("required"), "Expected the message to contain 'required', got: $message")
        // If it was double-translated, we'd see the [twice-reserved-name] marker
        assertFalse(message.contains("[twice-reserved-name]"), "Detected double translation of reserved name: $message")
    }

    @Test
    fun `help text with reserved translation key names translates only once`() {
        // Create a help text that looks like a translation key
        val helpTextKey = "default: {value}"  // This looks like a reserved metadata key
        val appDescKey = "required"  // This equals a reserved translation key

        val (helpOnce, helpTwice) = registerDoubleTranslation(helpTextKey, "help-meta")
        val (descOnce, descTwice) = registerDoubleTranslation(appDescKey, "app-desc")

        val terminal = StringTerminal(infoColumn = null)

        class ReservedArgs(term: StringTerminal) : Arguments(appDescription = appDescKey, terminal = term) {
            val metaOption by option("--meta").help(helpTextKey)
            val default by option("--default")  // 'default' is used in metadata
            val value by option("--value")  // 'value' is used in metadata
            val help by help()
        }

        ReservedArgs(terminal).printUsage()
        val output = terminal.toString()

        assertTrue(output.contains("[once-help-meta]"), "Expected single translation of help metadata, got: $output")
        assertFalse(output.contains("[twice-help-meta]"), "Detected double translation of help metadata: $output")
        assertTrue(output.contains("[once-app-desc]"), "Expected single translation of app description, got: $output")
        assertFalse(output.contains("[twice-app-desc]"), "Detected double translation of app description: $output")
    }

    @Test
    fun `option names matching message template placeholders translate only once`() {
        // Test option names that exactly match common message template placeholders
        val errorKey = "Invalid value for {option}: {value}"
        val (once, twice) = registerDoubleTranslation(errorKey, "placeholder-match")

        class Args : Arguments() {
            val option by option("--option").int().validate(errorKey) { it != null && it > 0 }
            val value by option("--value")  // These names match the placeholders in the error message
            val help by help()
        }

        val args = Args()
        var error: String? = null
        args.parse(arrayOf("--option", "-1"), onError = { err, _ -> error = err.message })

        val message = assertNotNull(error, "Expected validation error")
        assertTrue(
            message.contains("[once-placeholder-match]"),
            "Expected single translation with placeholder names, got: $message"
        )
        assertFalse(
            message.contains("[twice-placeholder-match]"),
            "Detected double translation with placeholder names: $message"
        )
    }

    private fun registerDoubleTranslation(key: String, label: String): Pair<String, String> {
        val once = "[once-$label] $key"
        val twice = "[twice-$label] $key"
        ArgosI18n.register(
            mapOf(
                key to once,
                once to twice
            )
        )
        return once to twice
    }
}
