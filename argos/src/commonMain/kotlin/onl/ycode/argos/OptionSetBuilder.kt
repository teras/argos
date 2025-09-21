/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.snapshot.PositionalKind
import kotlin.reflect.KProperty

/**
 * Builder for creating command-line set options with various configurations and constraints.
 *
 * This class provides a fluent API for defining set options that can collect unique values,
 * including type refinements, constraints, environment variable support, and help text.
 * Sets preserve insertion order but ensure uniqueness.
 */
class OptionSetBuilder<T : Any> internal constructor(
    switches: List<String>,
    helpText: String?,
    domainProps: Set<String>?,
    requiresValue: Boolean,
    single: (Raw) -> SingleResult<T?>,
    booleanFlag: Boolean,
    negationPrefix: String?,
    positionalKind: PositionalKind?,
    positionalSeq: Int?,
    expectedDesc: List<String>?,
    hidden: Boolean = false,
    isNonNullable: Boolean = false,
    callback: ((T) -> Unit)? = null
) : CollectionOptionBuilder<T, Set<T>>(
    switches, helpText, domainProps, requiresValue, single, booleanFlag,
    negationPrefix, positionalKind, positionalSeq, expectedDesc, hidden, isNonNullable, callback
) {

    override fun createCollection(values: List<T>): Set<T> {
        return LinkedHashSet(values)
    }

    override fun getValueKind(): ValueKind = ValueKind.SET

    // ---- Set-specific fluent API ----

    /**
     * Marks this set option as required, equivalent to atLeast(1).
     */
    fun required(): OptionSetBuilder<T> = atLeast(1)

    // ---- Validation methods ----

    /**
     * Adds a validator that checks each element in the set against the given predicate.
     *
     * @param message Template message for validation errors. Supports:
     *   - {option}: property name
     *   - {value}: the invalid element value (properly formatted)
     * @param predicate Function that returns true if the element is valid
     */
    override fun validate(
        message: String,
        predicate: (T) -> Boolean
    ): OptionSetBuilder<T> = apply {
        super.validate(message, predicate)
    }

    /**
     * Adds multiple element validators at once.
     *
     * @param validations Pairs of (message template, predicate) for element validation
     */
    fun validate(vararg validations: Pair<String, (T) -> Boolean>): OptionSetBuilder<T> = apply {
        validations.forEach { (message, predicate) ->
            validate(message, predicate)
        }
    }

    /**
     * Adds a validator that checks the entire set against the given predicate.
     *
     * @param message Template message for validation errors. Supports:
     *   - {option}: property name
     *   - {value}: the set (properly formatted)
     * @param predicate Function that returns true if the set is valid
     */
    override fun validateCollection(
        message: String,
        predicate: (Set<T>) -> Boolean
    ): OptionSetBuilder<T> = apply {
        super.validateCollection(message, predicate)
    }

    // Return types are covariant for Set-specific fluent API

    /**
     * Restricts this set option to only be available in specific domains.
     * When specified, this option will only be recognized when one of the
     * referenced domains is active.
     *
     * @param refs Property references to domain properties that should allow this option
     * @return This builder for method chaining
     */
    override fun onlyInDomains(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.onlyInDomains(*refs) as OptionSetBuilder<T>

    /**
     * Requires this set option to have at least the specified number of unique values.
     * This constraint is validated after parsing completes.
     *
     * @param n Minimum number of unique values required
     * @return This builder for method chaining
     */
    override fun atLeast(n: Int): OptionSetBuilder<T> =
        super.atLeast(n) as OptionSetBuilder<T>

    /**
     * Specifies that this set option conflicts with other options.
     * If this option is provided along with any of the conflicting options,
     * parsing will fail with an error.
     *
     * @param refs Property references to options that conflict with this one
     * @return This builder for method chaining
     */
    override fun conflictsWith(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.conflictsWith(*refs) as OptionSetBuilder<T>

    /**
     * Makes this set option required if any of the referenced options are present.
     * This creates a conditional requirement where providing any of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    override fun requireIfAnyPresent(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.requireIfAnyPresent(*refs) as OptionSetBuilder<T>

    /**
     * Makes this set option required if all of the referenced options are present.
     * This creates a conditional requirement where providing all of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    override fun requireIfAllPresent(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.requireIfAllPresent(*refs) as OptionSetBuilder<T>

    override fun requireIfAnyAbsent(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.requireIfAnyAbsent(*refs) as OptionSetBuilder<T>

    override fun requireIfAllAbsent(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.requireIfAllAbsent(*refs) as OptionSetBuilder<T>

    override fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionSetBuilder<T> =
        super.requireIfValue(ref, predicate) as OptionSetBuilder<T>

    override fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionSetBuilder<T> =
        super.allowOnlyIfValue(ref, predicate) as OptionSetBuilder<T>

    /**
     * Creates an "exactly one" constraint with other options.
     * Exactly one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the exclusive group
     * @return This builder for method chaining
     */
    override fun exactlyOneWith(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.exactlyOneWith(*refs) as OptionSetBuilder<T>

    /**
     * Creates an "at most one" constraint with other options.
     * At most one option from this group (including this option) may be provided.
     *
     * @param refs Property references to other options in the mutually exclusive group
     * @return This builder for method chaining
     */
    override fun atMostOneWith(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.atMostOneWith(*refs) as OptionSetBuilder<T>

    /**
     * Creates an "at least one" constraint with other options.
     * At least one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the required group
     * @return This builder for method chaining
     */
    override fun atLeastOneWith(vararg refs: KProperty<*>): OptionSetBuilder<T> =
        super.atLeastOneWith(*refs) as OptionSetBuilder<T>

    /**
     * Creates an arity option for sets where each invocation consumes exactly `n` values.
     *
     * For example:
     * ```
     * val coords by option("--coords").int().set().arity(2)
     * // Usage: --coords 1 2 --coords 3 4
     * // Result: Set([1, 2], [3, 4])  // Set<List<Int>>
     * ```
     *
     * @param n Number of values each option invocation must consume
     * @return AritySetOptionBuilder for further configuration
     */
    fun arity(n: Int): AritySetOptionBuilder<T> {
        if (n <= 1) throw ConfigException("Arity must be at least 2 (got $n). Use regular set() for single values.")

        // Create a non-nullable single function for arity
        val nonNullableSingle = createNonNullableSingleForArity(single)

        return AritySetOptionBuilder(this, nonNullableSingle, n, emptyList(), emptyList())
    }
}

/**
 * Makes this boolean set option negatable with a custom prefix.
 * Creates an automatic negation option (e.g., --disable-colors for --colors with prefix "disable-").
 *
 * @param prefix The prefix to use for negation (default uses the Arguments class default "no-")
 */
fun OptionSetBuilder<Boolean>.negatable(prefix: String? = null): OptionSetBuilder<Boolean> {
    if (!booleanFlag) {
        throw ConfigException("negatable() can only be used on boolean set options created with .bool().set()")
    }
    if (prefix != null && prefix.trim().isEmpty()) {
        throw ConfigException("negatable prefix cannot be empty or whitespace-only")
    }

    return OptionSetBuilder(
        switches = switches,
        helpText = helpText,
        domainProps = domainProps,
        requiresValue = requiresValue,
        single = single,
        booleanFlag = true,
        negationPrefix = prefix ?: "no-",
        positionalKind = positionalKind,
        positionalSeq = positionalSeq,
        expectedDesc = expectedDesc,
        hidden = hidden,
        isNonNullable = isNonNullable,
        callback = callback
    )
}
