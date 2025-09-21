/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.snapshot.PositionalKind
import kotlin.reflect.KProperty

/**
 * Builder for creating command-line list options with various configurations and constraints.
 *
 * This class provides a fluent API for defining list options that can collect multiple values,
 * including type refinements, constraints, environment variable support, and help text.
 */
class OptionListBuilder<T : Any> internal constructor(
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
) : CollectionOptionBuilder<T, List<T>>(
    switches, helpText, domainProps, requiresValue, single, booleanFlag,
    negationPrefix, positionalKind, positionalSeq, expectedDesc, hidden, isNonNullable, callback
) {

    override fun createCollection(values: List<T>): List<T> = values

    override fun getValueKind(): ValueKind = ValueKind.LIST

    // ---- List-specific fluent API ----

    /**
     * Marks this list option as required, equivalent to atLeast(1).
     */
    fun required(): OptionListBuilder<T> = atLeast(1)

    // ---- Validation methods ----

    /**
     * Adds a validator that checks each element in the list against the given predicate.
     *
     * @param message Template message for validation errors. Supports:
     *   - {option}: switch name
     *   - {value}: the value of the switch
     * @param predicate Function that returns true if the element is valid
     */
    override fun validate(
        message: String,
        predicate: (T) -> Boolean
    ): OptionListBuilder<T> = apply {
        super.validate(message, predicate)
    }

    /**
     * Adds multiple element validators at once.
     *
     * @param validations Pairs of (message template, predicate) for element validation
     */
    fun validate(vararg validations: Pair<String, (T) -> Boolean>): OptionListBuilder<T> = apply {
        validations.forEach { (message, predicate) ->
            validate(message, predicate)
        }
    }

    /**
     * Adds a validator that checks the entire list against the given predicate.
     *
     * @param message Template message for validation errors. Supports:
     *   - {option}: property name
     *   - {value}: the list (properly formatted)
     * @param predicate Function that returns true if the list is valid
     */
    override fun validateCollection(
        message: String,
        predicate: (List<T>) -> Boolean
    ): OptionListBuilder<T> = apply {
        super.validateCollection(message, predicate)
    }

    // Return types are covariant for List-specific fluent API

    /**
     * Restricts this list option to only be available in specific domains.
     * When specified, this option will only be recognized when one of the
     * referenced domains is active.
     *
     * @param refs Property references to domain properties that should allow this option
     * @return This builder for method chaining
     */
    override fun onlyInDomains(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.onlyInDomains(*refs) as OptionListBuilder<T>

    /**
     * Requires this list option to have at least the specified number of values.
     * This constraint is validated after parsing completes.
     *
     * @param n Minimum number of values required
     * @return This builder for method chaining
     */
    override fun atLeast(n: Int): OptionListBuilder<T> =
        super.atLeast(n) as OptionListBuilder<T>

    /**
     * Specifies that this list option conflicts with other options.
     * If this option is provided along with any of the conflicting options,
     * parsing will fail with an error.
     *
     * @param refs Property references to options that conflict with this one
     * @return This builder for method chaining
     */
    override fun conflictsWith(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.conflictsWith(*refs) as OptionListBuilder<T>

    /**
     * Makes this list option required if any of the referenced options are present.
     * This creates a conditional requirement where providing any of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    override fun requireIfAnyPresent(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.requireIfAnyPresent(*refs) as OptionListBuilder<T>

    /**
     * Makes this list option required if all of the referenced options are present.
     * This creates a conditional requirement where providing all of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    override fun requireIfAllPresent(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.requireIfAllPresent(*refs) as OptionListBuilder<T>

    override fun requireIfAnyAbsent(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.requireIfAnyAbsent(*refs) as OptionListBuilder<T>

    override fun requireIfAllAbsent(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.requireIfAllAbsent(*refs) as OptionListBuilder<T>

    override fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionListBuilder<T> =
        super.requireIfValue(ref, predicate) as OptionListBuilder<T>

    override fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): OptionListBuilder<T> =
        super.allowOnlyIfValue(ref, predicate) as OptionListBuilder<T>

    /**
     * Creates an "exactly one" constraint with other options.
     * Exactly one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the exclusive group
     * @return This builder for method chaining
     */
    override fun exactlyOneWith(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.exactlyOneWith(*refs) as OptionListBuilder<T>

    /**
     * Creates an "at most one" constraint with other options.
     * At most one option from this group (including this option) may be provided.
     *
     * @param refs Property references to other options in the mutually exclusive group
     * @return This builder for method chaining
     */
    override fun atMostOneWith(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.atMostOneWith(*refs) as OptionListBuilder<T>

    /**
     * Creates an "at least one" constraint with other options.
     * At least one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the required group
     * @return This builder for method chaining
     */
    override fun atLeastOneWith(vararg refs: KProperty<*>): OptionListBuilder<T> =
        super.atLeastOneWith(*refs) as OptionListBuilder<T>

    /**
     * Creates an arity option for lists where each invocation consumes exactly `n` values.
     *
     * For example:
     * ```
     * val coords by option("--coords").int().list().arity(2)
     * // Usage: --coords 1 2 --coords 3 4
     * // Result: [[1, 2], [3, 4]]
     * ```
     *
     * @param n Number of values each option invocation must consume
     * @return ArityListOptionBuilder for further configuration
     */
    fun arity(n: Int): ArityListOptionBuilder<T> {
        if (n <= 1) throw ConfigException("Arity must be at least 2 (got $n). Use regular list() for single values.")

        // Create a non-nullable single function for arity
        val nonNullableSingle = createNonNullableSingleForArity(single)

        return ArityListOptionBuilder(this, nonNullableSingle, n, emptyList(), emptyList())
    }
}

/**
 * Makes this boolean list option negatable with a custom prefix.
 * Creates an automatic negation option (e.g., --disable-colors for --colors with prefix "disable-").
 *
 * @param prefix The prefix to use for negation (default uses the Arguments class default "no-")
 */
fun OptionListBuilder<Boolean>.negatable(prefix: String? = null): OptionListBuilder<Boolean> {
    if (!booleanFlag) {
        throw ConfigException("negatable() can only be used on boolean list options created with .bool().list()")
    }
    if (prefix != null && prefix.trim().isEmpty()) {
        throw ConfigException("negatable prefix cannot be empty or whitespace-only")
    }

    return OptionListBuilder(
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
