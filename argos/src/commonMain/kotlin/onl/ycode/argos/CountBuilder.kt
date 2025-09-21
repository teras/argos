/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Builder for creating command-line count options with various configurations and constraints.
 *
 * This class provides a fluent API for defining count options that track the number of times
 * an option appears on the command line, including constraints and help text.
 */
class CountBuilder internal constructor(
    internal val switches: List<String>,
    private val helpText: String?,
    private val domainProps: Set<String>?,
    private val requiresValue: Boolean = false,
    private val hidden: Boolean = false,
    private val isNonNullable: Boolean = false
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, Int>> {

    internal val constraintHooks = mutableListOf<(Arguments, owner: String) -> Unit>()
    internal val validators = mutableListOf<Pair<String, (Int) -> Boolean>>()

    /**
     * Provides the property delegate implementation for count options.
     * This method is called automatically by Kotlin's property delegation system.
     * It registers the count option with the Arguments instance and returns a delegate
     * that provides access to the count value (number of times the option was specified).
     *
     * @param thisRef The Arguments instance this count option belongs to
     * @param property The property this delegate is assigned to
     * @return A ReadOnlyProperty that provides access to the count value
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, Int> {
        val name = property.name
        val regSwitches = switches.ifEmpty { listOf(thisRef.defaultLongPrefix() + name.toKebab()) }

        // Initialize value source - count starts at 0 (no default)
        thisRef.valueSources[name] = ValueSource.MISSING

        thisRef.registerOption(
            ownerName = name,
            switches = regSwitches,
            repeatable = true,
            help = helpText,
            domainProps = domainProps,
            requiresValue = requiresValue,
            valueKind = ValueKind.COUNT,
            booleanFlag = false,
            booleanNegatable = false,
            negationPrefix = null,
            positionalKind = null,
            positionalSeq = null,
            envVar = null,
            expectedDesc = null,
            eager = false,
            hidden = hidden,
            parseMany = { raws, _, _ ->
            val count = raws.count { it.present }


            ParseOutcome.Success(count)
        },
            single = { raw ->
                if (raw.present) SingleResult.Value(1, ValueSource.USER)
                else SingleResult.Missing
            },
            validators = validators.map { (msg, pred) ->
                msg to { value: Any? -> pred(value as Int) }
            }
        )

        constraintHooks.forEach { it(thisRef, name) }

        return ReadOnlyProperty { _, property ->
            try {
                val value = thisRef.valueOf(name)
                if (value == null && isNonNullable) {
                    throw UninitializedPropertyException(property.name)
                }
                @Suppress("UNCHECKED_CAST")
                value as Int
            } catch (_: Exception) {
                throw UninitializedPropertyException(property.name)
            }
        }
    }

    // ---- Fluent constraint API (GLOBAL scope) ----

    /**
     * Marks this count option as required, equivalent to atLeast(1).
     */
    fun required(): CountBuilder = atLeast(1)

    /**
     * Requires at least n occurrences of this count option.
     */
    fun atLeast(n: Int) = apply {
        if (n <= 0) throw ConfigError("atLeast requires n >= 1 (got $n)")
        constraintHooks += { reg, owner -> reg.addRequiredMin(owner, n, scope = null) }
    }

    /**
     * Requires that this count option be present along with any of the specified properties.
     */
    fun requireIfAnyPresent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ANY_PRESENT, refs)
    }

    /**
     * Requires that this count option be present when all of the specified properties are present.
     */
    fun requireIfAllPresent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ALL_PRESENT, refs)
    }

    /**
     * Requires that this count option be present when any of the specified properties are absent.
     */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ANY_ABSENT, refs)
    }

    /**
     * Requires that this count option be present when all of the specified properties are absent.
     */
    fun requireIfAllAbsent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ALL_ABSENT, refs)
    }

    /**
     * Requires that this count option be present when the specified property matches the given predicate.
     */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean) = apply {
        addPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Allows this count option only when the specified property matches the given predicate.
     * If this option is provided but the condition is false, an error is reported.
     */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean) = apply {
        addAllowOnlyIfPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Ensures exactly one of this count option and the specified properties is provided.
     */
    fun exactlyOneWith(vararg refs: KProperty<*>) = apply {
        addGroupConstraint(constraintHooks, "exactly-one", refs)
    }

    /**
     * Ensures at most one of this count option and the specified properties is provided.
     */
    fun atMostOneWith(vararg refs: KProperty<*>) = apply {
        addGroupConstraint(constraintHooks, "at-most-one", refs)
    }

    /**
     * Ensures at least one of this count option and the specified properties is provided.
     */
    fun atLeastOneWith(vararg refs: KProperty<*>) = apply {
        addGroupConstraint(constraintHooks, "at-least-one", refs)
    }

    /**
     * Specifies that this count option conflicts with the specified properties.
     */
    fun conflictsWith(vararg refs: KProperty<*>) = apply {
        addConflictsConstraint(constraintHooks, refs)
    }

    // ---- Validation methods ----

    /**
     * Adds a validator that checks the count value against the given predicate.
     *
     * @param message Template message for validation errors. Supports:
     *   - {name}: property name
     *   - {value}: the invalid count value
     *   - {switches}: option switches (e.g. "-v|--verbose")
     * @param predicate Function that returns true if the count is valid
     */
    fun validate(
        message: String = "Invalid count for {name}: {value}",
        predicate: (Int) -> Boolean
    ): CountBuilder = apply {
        validators.add(message to predicate)
    }

    /**
     * Adds multiple validators at once.
     *
     * @param validations Pairs of (message template, predicate) for validation
     */
    fun validate(vararg validations: Pair<String, (Int) -> Boolean>): CountBuilder = apply {
        validators.addAll(validations)
    }

    // ---- Configuration methods ----

    /**
     * Sets help text for this count option.
     */
    fun help(text: String) = CountBuilder(switches, text, domainProps, requiresValue, hidden, isNonNullable)

    /**
     * Marks this count option as hidden from help output.
     */
    fun hidden() = CountBuilder(switches, helpText, domainProps, requiresValue, true, isNonNullable)

    /**
     * Restricts this count option to be available only in the specified domains.
     */
    fun onlyInDomains(vararg refs: KProperty<*>): CountBuilder {
        if (refs.isEmpty()) throw ConfigError("onlyInDomains requires at least 1 domain (got ${refs.size})")
        return CountBuilder(switches, helpText, refs.map { it.name }.toSet(), requiresValue, hidden)
    }

    /**
     * Controls whether this count option requires a value.
     *
     * When requires=true: Option must be followed by a value (e.g. --verbose 3)
     * When requires=false: Option works in flag mode, counting occurrences (e.g. -vvv = 3)
     */
    fun requiresValue(requires: Boolean): CountBuilder =
        CountBuilder(switches, helpText, domainProps, requires, hidden, isNonNullable)
}
