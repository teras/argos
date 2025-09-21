/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Builder for non-nullable command-line options.
 * This type is returned by .default() and .required() to ensure type safety.
 * It delegates most operations to the underlying OptionBuilder while ensuring type safety.
 *
 * @property delegate The underlying nullable OptionBuilder
 */
class NonNullableOptionBuilder<T : Any> constructor(
    val delegate: OptionBuilder<T?>
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, T>> {

    /**
     * Provides the property delegate implementation for non-nullable options.
     * This method is called automatically by Kotlin's property delegation system.
     * It delegates to the underlying OptionBuilder but ensures the returned value is non-null.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property this delegate is assigned to
     * @return A ReadOnlyProperty that provides access to the non-null parsed value
     * @throws UninitializedPropertyException if the property value is null at access time
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, T> {
        val delegateProperty = delegate.provideDelegate(thisRef, property)
        return ReadOnlyProperty { args, prop ->
            delegateProperty.getValue(args, prop)!!
        }
    }

    /**
     * Maps the parsed value using the specified transformation function.
     * Preserves non-nullable type when transformation returns non-null.
     *
     * @param f Transformation function to apply to the parsed value
     * @return A new builder for the transformed type
     */
    fun <R : Any> map(f: (T) -> R): NonNullableOptionBuilder<R> =
        map(delegate.expectedDesc, f)

    /**
     * Maps the parsed value using the specified transformation function with custom expected description.
     *
     * @param desc Expected value description for help text (e.g., "integer", "email")
     * @param f Transformation function to apply to the parsed value
     * @return A new builder for the transformed type
     */
    fun <R : Any> map(desc: String?, f: (T) -> R): NonNullableOptionBuilder<R> =
        map(desc?.let { listOf(it) }, f)

    /**
     * Maps the parsed value using the specified transformation function with multiple expected descriptions.
     *
     * @param desc List of expected value descriptions for help text
     * @param f Transformation function to apply to the parsed value
     * @return A new builder for the transformed type
     */
    fun <R : Any> map(desc: List<String>?, f: (T) -> R): NonNullableOptionBuilder<R> =
        NonNullableOptionBuilder(delegate.map(desc) { value -> value?.let(f) })

    /**
     * Maps the parsed value, allowing nullable output. Returns a nullable OptionBuilder.
     *
     * @param desc Expected value description for help text
     * @param f Transformation function that may return null
     * @return A new nullable builder for the transformed type
     */
    fun <R : Any> map(desc: String?, f: (T) -> R?): OptionBuilder<R?> =
        map(desc?.let { listOf(it) }, f)

    /**
     * Maps the parsed value with multiple expected descriptions, allowing nullable output.
     *
     * @param desc List of expected value descriptions for help text
     * @param f Transformation function that may return null
     * @return A new nullable builder for the transformed type
     */
    fun <R : Any> map(desc: List<String>?, f: (T) -> R?): OptionBuilder<R?> =
        delegate.map(desc) { value -> value?.let(f) }

    // ---- Delegate all other methods with type-safe wrappers ----

    /**
     * Makes this option required if any of the referenced options are present.
     * This creates a conditional requirement where providing any of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.requireIfAnyPresent(*refs))

    /**
     * Makes this option required if all of the referenced options are present.
     * This creates a conditional requirement where providing all of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.requireIfAllPresent(*refs))

    /**
     * Makes this option required if any of the referenced options are absent.
     * This creates a conditional requirement where missing any of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    /**
     * Makes this option required if all of the referenced options are absent.
     * This creates a conditional requirement where all of the trigger options
     * being missing makes this option mandatory.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.requireIfAllAbsent(*refs))

    /**
     * Makes this option required if the referenced option's value matches the predicate.
     * This creates a value-based conditional requirement.
     *
     * @param ref Property reference to the option whose value triggers this requirement
     * @param predicate Function that returns true if this option should be required
     * @return This builder for method chaining
     */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.requireIfValue(ref, predicate))

    /**
     * Allows this option only when the specified property matches the given predicate.
     * If this option is provided but the condition is false, an error is reported.
     */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    /**
     * Creates an "exactly one" constraint with other options.
     * Exactly one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the exclusive group
     * @return This builder for method chaining
     */
    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.exactlyOneWith(*refs))

    /**
     * Creates an "at most one" constraint with other options.
     * At most one option from this group (including this option) may be provided.
     *
     * @param refs Property references to other options in the mutually exclusive group
     * @return This builder for method chaining
     */
    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.atMostOneWith(*refs))

    /**
     * Creates an "at least one" constraint with other options.
     * At least one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the required group
     * @return This builder for method chaining
     */
    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.atLeastOneWith(*refs))

    /**
     * Specifies that this option conflicts with other options.
     * If this option is provided along with any of the conflicting options,
     * parsing will fail with an error.
     *
     * @param refs Property references to options that conflict with this one
     * @return This builder for method chaining
     */
    fun conflictsWith(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.conflictsWith(*refs))

    /**
     * Adds validation for this option's value.
     * The validation is applied to the parsed non-null value.
     *
     * @param message Error message template. Supports placeholders: {option}, {value}
     * @param predicate Function that returns true if the value is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid value for {option}: {value}",
        predicate: (T) -> Boolean
    ): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.validate(message) { it?.let(predicate) == true })

    /**
     * Adds multiple validations for this option's value.
     * All validations are applied to the parsed non-null value.
     *
     * @param validations Pairs of (error message template, validation predicate)
     * @return This builder for method chaining
     */
    fun validate(vararg validations: Pair<String, (T) -> Boolean>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(
            delegate.validate(
                *validations.map { (msg, pred) -> msg to { value: T? -> value?.let(pred) == true } }.toTypedArray()
            )
        )

    /**
     * Sets the help text for this option.
     * The help text is displayed when the user requests help information.
     *
     * @param text Help text to display
     * @return This builder for method chaining
     */
    fun help(text: String): NonNullableOptionBuilder<T> = NonNullableOptionBuilder(delegate.help(text))

    /**
     * Marks this option as hidden from help output.
     * Hidden options are not displayed in help text but remain functional.
     *
     * @return This builder for method chaining
     */
    fun hidden(): NonNullableOptionBuilder<T> = NonNullableOptionBuilder(delegate.hidden())

    /**
     * Restricts this option to only be available in specific domains.
     * When specified, this option will only be recognized when one of the
     * referenced domains is active.
     *
     * @param refs Property references to domain properties that should allow this option
     * @return This builder for method chaining
     */
    fun onlyInDomains(vararg refs: KProperty<*>): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.onlyInDomains(*refs))

    /**
     * Configures this option to read its value from an environment variable.
     * If the command-line option is not provided, the specified environment
     * variable will be checked for a value.
     *
     * @param name Name of the environment variable to read from
     * @return This builder for method chaining
     */
    fun fromEnv(name: String): NonNullableOptionBuilder<T> = NonNullableOptionBuilder(delegate.fromEnv(name))

    /**
     * Registers a callback to be executed when this option's value is found during parsing.
     * The callback receives the converted non-null value and is executed after all parsing and validation completes.
     *
     * @param callback Function to call with the parsed value
     * @return This builder for method chaining
     */
    fun onValue(callback: (T) -> Unit): NonNullableOptionBuilder<T> =
        NonNullableOptionBuilder(delegate.onValue { value -> value?.let(callback) })
}

// ---- Extension functions for NonNullableOptionBuilder type refinement ----

/**
 * Converts this non-nullable string option to a non-nullable integer option.
 *
 * Parses string values as integers. Since the option is already non-nullable (via [default] or [required]),
 * the result is guaranteed to be non-null Int. Invalid values throw NumberFormatException during parsing.
 *
 * @return A new non-nullable builder that parses values as integers
 * @throws NumberFormatException If the value cannot be parsed as an integer
 * @see OptionBuilder.int For nullable variant
 */
fun NonNullableOptionBuilder<String>.int(): NonNullableOptionBuilder<Int> =
    map(desc = "integer") { it.toInt() }

/**
 * Converts this non-nullable string option to a non-nullable long integer option.
 *
 * @return A new non-nullable builder that parses values as long integers
 * @throws NumberFormatException If the value cannot be parsed as a long
 * @see OptionBuilder.long For nullable variant
 */
fun NonNullableOptionBuilder<String>.long(): NonNullableOptionBuilder<Long> =
    map(desc = "long") { it.toLong() }

/**
 * Converts this non-nullable string option to a non-nullable float option.
 *
 * @return A new non-nullable builder that parses values as floats
 * @throws NumberFormatException If the value cannot be parsed as a float
 * @see OptionBuilder.float For nullable variant
 */
fun NonNullableOptionBuilder<String>.float(): NonNullableOptionBuilder<Float> =
    map(desc = "float") { it.toFloat() }

/**
 * Converts this non-nullable string option to a non-nullable double option.
 *
 * @return A new non-nullable builder that parses values as doubles
 * @throws NumberFormatException If the value cannot be parsed as a double
 * @see OptionBuilder.double For nullable variant
 */
fun NonNullableOptionBuilder<String>.double(): NonNullableOptionBuilder<Double> =
    map(desc = "double") { it.toDouble() }

/**
 * Converts this non-nullable string option to a non-nullable enum option.
 *
 * Parses string values as enum constants. Since the option is non-nullable, the result is guaranteed
 * to be a valid enum value. Invalid values throw NoSuchElementException during parsing.
 *
 * @param E The enum type to parse into (must be reified)
 * @param aliases Optional map of alternative string values to enum constants
 * @param ignoreCase Whether to ignore case when matching (default: true)
 * @return A new non-nullable builder that parses values as enum constants
 * @throws NoSuchElementException If the value doesn't match any enum constant or alias
 * @see OptionBuilder.enum For nullable variant
 */
inline fun <reified E : Enum<E>> NonNullableOptionBuilder<String>.enum(
    aliases: Map<String, E> = emptyMap(),
    ignoreCase: Boolean = true
): NonNullableOptionBuilder<E> =
    map(desc = enumValues<E>().map { it.name }) { s ->
        aliases.entries.firstOrNull { it.key.equals(s, ignoreCase) }?.value
            ?: enumValues<E>().first { it.name.equals(s, ignoreCase) }
    }

/**
 * Restricts this non-nullable string option to accept only one of the specified allowed values.
 *
 * Since the option is non-nullable, the result is guaranteed to be one of the allowed values.
 * Invalid values throw NoSuchElementException during parsing.
 *
 * @param allowed The list of acceptable string values
 * @param ignoreCase Whether to ignore case when matching (default: true)
 * @return A new non-nullable builder that only accepts the specified values
 * @throws NoSuchElementException If the value is not in the allowed list
 * @see OptionBuilder.oneOf For nullable variant
 */
fun NonNullableOptionBuilder<String>.oneOf(
    vararg allowed: String,
    ignoreCase: Boolean = true
): NonNullableOptionBuilder<String> =
    map(desc = allowed.toList()) { s ->
        allowed.first { it.equals(s, ignoreCase) }
    }

/**
 * Makes this non-nullable boolean option negatable with automatic negation switches.
 *
 * Creates automatic negation options (e.g., `--no-cache` for `--cache`) that set the value to false.
 * The prefix can be customized or uses the default from the Arguments class.
 *
 * @param prefix The prefix to use for negation switches (default: uses Arguments class default "no-")
 * @return A new non-nullable builder with negatable behavior
 * @see OptionBuilder.negatable For nullable variant
 */
fun NonNullableOptionBuilder<Boolean>.negatable(prefix: String? = null): NonNullableOptionBuilder<Boolean> {
    // The delegate is OptionBuilder<Boolean?> but we need the non-nullable negatable behavior
    // Since this is a NonNullableOptionBuilder, we know the value will never be null at runtime
    // So we can safely call the nullable version and wrap it back
    return NonNullableOptionBuilder(delegate.negatable(prefix))
}
