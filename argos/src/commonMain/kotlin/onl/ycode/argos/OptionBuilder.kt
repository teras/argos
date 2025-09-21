/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.snapshot.PositionalKind
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
    internal val negationPrefix: String?,
    internal val positionalKind: PositionalKind?,
    internal val positionalSeq: Int?,
    internal val envVar: String?,
    internal val expectedDesc: List<String>?,
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

        // Resolve the actual negation prefix
        val actualNegationPrefix = if (negationPrefix == "") {
            thisRef.getDefaultNegationPrefix()  // Use default when empty string marker present
        } else {
            negationPrefix  // Use explicit prefix or null (not negatable)
        }

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
            negationPrefix = actualNegationPrefix,
            positionalKind = positionalKind,
            positionalSeq = seq,
            envVar = envVar,
            expectedDesc = expectedDesc,
            eager = eager,
            hidden = hidden,
            defaultValue = defaultValue,
            parseMany = { raws, baseSwitch, expected ->
                val r = raws.lastOrNull() ?: Raw(false, null, null)
                when (val res = single(r)) {
                    is SingleResult.Missing -> ParseOutcome.Success(null)
                    is SingleResult.Value<*> -> {
                        // Update value source from SingleResult
                        thisRef.valueSources[name] = res.source

                        ParseOutcome.Success(res.value)
                    }

                    is SingleResult.Invalid -> {

                        ParseOutcome.Error(
                            invalidValueFragments(
                                res.original ?: r.value,
                                baseSwitch,
                                r.argvIndex,
                                expected
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
     * Makes this option required if any of the referenced options are present.
     *
     * Creates a conditional requirement where providing any of the trigger options makes this option mandatory.
     * The constraint is evaluated after all parsing completes. This enables dependencies where the presence
     * of one or more related options requires this option to also be provided.
     *
     * @param refs Property references to options that trigger this requirement when present
     * @return This builder for method chaining
     * @see requireIfAllPresent For requiring when all specified options are present
     * @see requireIfAnyAbsent For requiring when any specified option is absent
     */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ANY_PRESENT, refs) }

    /**
     * Makes this option required when all of the referenced options are present.
     *
     * Creates a conditional requirement where this option becomes mandatory only when all specified
     * trigger options are provided. The constraint is evaluated after parsing completes. This is useful
     * for scenarios where a complete set of related options requires additional configuration.
     *
     * @param refs Property references to options that collectively trigger this requirement
     * @return This builder for method chaining
     * @see requireIfAnyPresent For requiring when any specified option is present
     * @see requireIfAllAbsent For requiring when all specified options are absent
     */
    fun requireIfAllPresent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ALL_PRESENT, refs) }

    /**
     * Makes this option required when any of the referenced options are absent.
     *
     * Creates a conditional requirement where this option becomes mandatory if any of the specified
     * options are missing. The constraint is evaluated after parsing completes. This is useful for
     * providing fallback or alternative configuration when expected options are not provided.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     * @see requireIfAllAbsent For requiring when all specified options are absent
     * @see requireIfAnyPresent For requiring when any specified option is present
     */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ANY_ABSENT, refs) }

    /**
     * Makes this option required when all of the referenced options are absent.
     *
     * Creates a conditional requirement where this option becomes mandatory only when all specified
     * options are missing. The constraint is evaluated after parsing completes. This is useful for
     * enforcing that at least one configuration path is provided when expected options are unavailable.
     *
     * @param refs Property references to options whose collective absence triggers this requirement
     * @return This builder for method chaining
     * @see requireIfAnyAbsent For requiring when any specified option is absent
     * @see requireIfAllPresent For requiring when all specified options are present
     */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConditionalConstraint(constraintHooks, CondKind.ALL_ABSENT, refs) }

    /**
     * Makes this option required when the referenced option's value matches the given predicate.
     *
     * Creates a value-based conditional requirement where this option becomes mandatory based on another
     * option's value. The predicate is evaluated after parsing completes and receives the parsed value
     * (which may be null). This enables sophisticated conditional logic based on actual values.
     *
     * @param U The type of the referenced option's value
     * @param ref Property reference to the option whose value is checked
     * @param predicate Function that evaluates the referenced option's value and returns true when this option should be required
     * @return This builder for method chaining
     * @see allowOnlyIfValue For preventing usage based on value conditions
     * @see requireIfAnyPresent For requiring based on presence rather than value
     */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionBuilder<T> = apply {
        addPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Restricts this option to only be allowed when the referenced option's value matches the given predicate.
     *
     * Creates a value-based permission constraint where this option can only be used when another option's
     * value satisfies the predicate. If the user provides this option when the condition is false, an error
     * is reported. The predicate is evaluated after parsing and receives the parsed value (which may be null).
     * This is the inverse of [requireIfValue] - it prevents usage rather than requiring presence.
     *
     * @param U The type of the referenced option's value
     * @param ref Property reference to the option whose value determines permission
     * @param predicate Function that evaluates the referenced option's value and returns true when this option is allowed
     * @return This builder for method chaining
     * @see requireIfValue For requiring based on value conditions
     * @see conflictsWith For unconditional mutual exclusion
     */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionBuilder<T> = apply {
        addAllowOnlyIfPredicateConstraint(
            constraintHooks,
            ref.name
        ) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Ensures that exactly one option from the group (this option and the referenced options) is provided.
     *
     * Creates a mutual exclusivity constraint where the user must provide exactly one option from the group.
     * If none are provided or more than one is provided, an error is reported. This is useful for mutually
     * exclusive alternatives where one choice must be made.
     *
     * @param refs Property references to other options in the mutual exclusivity group
     * @return This builder for method chaining
     * @see atMostOneWith For allowing zero or one from the group
     * @see atLeastOneWith For requiring one or more from the group
     * @see conflictsWith For simple pairwise conflicts
     */
    fun exactlyOneWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addGroupConstraint(constraintHooks, "exactly-one", refs) }

    /**
     * Ensures that at most one option from the group (this option and the referenced options) is provided.
     *
     * Creates a partial mutual exclusivity constraint where zero or one option from the group may be provided,
     * but not more than one. If multiple options from the group are provided, an error is reported. This is
     * useful for optional alternatives where providing more than one would be conflicting.
     *
     * @param refs Property references to other options in the group
     * @return This builder for method chaining
     * @see exactlyOneWith For requiring exactly one from the group
     * @see atLeastOneWith For requiring one or more from the group
     * @see conflictsWith For simple pairwise conflicts
     */
    fun atMostOneWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addGroupConstraint(constraintHooks, "at-most-one", refs) }

    /**
     * Ensures that at least one option from the group (this option and the referenced options) is provided.
     *
     * Creates a requirement constraint where the user must provide one or more options from the group.
     * If none of the options in the group are provided, an error is reported. This is useful for ensuring
     * that at least one configuration path is selected from a set of alternatives.
     *
     * @param refs Property references to other options in the group
     * @return This builder for method chaining
     * @see exactlyOneWith For requiring exactly one from the group
     * @see atMostOneWith For allowing zero or one from the group
     */
    fun atLeastOneWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addGroupConstraint(constraintHooks, "at-least-one", refs) }

    /**
     * Declares that this option conflicts with the referenced options and cannot be used together.
     *
     * Creates pairwise conflict constraints where this option cannot be provided simultaneously with any
     * of the referenced options. If this option and any conflicting option are both provided, an error is
     * reported. This is simpler than [atMostOneWith] for basic mutual exclusion scenarios.
     *
     * @param refs Property references to options that conflict with this option
     * @return This builder for method chaining
     * @see atMostOneWith For group-based mutual exclusivity
     * @see exactlyOneWith For requiring exactly one from mutually exclusive options
     */
    fun conflictsWith(vararg refs: KProperty<*>): OptionBuilder<T> =
        apply { addConflictsConstraint(constraintHooks, refs) }

    // ---- Validation methods ----

    /**
     * Adds a custom validator that checks the parsed value against the given predicate.
     *
     * Validators run after type conversion but before constraint checking. The validator only executes
     * if the value is not null. If the predicate returns false, parsing fails with the specified error
     * message. Multiple validators can be added and all will be checked in order.
     *
     * The message is a template that supports placeholder substitution:
     * - `{option}` - The option's property name
     * - `{value}` - The invalid value that failed validation (properly formatted)
     *
     * @param message Error message template for validation failures (default: "Invalid value for {option}: {value}")
     * @param predicate Function that receives the parsed value and returns true if valid, false if invalid
     * @return This builder for method chaining
     * @see validate For adding multiple validators at once
     */
    fun validate(
        message: String = "Invalid value for {option}: {value}",
        predicate: (T) -> Boolean
    ): OptionBuilder<T> = apply {
        validators.add(message to predicate)
    }

    /**
     * Adds multiple validators at once for batch validation configuration.
     *
     * This is a convenience method for adding several validators in a single call. Each pair consists
     * of an error message template and a validation predicate. All validators are checked in the order
     * they are added.
     *
     * @param validations Vararg of pairs, each containing (error message template, validation predicate)
     * @return This builder for method chaining
     * @see validate For adding a single validator with detailed documentation
     */
    fun validate(vararg validations: Pair<String, (T) -> Boolean>): OptionBuilder<T> = apply {
        validators.addAll(validations)
    }

    /**
     * Registers a callback to be executed when this option's value is parsed from the command line.
     *
     * The callback receives the converted and validated value and executes after all parsing and validation
     * completes successfully. If the option is provided multiple times, the callback is invoked for each
     * occurrence in the order they appear on the command line. This is useful for side effects like logging,
     * updating external state, or performing additional validation.
     *
     * **Note**: For collection options (.list()/.set()), the callback receives individual values, not the collection.
     *
     * @param callback Function to invoke with each parsed value
     * @return A new builder with the callback registered
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
     * The transformation function may return null, resulting in a nullable OptionBuilder.
     *
     * @param f Transformation function to apply to the parsed value
     * @return A new nullable builder for the transformed type
     */
    fun <R> map(f: (T) -> R?): OptionBuilder<R?> = map(expectedDesc, f)

    /**
     * Maps the parsed value using the specified transformation function with custom expected description.
     *
     * @param desc Expected value description for help text (e.g., "integer", "email")
     * @param f Transformation function that may return null
     * @return A new nullable builder for the transformed type
     */
    fun <R> map(desc: String?, f: (T) -> R?): OptionBuilder<R?> =
        map(desc?.let { listOf(it) }, f)

    /**
     * Maps the parsed value using the specified transformation function with multiple expected descriptions.
     *
     * @param desc List of expected value descriptions for help text
     * @param f Transformation function that may return null
     * @return A new nullable builder for the transformed type
     */
    fun <R> map(desc: List<String>?, f: (T) -> R?): OptionBuilder<R?> =
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
     * Sets the help text description for this option.
     *
     * The help text is displayed in the generated help output alongside the option's switches and
     * expected value type. Use this to clearly explain what the option does and when to use it.
     *
     * @param text The descriptive help text to display for this option
     * @return A new builder with the help text configured
     */
    fun help(text: String): OptionBuilder<T> =
        OptionBuilder(
            switches,
            repeatable,
            text,
            domainProps,
            requiresValue,
            single,
            booleanFlag,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar,
            expectedDesc,
            eager,
            hidden,
            isNonNullable,
            defaultValue
        )

    /**
     * Marks this option as hidden, excluding it from help output.
     *
     * Hidden options are fully functional but are not displayed in help text. This is useful for
     * internal options, deprecated options, or options that shouldn't be advertised to users.
     * The option can still be used normally if users know about it.
     *
     * @return A new builder with the hidden flag enabled
     * @see help For setting visible help text
     */
    fun hidden(): OptionBuilder<T> =
        OptionBuilder(
            switches,
            repeatable,
            helpText,
            domainProps,
            requiresValue,
            single,
            booleanFlag,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar,
            expectedDesc,
            eager,
            true,
            isNonNullable,
            defaultValue
        )

    /**
     * Restricts this option to only be available within the specified domains (subcommands).
     *
     * When an option is restricted to specific domains, it can only be used when one of those domains
     * is active. If the option is provided outside of the allowed domains, an error is reported. This
     * is useful for domain-specific options that don't make sense globally.
     *
     * @param refs Property references to domain definitions where this option should be available
     * @return A new builder with domain restrictions configured
     * @throws ConfigException If no domain references are provided
     */
    fun onlyInDomains(vararg refs: KProperty<*>): OptionBuilder<T> {
        if (refs.isEmpty()) throw ConfigException("onlyInDomains requires at least 1 domain (got ${refs.size})")
        return OptionBuilder(
            switches,
            repeatable,
            helpText,
            refs.map { it.name }.toSet(),
            requiresValue,
            single,
            booleanFlag,
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
     * Configures this option to read its value from the specified environment variable when not provided on the command line.
     *
     * If the option is not provided as a command-line argument, the library will attempt to read its value
     * from the specified environment variable. Command-line values always take precedence over environment
     * variables. The value source can be queried using [Arguments.valueSourceOf] to determine where the
     * value came from.
     *
     * @param name The name of the environment variable to read from
     * @return A new builder with environment variable fallback configured
     * @see Arguments.valueSourceOf For determining the source of a value
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
        throw ConfigException("negatable() can only be used on boolean options created with .bool()")
    }
    if (prefix != null && prefix.trim().isEmpty()) {
        throw ConfigException("negatable prefix cannot be empty or whitespace-only")
    }

    return createModifiedBuilder(
        negationPrefix = prefix ?: ""  // Use empty string as marker for default prefix
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
 * Converts this string option to an integer option.
 *
 * Parses string values as integers using Kotlin's standard integer parsing. Invalid values
 * (non-numeric strings or numbers outside Int range) result in parse errors. The result type
 * is [Int?] (nullable) - use [NonNullableOptionBuilder.default] or [NonNullableOptionBuilder.required]
 * to make it non-nullable.
 *
 * @return A new builder that parses values as integers
 * @see long For 64-bit integers
 * @see double For decimal numbers
 */
fun OptionBuilder<String?>.int() = map(desc = "integer") { it?.toIntOrNull() }

/**
 * Converts this string option to a long integer option.
 *
 * Parses string values as 64-bit long integers using Kotlin's standard parsing. Invalid values
 * (non-numeric strings or numbers outside Long range) result in parse errors. The result type
 * is [Long?] (nullable) - use [NonNullableOptionBuilder.default] or [NonNullableOptionBuilder.required]
 * to make it non-nullable.
 *
 * @return A new builder that parses values as long integers
 * @see int For 32-bit integers
 * @see double For decimal numbers
 */
fun OptionBuilder<String?>.long() = map(desc = "long") { it?.toLongOrNull() }

/**
 * Converts this string option to a single-precision floating-point option.
 *
 * Parses string values as 32-bit float values using Kotlin's standard parsing. Invalid values
 * (non-numeric strings) result in parse errors. The result type is [Float?] (nullable) - use
 * [NonNullableOptionBuilder.default] or [NonNullableOptionBuilder.required] to make it non-nullable.
 *
 * @return A new builder that parses values as floats
 * @see double For double-precision decimals
 * @see int For integers
 */
fun OptionBuilder<String?>.float() = map(desc = "float") { it?.toFloatOrNull() }

/**
 * Converts this string option to a double-precision floating-point option.
 *
 * Parses string values as 64-bit double values using Kotlin's standard parsing. Invalid values
 * (non-numeric strings) result in parse errors. The result type is [Double?] (nullable) - use
 * [NonNullableOptionBuilder.default] or [NonNullableOptionBuilder.required] to make it non-nullable.
 *
 * @return A new builder that parses values as doubles
 * @see float For single-precision decimals
 * @see int For integers
 */
fun OptionBuilder<String?>.double() = map(desc = "double") { it?.toDoubleOrNull() }

/**
 * Converts this string option to a key-value pair option.
 *
 * Parses string values in the format "key\<separator\>value" and returns a [KeyValue] object containing
 * both the key and value as separate strings. The separator defaults to "=" but can be customized.
 * Values that don't contain the separator result in parse errors. Use with [CollectionOptionBuilder.list]
 * or [CollectionOptionBuilder.set] to collect multiple key-value pairs, which can be easily converted
 * to a Map using [KeyValue.toMap].
 *
 * @param separator The string used to separate key and value (default: "=")
 * @return A new builder that parses values as key-value pairs
 * @see KeyValue For the resulting data type
 */
fun OptionBuilder<String?>.keyvalue(separator: String = "=") = map(desc = "key${separator}value") { str ->
    str?.let { s ->
        val parts = s.split(separator, limit = 2)
        if (parts.size == 2) {
            KeyValue(parts[0], parts[1])
        } else null
    }
}

/**
 * Converts this string option to a boolean option.
 *
 * Boolean options have special parsing behavior:
 * - When the switch is present without a value, it defaults to `true`
 * - Explicit boolean values can be provided: "true"/"false", "yes"/"no", "on"/"off", "1"/"0" (case-insensitive)
 * - Can be made negatable using [negatable] to auto-generate opposite switches (e.g., `--no-cache` for `--cache`)
 * - Can be made eager using [eager] for early evaluation (useful for `--help` and `--version`)
 * - By default, boolean options do not require a value (the switch alone means true)
 *
 * @return A new builder that parses values as booleans
 * @see negatable For creating negatable boolean options
 * @see eager For early evaluation
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
                val baseValue = when {
                    lit == null -> true
                    else -> lit
                }
                // If this is a negated option with explicit value, invert the parsed boolean
                val finalValue = if (raw.negated) !baseValue else baseValue
                SingleResult.Value(finalValue)
            }
        },
        booleanFlag = true,
        negationPrefix = null,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = listOf("boolean"),
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
    if (!this.booleanFlag) throw ConfigException("eager() can only be applied to boolean options")
    return OptionBuilder(
        switches = switches,
        repeatable = repeatable,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = false,
        single = single,
        booleanFlag = true,
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
 * Converts this string option to an enum option.
 *
 * Parses string values as enum constants using the enum's name. Matching is case-insensitive by default.
 * You can provide a map of aliases to map alternative string values to specific enum constants.
 * Invalid values (strings that don't match any enum constant or alias) result in parse errors.
 * The result type is [E?] (nullable) - use [default] or [required] to make it non-nullable.
 *
 * @param E The enum type to parse into (must be reified)
 * @param aliases Optional map of alternative string values to enum constants
 * @param ignoreCase Whether to ignore case when matching (default: true)
 * @return A new builder that parses values as enum constants
 * @see oneOf For restricting to specific string values without enums
 */
inline fun <reified E : Enum<E>> OptionBuilder<String?>.enum(
    aliases: Map<String, E> = emptyMap(),
    ignoreCase: Boolean = true
): OptionBuilder<E?> {
    return map(desc = enumValues<E>().map { it.name }) { s ->
        s?.let { v ->
            aliases.entries.firstOrNull { it.key.equals(v, ignoreCase) }?.value
                ?: enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase) }
        }
    }
}

/**
 * Restricts this string option to accept only one of the specified allowed values.
 *
 * Values not in the allowed set result in parse errors. Matching is case-insensitive by default.
 * This provides simple value restriction without needing to define an enum type.
 *
 * @param allowed The list of acceptable string values
 * @param ignoreCase Whether to ignore case when matching (default: true)
 * @return A new builder that only accepts the specified values
 * @see enum For type-safe enum parsing
 */
fun OptionBuilder<String?>.oneOf(vararg allowed: String, ignoreCase: Boolean = true): OptionBuilder<String?> =
    map(allowed.toList()) { s ->
        s?.let { v -> allowed.firstOrNull { it.equals(v, ignoreCase) } }
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
                is SingleResult.Value -> SingleResult.Value(
                    (r.value ?: value) as T?,
                    if (r.value != null) r.source else ValueSource.DEFAULT
                )
            }
        },
        booleanFlag = booleanFlag,
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
 * Converts this option to a list collector that accumulates multiple values.
 *
 * List options can be specified multiple times on the command line, with each occurrence adding a value
 * to the list. Lists preserve insertion order and allow duplicate values. The returned list is never null,
 * but may be empty if the option is never provided. Use constraint methods like [CollectionOptionBuilder.atLeast]
 * to require a minimum number of values.
 *
 * @param T The element type (must be non-nullable)
 * @return A new [OptionListBuilder] that collects values into a list
 * @see set For collecting unique values
 * @see arity For consuming multiple values per invocation
 */
fun <T : Any> OptionBuilder<T?>.list() =
    OptionListBuilder(
        switches, helpText, domainProps, requiresValue,
        single = { raw -> this.single(raw) }, booleanFlag = booleanFlag, negationPrefix = negationPrefix,
        positionalKind = if (positionalKind != null) PositionalKind.LIST else null,
        positionalSeq = positionalSeq, expectedDesc = expectedDesc, hidden = hidden,
        isNonNullable = false,
        callback = callback
    )

/**
 * Converts this option to a set collector that accumulates unique values.
 *
 * Set options can be specified multiple times on the command line, but duplicate values are automatically
 * filtered out. The set preserves insertion order (using LinkedHashSet). The returned set is never null,
 * but may be empty if the option is never provided. Use constraint methods like [CollectionOptionBuilder.atLeast]
 * to require a minimum number of unique values.
 *
 * @param T The element type (must be non-nullable)
 * @return A new [OptionSetBuilder] that collects unique values into a set
 * @see list For collecting values with duplicates allowed
 * @see arity For consuming multiple values per invocation
 */
fun <T : Any> OptionBuilder<T?>.set() =
    OptionSetBuilder(
        switches, helpText, domainProps, requiresValue,
        single = { raw -> this.single(raw) }, booleanFlag = booleanFlag, negationPrefix = negationPrefix,
        positionalKind = if (positionalKind != null) PositionalKind.SET else null,
        positionalSeq = positionalSeq, expectedDesc = expectedDesc, hidden = hidden
    )


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
 * Converts this string option to a secure input option with interactive prompting capabilities.
 *
 * Input options return CharArray instead of String for security (can be cleared from memory after use).
 * When the option is provided without a value, the user is prompted to enter the input interactively
 * with either clear-text or hidden input based on the [hidden] flag. Values provided on the command line
 * (e.g., `--password=secret`) are accepted directly.
 *
 * **Security Note**: Providing sensitive values on the command line is insecure as they appear in process
 * lists and shell history. Interactive prompting is recommended for production use.
 *
 * @param prompt The prompt message shown when requesting input (default: "Password: ")
 * @param hidden Whether to hide user input using the native hidden prompt (default: false)
 * @param confirmPrompt The prompt message for confirmation when [requireConfirmation] is true (default: "Confirm password: ")
 * @param requireConfirmation Whether to require the user to enter the input twice (default: false)
 * @param mismatchPrompt The message shown when confirmation doesn't match (default: "Passwords do not match. Please try again.")
 * @param maxRetries Maximum number of retry attempts for mismatched confirmations (default: 3)
 * @return A new builder that parses or prompts for input as CharArray
 * @see CharArray For secure input storage that can be cleared from memory
 */
fun OptionBuilder<String?>.input(
    prompt: String = "Password: ",
    hidden: Boolean = false,
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
                    val password = promptForInput(
                        prompt,
                        hidden,
                        confirmPrompt,
                        requireConfirmation,
                        mismatchPrompt,
                        maxRetries
                    )
                    if (password != null) SingleResult.Value(password) else SingleResult.Missing
                }

                else -> SingleResult.Missing // option not present
            }
        },
        booleanFlag = false,
        negationPrefix = null,
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        envVar = envVar,
        expectedDesc = listOf("input"),
        eager = eager
    ).also { builder ->
        // Store input configuration in the constraint hooks for later retrieval
        builder.constraintHooks.add { args, owner ->
            args.inputConfigs[owner] =
                InputConfig(prompt, hidden, confirmPrompt, requireConfirmation, mismatchPrompt, maxRetries)
        }
    }

// ---- Supporting data classes and functions for secure input functionality ----

/**
 * Configuration for secure input options.
 */
data class InputConfig(
    /**
     * The prompt text displayed when asking for the input.
     */
    val prompt: String,

    /**
     * Whether the input should be captured with hidden echo suppression.
     */
    val hidden: Boolean,

    /**
     * The prompt text displayed when asking for input confirmation.
     */
    val confirmPrompt: String,

    /**
     * Whether to require confirmation by asking for the input twice.
     */
    val requireConfirmation: Boolean,

    /**
     * The message displayed when the input and confirmation don't match.
     */
    val mismatchPrompt: String,

    /**
     * Maximum number of retry attempts allowed for input confirmation.
     */
    val maxRetries: Int
)

internal fun promptForInput(
    prompt: String,
    hidden: Boolean,
    confirmPrompt: String,
    requireConfirmation: Boolean,
    mismatchPrompt: String,
    maxRetries: Int
): CharArray? {
    var attemptsLeft = maxRetries

    while (attemptsLeft > 0) {
        val password = readInputWithPrompt(prompt, hidden) ?: return null

        if (requireConfirmation) {
            val confirmation = readInputWithPrompt(confirmPrompt, hidden) ?: return null

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

internal fun readInputWithPrompt(prompt: String, hidden: Boolean): CharArray? {
    OsBound.eprint(prompt)
    OsBound.eflush()
    val password = if (hidden) {
        OsBound.readPassword()
    } else {
        OsBound.readLine()?.toCharArray()
    }
    // Note: System.console().readPassword() on JVM handles cursor positioning automatically
    // The fallback readlnOrNull() also handles newlines properly
    // No additional newline needed
    return password
}

internal fun formatValue(value: Any?, separator: String = ", "): String = when (value) {
    null -> "null"
    is String -> "\"$value\""
    is CharArray -> "\"${value.concatToString()}\""
    is Array<*> -> "[${value.joinToString(separator) { formatValue(it, separator) }}]"
    is Collection<*> -> "[${value.joinToString(separator) { formatValue(it, separator) }}]"
    else -> value.toString()
}

/**
 * Configures this option to consume exactly N values per invocation, enabling structured multi-value parsing.
 *
 * Arity options consume a fixed number of values each time the option appears on the command line.
 * Each invocation produces a List<T> containing exactly N values. For example, `--point 10 20 30` with
 * arity(3) consumes three values and produces `[10, 20, 30]`.
 *
 * When combined with [list] or [set], arity enables collecting multiple groups of values:
 * - `.arity(N)` → Produces `List<T>?` (nullable single group)
 * - `.list().arity(N)` → Produces `List<List<T>>` (multiple groups)
 * - `.set().arity(N)` → Produces `Set<List<T>>` (unique groups)
 *
 * **Restrictions:**
 * - Arity must be at least 2 (use regular options for single values)
 * - Incompatible with `requiresValue(false)` (multi-value options always require values)
 * - Incompatible with `fromEnv()` (environment variables are single values)
 *
 * @param n The number of values to consume per invocation (must be >= 2)
 * @return [ArityOptionBuilder] for further configuration with arity-specific constraints
 * @throws ConfigException If n < 2, or if incompatible with requiresValue(false) or fromEnv()
 * @see list For collecting multiple invocations with arity
 * @see set For collecting unique invocations with arity
 */
fun <T : Any> OptionBuilder<T?>.arity(n: Int): ArityOptionBuilder<T> {
    if (n <= 1) throw ConfigException("arity must be at least 2 (got $n). Use regular options for single values.")
    if (!requiresValue) {
        throw ConfigException("arity is incompatible with requiresValue(false)")
    }
    if (envVar != null) {
        throw ConfigException("arity is incompatible with fromEnv() (environment variables are single values)")
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
