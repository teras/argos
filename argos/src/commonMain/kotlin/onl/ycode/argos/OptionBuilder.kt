/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import onl.ycode.argos.terminal.ContentStyle
import kotlin.jvm.JvmName
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Builder for creating command-line options with various configurations and constraints.
 *
 * This class provides a fluent API for defining options, including type refinements,
 * constraints, environment variable support, and help text.
 */
class OptionBuilder<T> internal constructor(
    internal val switches: List<String>,
    internal val repeatable: Boolean,
    internal val helpText: String?,
    internal val domainProps: Set<String>?,
    internal val requiresValue: Boolean,
    internal val single: (Raw) -> SingleResult<T>,
    internal val booleanFlag: Boolean,
    internal val booleanNegatable: Boolean,
    internal val negationPrefix: String?,
    internal val positionalKind: PositionalKind?,
    internal val positionalSeq: Int?,
    internal val envVar: String?,
    internal val expectedDesc: String?,
    internal val eager: Boolean = false,
    internal val hidden: Boolean = false,
    internal val isNonNullable: Boolean = false,
    internal val defaultValue: String? = null,
    internal val callback: ((T) -> Unit)? = null
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, T>> {

    internal val constraintHooks = mutableListOf<(Arguments, owner: String) -> Unit>()
    internal val validators = mutableListOf<Pair<String, (T) -> Boolean>>()

    /**
     * Provides the property delegate implementation for command-line options.
     * This method is called automatically by Kotlin's property delegation system.
     * It registers the option with the Arguments instance and returns a delegate
     * that provides access to the parsed value.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property this delegate is assigned to
     * @return A ReadOnlyProperty that provides access to the parsed value
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, T> {
        val name = property.name
        val seq = positionalSeq ?: if (positionalKind != null) thisRef.nextPosSeq() else null

        val regSwitches =
            if (switches.isEmpty() && positionalKind == null)
                listOf(thisRef.defaultLongPrefix() + name.toKebab())
            else switches

        // Resolve the actual negation prefix - use custom prefix or default
        val actualNegationPrefix = negationPrefix ?: if (booleanNegatable) thisRef.getDefaultNegationPrefix() else null

        // Initialize value source based on whether this option has a default
        thisRef.valueSources[name] = if (isNonNullable) ValueSource.DEFAULT else ValueSource.MISSING

        thisRef.registerOption(
            ownerName = name,
            switches = regSwitches,
            repeatable = repeatable,
            help = helpText,
            domainProps = domainProps,
            requiresValue = requiresValue,
            valueKind = ValueKind.SINGLE,
            booleanFlag = booleanFlag,
            booleanNegatable = booleanNegatable,
            negationPrefix = actualNegationPrefix,
            positionalKind = positionalKind,
            positionalSeq = seq,
            envVar = envVar,
            expectedDesc = expectedDesc,
            eager = eager,
            hidden = hidden,
            defaultValue = defaultValue,
            parseMany = { raws, labelFrags, expected ->
            val r = raws.lastOrNull() ?: Raw(false, null, null)
            when (val res = single(r)) {
                is SingleResult.Missing -> ParseOutcome.Success(null)
                is SingleResult.Value<*> -> {
                    // Update value source from SingleResult
                    thisRef.valueSources[name] = res.source

                    ParseOutcome.Success(res.value)
                }
                is SingleResult.Invalid -> {
                    val more = expected?.let { " (expected $it)" } ?: ""
                    ParseOutcome.Error(
                        listOf(
                            TextFragment(ContentStyle.PLAIN, "Invalid value '"),
                            TextFragment(ContentStyle.PLAIN, res.original ?: r.value ?: "null"),
                            TextFragment(ContentStyle.PLAIN, "' for ")
                        ) + labelFrags + listOf(
                            TextFragment(ContentStyle.PLAIN, pos(r.argvIndex) + more)
                        )
                    )
                }
            }
        },
            single = single,
            validators = validators.map { (msg, pred) ->
                msg to { value: Any? -> @Suppress("UNCHECKED_CAST") pred(value as T) }
            }
        )

        constraintHooks.forEach { it(thisRef, name) }

        // Register callback if present
        callback?.let { cb ->
            thisRef.registerCallback(name) { value ->
                @Suppress("UNCHECKED_CAST")
                cb(value as T)
            }
        }

        return ReadOnlyProperty { _, property ->
            try {
                val value = thisRef.valueOf(name)
                if (value == null && isNonNullable) {
                    throw UninitializedPropertyException(property.name)
                }
                @Suppress("UNCHECKED_CAST")
                value as T
            } catch (_: Exception) {
                throw UninitializedPropertyException(property.name)
            }
        }
    }

    // ---- Fluent constraint API (GLOBAL scope) ----

    /**
     * Requires that this option be present along with any of the specified properties.
     */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ANY_PRESENT, refs) }

    /**
     * Requires that this option be present when all of the specified properties are present.
     */
    fun requireIfAllPresent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ALL_PRESENT, refs) }

    /**
     * Requires that this option be present when any of the specified properties are absent.
     */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ANY_ABSENT, refs) }

    /**
     * Requires that this option be present when all of the specified properties are absent.
     */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ALL_ABSENT, refs) }

    /**
     * Requires that this option be present when the specified property matches the given predicate.
     */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionBuilder<T> = apply {
        addPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Allows this option only when the specified property matches the given predicate.
     * If this option is provided but the condition is false, an error is reported.
     */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionBuilder<T> = apply {
        addAllowOnlyIfPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Ensures exactly one of this option and the specified properties is provided.
     */
    fun exactlyOneWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addGroupConstraint(constraintHooks, "exactly-one", refs) }

    /**
     * Ensures at most one of this option and the specified properties is provided.
     */
    fun atMostOneWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addGroupConstraint(constraintHooks, "at-most-one", refs) }

    /**
     * Ensures at least one of this option and the specified properties is provided.
     */
    fun atLeastOneWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addGroupConstraint(constraintHooks, "at-least-one", refs) }

    /**
     * Specifies that this option conflicts with the specified properties.
     */
    fun conflictsWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConflictsConstraint(constraintHooks, refs) }

    // ---- Validation methods ----

    /**
     * Adds a validator that checks the parsed value against the given predicate.
     * The validator only runs if the value is not null.
     *
     * @param message Template message for validation errors. Supports:
     *   - {name}: property name
     *   - {value}: the invalid value (properly formatted)
     *   - {switches}: option switches (e.g. "--port|-p")
     * @param predicate Function that returns true if the value is valid
     */
    fun validate(
        message: String = "Invalid value for {name}: {value}",
        predicate: (T) -> Boolean
    ): OptionBuilder<T> = apply {
        validators.add(message to predicate)
    }

    /**
     * Adds multiple validators at once.
     *
     * @param validations Pairs of (message template, predicate) for validation
     */
    fun validate(vararg validations: Pair<String, (T) -> Boolean>): OptionBuilder<T> = apply {
        validators.addAll(validations)
    }

    /**
     * Registers a callback to be executed when this option's value is found during parsing.
     * The callback receives the converted value and is executed after all parsing and validation completes.
     * Callbacks are executed in the order their values appear on the command line.
     */
    fun onValue(callback: (T) -> Unit): OptionBuilder<T> {
        return OptionBuilder(
            switches,
            repeatable,
            helpText,
            domainProps,
            requiresValue,
            single,
            booleanFlag,
            booleanNegatable,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar,
            expectedDesc,
            eager,
            hidden,
            isNonNullable,
            defaultValue,
            callback
        ).also { newBuilder ->
            // Copy constraint hooks and validators
            constraintHooks.forEach { hook ->
                newBuilder.constraintHooks.add(hook)
            }
            validators.forEach { validator ->
                newBuilder.validators.add(validator)
            }
        }
    }

    // ---- Type refinement and meta methods ----

    /**
     * Maps the parsed value using the specified transformation function.
     * For non-nullable builders (from .default() or .required()), preserves non-nullable type when transformation returns non-null.
     * For nullable builders, always returns nullable type.
     */
    fun <R> map(desc: String? = expectedDesc, f: (T) -> R?): OptionBuilder<R?> =
        OptionBuilder(
            switches, repeatable, helpText, domainProps, requiresValue,
            single = { raw ->
                when (val r = single(raw)) {
                    is SingleResult.Missing -> SingleResult.Missing
                    is SingleResult.Invalid -> r
                    is SingleResult.Value -> {
                        val inVal = r.value
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val out = f(inVal)
                            if (inVal != null && out == null) SingleResult.Invalid(raw.value)
                            else SingleResult.Value(out, r.source)
                        } catch (_: Exception) {
                            SingleResult.Invalid(raw.value)
                        }
                    }
                }
            },
            booleanFlag = booleanFlag,
            booleanNegatable = booleanNegatable,
            negationPrefix = negationPrefix,
            positionalKind = positionalKind,
            positionalSeq = positionalSeq,
            envVar = envVar,
            expectedDesc = desc,
            eager = eager,
            hidden = hidden,
            isNonNullable = false, // Always nullable for general map
            defaultValue = defaultValue,
            callback = null // Callbacks lost during type transformation - call onValue() AFTER transformation
        ).also { newBuilder ->
            // Copy constraint hooks
            constraintHooks.forEach { hook ->
                newBuilder.constraintHooks.add(hook)
            }
        }


    /**
     * Sets help text for this option.
     */
    fun help(text: String): OptionBuilder<T> =
        OptionBuilder(
            switches, repeatable, text, domainProps, requiresValue, single,
            booleanFlag, booleanNegatable, negationPrefix, positionalKind, positionalSeq, envVar, expectedDesc, eager, hidden, isNonNullable, defaultValue
        )

    /**
     * Marks this option as hidden from help output.
     */
    fun hidden(): OptionBuilder<T> =
        OptionBuilder(
            switches, repeatable, helpText, domainProps, requiresValue, single,
            booleanFlag, booleanNegatable, negationPrefix, positionalKind, positionalSeq, envVar, expectedDesc, eager, true, isNonNullable, defaultValue
        )

    /**
     * Restricts this option to be available only in the specified domains.
     */
    fun onlyInDomains(vararg refs: KProperty<*>): OptionBuilder<T> {
        if (refs.isEmpty()) throw ConfigError("onlyInDomains requires at least 1 domain (got ${refs.size})")
        return OptionBuilder(
            switches,
            repeatable,
            helpText,
            refs.map { it.name }.toSet(),
            requiresValue,
            single,
            booleanFlag,
            booleanNegatable,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar,
            expectedDesc,
            eager,
            hidden
        )
    }

    /**
     * Configures this option to read its value from the specified environment variable.
     */
    fun fromEnv(name: String): OptionBuilder<T> =
        OptionBuilder(
            switches,
            repeatable,
            helpText,
            domainProps,
            requiresValue,
            single,
            booleanFlag,
            booleanNegatable,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar = name,
            expectedDesc = expectedDesc,
            eager = eager,
            hidden = hidden
        )
}

// ---- Extension functions for constraint functionality ----

/**
 * Marks this option as required.
 */
fun <T> OptionBuilder<T>.required(): OptionBuilder<T> = apply {
    addRequiredConstraint(constraintHooks, 1)
}


// ---- Helper methods for reducing duplication ----

/**
 * Creates a new OptionBuilder with the same properties as this one but prepared for NonNullableOptionBuilder wrapping.
 * This method reduces duplication in transformation methods.
 */
private fun <T : Any> OptionBuilder<T?>.createBuilderForNonNullableWrapping(
    isNonNullable: Boolean = true,
    defaultValue: String? = this.defaultValue
): OptionBuilder<T?> {
    return OptionBuilder(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = requiresValue,
        single = { raw ->
            when (val result = single(raw)) {
                is SingleResult.Missing -> SingleResult.Missing // Let constraint system handle this
                is SingleResult.Value -> {
                    // For required options, constraint validation ensures value is present
                    SingleResult.Value(result.value, result.source)
                }
                is SingleResult.Invalid -> result
            }
        },
        booleanFlag = booleanFlag,
        booleanNegatable = booleanNegatable,
        negationPrefix = negationPrefix,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = expectedDesc,
        eager = eager,
        hidden = hidden,
        isNonNullable = isNonNullable,
        defaultValue = defaultValue
    ).also { builder ->
        constraintHooks.forEach { hook ->
            builder.constraintHooks.add(hook)
        }
    }
}

/**
 * Creates a new OptionBuilder with the same properties as this one but with specified overrides.
 * This method reduces duplication in transformation methods for same-type builders.
 */
private fun <T> OptionBuilder<T>.createModifiedBuilder(
    booleanNegatable: Boolean = this.booleanNegatable,
    negationPrefix: String? = this.negationPrefix,
    single: ((Raw) -> SingleResult<T>)? = null
): OptionBuilder<T> {
    return OptionBuilder(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = requiresValue,
        single = single ?: this.single,
        booleanFlag = booleanFlag,
        booleanNegatable = booleanNegatable,
        negationPrefix = negationPrefix,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = expectedDesc,
        eager = eager,
        hidden = hidden,
        isNonNullable = isNonNullable,
        defaultValue = defaultValue
    ).also { builder ->
        constraintHooks.forEach { hook ->
            builder.constraintHooks.add(hook)
        }
    }
}

/**
 * Helper method for creating negatable boolean options.
 * Reduces duplication between nullable and non-nullable negatable methods.
 */
private fun <T> OptionBuilder<T>.createNegatableBuilder(prefix: String?): OptionBuilder<T> {
    if (!booleanFlag) {
        throw ConfigError("negatable() can only be used on boolean options created with .bool()")
    }
    if (prefix != null && prefix.trim().isEmpty()) {
        throw ConfigError("negatable prefix cannot be empty or whitespace-only")
    }

    return createModifiedBuilder(
        booleanNegatable = true,
        negationPrefix = prefix
    )
}

// ---- Nullable to non-nullable conversion functions ----

/**
 * Marks this nullable option as required, converting it to a non-nullable type.
 */
@JvmName("requiredGeneric")
fun <T : Any> OptionBuilder<T?>.required(): NonNullableOptionBuilder<T> {
    val newBuilder = createBuilderForNonNullableWrapping().also { builder ->
        addRequiredConstraint(builder.constraintHooks, 1)
    }
    return NonNullableOptionBuilder(newBuilder)
}


// ---- Type refinement extension functions ----


/**
 * Converts string option to integer option.
 */
fun OptionBuilder<String?>.int() = map(desc = "an integer") { it?.toIntOrNull() }

/**
 * Converts string option to long option.
 */
fun OptionBuilder<String?>.long() = map(desc = "a long") { it?.toLongOrNull() }

/**
 * Converts string option to float option.
 */
fun OptionBuilder<String?>.float() = map(desc = "a decimal number") { it?.toFloatOrNull() }

/**
 * Converts string option to double option.
 */
fun OptionBuilder<String?>.double() = map(desc = "a decimal number") { it?.toDoubleOrNull() }

/**
 * Converts string option to key-value pair option.
 * Expects input in format "key<separator>value" and returns a KeyValue object.
 *
 * @param separator The character used to separate key and value (default: "=")
 */
fun OptionBuilder<String?>.keyvalue(separator: String = "=") = map(desc = "key${separator}value pair") { str ->
    str?.let { s ->
        val parts = s.split(separator, limit = 2)
        if (parts.size == 2) {
            KeyValue(parts[0], parts[1])
        } else null
    }
}

/**
 * Converts string option to boolean option.
 */
fun OptionBuilder<String?>.bool(): OptionBuilder<Boolean?> =
    OptionBuilder(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = false, // Boolean options default to optional values for backward compatibility
        single = { raw ->
            if (!raw.present) SingleResult.Missing else {
                val lit = parseBooleanLiteral(raw.value)
                when {
                    lit == null -> SingleResult.Value(true)
                    else -> SingleResult.Value(lit)
                }
            }
        },
        booleanFlag = true,
        booleanNegatable = false,
        negationPrefix = null,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = "boolean",
        eager = eager,
        hidden = hidden,
        isNonNullable = isNonNullable,
        defaultValue = defaultValue,
        callback = null // Callbacks lost during type transformation - call onValue() AFTER .bool()
    )

/**
 * Makes boolean option eager (evaluated immediately, causing early exit).
 */
fun OptionBuilder<Boolean?>.eager(): OptionBuilder<Boolean?> {
    if (!this.booleanFlag) throw ConfigError("eager() can only be applied to boolean options")
    return OptionBuilder(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = false,
        single = single,
        booleanFlag = true,
        booleanNegatable = booleanNegatable,
        negationPrefix = negationPrefix,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = expectedDesc,
        eager = true,
        hidden = hidden,
        isNonNullable = isNonNullable,
        defaultValue = defaultValue
    )
}

/**
 * Converts string option to enum option.
 */
inline fun <reified E : Enum<E>> OptionBuilder<String?>.enum(
    aliases: Map<String, E> = emptyMap(),
    ignoreCase: Boolean = true
): OptionBuilder<E?> {
    val allowed = buildString {
        val vals = enumValues<E>().map { it.name }
        append("one of: "); append(vals.joinToString(", "))
    }
    return map(desc = allowed) { s ->
        s?.let { v ->
            aliases.entries.firstOrNull { it.key.equals(v, ignoreCase) }?.value
                ?: enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase) }
        }
    }
}

/**
 * Restricts string option to one of the specified allowed values.
 */
fun OptionBuilder<String?>.oneOf(vararg allowed: String, ignoreCase: Boolean = true): OptionBuilder<String?> {
    val desc = "one of: " + allowed.joinToString(", ")
    return map(desc = desc) { s -> s?.let { v -> allowed.firstOrNull { it.equals(v, ignoreCase) } } }
}

/**
 * Sets a default value for nullable option, making it non-nullable.
 */
fun <T : Any> OptionBuilder<T?>.default(value: T): NonNullableOptionBuilder<T> {
    val base = this.single
    val newBuilder: OptionBuilder<T?> = OptionBuilder(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = requiresValue,
        single = { raw ->
            when (val r = base(raw)) {
                is SingleResult.Missing -> SingleResult.Value(value as T?, ValueSource.DEFAULT)
                is SingleResult.Invalid -> r
                is SingleResult.Value -> SingleResult.Value((r.value ?: value) as T?, if (r.value != null) r.source else ValueSource.DEFAULT)
            }
        },
        booleanFlag = booleanFlag,
        booleanNegatable = booleanNegatable,
        negationPrefix = negationPrefix,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = expectedDesc,
        eager = eager,
        hidden = hidden,
        isNonNullable = true,
        defaultValue = value.toString()
    ).also { builder ->
        constraintHooks.forEach { hook ->
            builder.constraintHooks.add(hook)
        }
    }
    return NonNullableOptionBuilder(newBuilder)
}

/**
 * Converts option to list collector.
 */
fun <T : Any> OptionBuilder<T?>.list() =
    OptionListBuilder(
        switches, helpText, domainProps, requiresValue,
        single = { raw -> this.single(raw) }, booleanFlag = false, negationPrefix = null,
        positionalKind = if (positionalKind != null) PositionalKind.LIST else null,
        positionalSeq = positionalSeq, expectedDesc = expectedDesc, hidden = hidden,
        isNonNullable = false,
        callback = callback
    )

/**
 * Converts option to set collector.
 */
fun <T : Any> OptionBuilder<T?>.set() =
    OptionSetBuilder(
        switches, helpText, domainProps, requiresValue,
        single = { raw -> this.single(raw) }, booleanFlag = false, negationPrefix = null,
        positionalKind = if (positionalKind != null) PositionalKind.SET else null,
        positionalSeq = positionalSeq, expectedDesc = expectedDesc, hidden = hidden
    )

/**
 * Converts boolean option to count collector.
 */
fun OptionBuilder<Boolean?>.count() = CountBuilder(switches, helpText, domainProps, requiresValue, hidden = hidden)

/**
 * Controls whether this option requires a value.
 *
 * When requires=true (default): Option always expects a value (--option value)
 * When requires=false: Option can work without a value, but will consume one if available
 *                      using smart lookahead parsing to distinguish between option values
 *                      and subsequent options/arguments.
 */
fun <T> OptionBuilder<T>.requiresValue(requires: Boolean): OptionBuilder<T> =
    if (requires == requiresValue) {
        // If the setting is already correct, don't create a new builder
        this
    } else {
        OptionBuilder(
            switches = switches,
            repeatable = repeatable,
            helpText = helpText,
            domainProps = domainProps,
            requiresValue = requires,  // Set to the requested value
            single = single,
            booleanFlag = booleanFlag,
            booleanNegatable = booleanNegatable,
            negationPrefix = negationPrefix,
            positionalKind = positionalKind,
            positionalSeq = positionalSeq,
            envVar = envVar,
            expectedDesc = expectedDesc,
            eager = eager,
            hidden = hidden,
            isNonNullable = isNonNullable,
            defaultValue = defaultValue,
            callback = callback
        ).also { newBuilder ->
            // Copy constraint hooks and validators from the original builder
            newBuilder.constraintHooks.addAll(constraintHooks)
            newBuilder.validators.addAll(validators)
        }
    }

/**
 * Makes this nullable boolean option negatable with a custom prefix.
 * Creates an automatic negation option (e.g., --disable-colors for --colors with prefix "disable-").
 *
 * @param prefix The prefix to use for negation (default uses the Arguments class default "no-")
 */
fun OptionBuilder<Boolean?>.negatable(prefix: String? = null): OptionBuilder<Boolean?> {
    return createNegatableBuilder(prefix)
}

/**
 * Makes this non-nullable boolean option negatable with a custom prefix.
 * Creates an automatic negation option (e.g., --disable-colors for --colors with prefix "disable-").
 *
 * @param prefix The prefix to use for negation (default uses the Arguments class default "no-")
 */
@JvmName("negatableNonNull")
fun OptionBuilder<Boolean>.negatable(prefix: String? = null): OptionBuilder<Boolean> {
    return createNegatableBuilder(prefix)
}

/**
 * Converts string option to password option with prompting capabilities.
 */
fun OptionBuilder<String?>.password(
    prompt: String = "Password: ",
    confirmPrompt: String = "Confirm password: ",
    requireConfirmation: Boolean = false,
    mismatchPrompt: String = "Passwords do not match. Please try again.",
    maxRetries: Int = 3
): OptionBuilder<CharArray?> =
    OptionBuilder<CharArray?>(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = true,  // Keep true to ensure normal value consumption works
        single = { raw ->
            when {
                raw.present && raw.value != null -> {
                    // --password=value or --password value (consumed properly)
                    SingleResult.Value(raw.value.toCharArray())
                }
                raw.present && raw.value == null -> {
                    // This shouldn't normally happen with requiresValue=true,
                    // but if it does (e.g., custom parsing), prompt for password
                    val password = promptForPassword(prompt, confirmPrompt, requireConfirmation, mismatchPrompt, maxRetries)
                    if (password != null) SingleResult.Value(password) else SingleResult.Missing
                }
                else -> SingleResult.Missing // option not present
            }
        },
        booleanFlag = false,
        booleanNegatable = false,
        negationPrefix = null,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = "password",
        eager = eager
    ).also { builder ->
        // Store password configuration in the constraint hooks for later retrieval
        builder.constraintHooks.add { args, owner ->
            args.passwordConfigs[owner] = PasswordConfig(prompt, confirmPrompt, requireConfirmation, mismatchPrompt, maxRetries)
        }
    }

// ---- Supporting data classes and functions for password functionality ----

/**
 * Configuration for password options.
 */
data class PasswordConfig(
    /**
     * The prompt text displayed when asking for the password.
     */
    val prompt: String,

    /**
     * The prompt text displayed when asking for password confirmation.
     */
    val confirmPrompt: String,

    /**
     * Whether to require password confirmation by asking for the password twice.
     */
    val requireConfirmation: Boolean,

    /**
     * The message displayed when the password and confirmation don't match.
     */
    val mismatchPrompt: String,

    /**
     * Maximum number of retry attempts allowed for password confirmation.
     */
    val maxRetries: Int
)

internal fun promptForPassword(
    prompt: String,
    confirmPrompt: String,
    requireConfirmation: Boolean,
    mismatchPrompt: String,
    maxRetries: Int
): CharArray? {
    var attemptsLeft = maxRetries

    while (attemptsLeft > 0) {
        val password = readPasswordWithPrompt(prompt) ?: return null

        if (requireConfirmation) {
            val confirmation = readPasswordWithPrompt(confirmPrompt) ?: return null

            if (!password.contentEquals(confirmation)) {
                attemptsLeft--
                // Clear passwords for security
                password.fill('\u0000')
                confirmation.fill('\u0000')

                if (attemptsLeft > 0) {
                    OsBound.eprintln(mismatchPrompt)
                } else {
                    // Max retries exceeded - return null (missing)
                    return null
                }
            } else {
                // Passwords match - clear confirmation and return password
                confirmation.fill('\u0000')
                return password
            }
        } else {
            // No confirmation required - return password directly
            return password
        }
    }

    // This should not be reached, but return null as fallback
    return null
}

internal fun readPasswordWithPrompt(prompt: String): CharArray? {
    OsBound.eprint(prompt)
    OsBound.eflush()
    val password = OsBound.readPassword()
    // Note: System.console().readPassword() on JVM handles cursor positioning automatically
    // The fallback readlnOrNull() also handles newlines properly
    // No additional newline needed
    return password
}

internal fun formatValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"$value\""
    is CharArray -> "\"${value.concatToString()}\""
    is Array<*> -> "[${value.joinToString(", ") { formatValue(it) }}]"
    is Collection<*> -> "[${value.joinToString(", ") { formatValue(it) }}]"
    else -> value.toString()
}

/**
 * Sets the arity (number of required values) for this option.
 *
 * The option must consume exactly that many values from the command line.
 * This is incompatible with requiresValue(false) since multi-value options always require values.
 *
 * @param n The number of values this option should consume (must be >= 2)
 * @return ArityOptionBuilder<T> for further configuration
 */
fun <T : Any> OptionBuilder<T?>.arity(n: Int): ArityOptionBuilder<T> {
    if (n <= 1) throw ConfigError("arity must be at least 2 (got $n). Use regular options for single values.")
    if (!requiresValue) {
        throw ConfigError("arity is incompatible with requiresValue(false)")
    }
    if (envVar != null) {
        throw ConfigError("arity is incompatible with fromEnv() (environment variables are single values)")
    }

    // Create a non-nullable single function that unwraps T? to T
    val nonNullableSingle: (Raw) -> SingleResult<T> = { raw ->
        when (val result = single(raw)) {
            is SingleResult.Missing -> SingleResult.Missing
            is SingleResult.Value<*> -> {
                @Suppress("UNCHECKED_CAST")
                val value = result.value as T?
                if (value != null) {
                    @Suppress("UNCHECKED_CAST")
                    SingleResult.Value(value as T, result.source)
                } else {
                    SingleResult.Invalid("null value not allowed for arity option")
                }
            }
            is SingleResult.Invalid -> result
        }
    }

    // All arity options have n > 1, create mapped builder that will be replaced by the arity logic
    val arityBuilder: OptionBuilder<List<T>?> = map { value -> if (value != null) listOf(value) else null }

    return ArityOptionBuilder(arityBuilder, nonNullableSingle, n)
}

// Note: Uses the shared parseBooleanLiteral function from Arguments.kt
