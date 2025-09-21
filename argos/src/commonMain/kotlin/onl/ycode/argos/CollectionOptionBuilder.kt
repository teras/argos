/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.snapshot.PositionalKind
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Shared utility to create non-nullable single converter for arity options.
 * Converts nullable SingleResult<T?> to non-nullable SingleResult<T>.
 */
internal fun <T : Any> createNonNullableSingleForArity(single: (Raw) -> SingleResult<T?>): (Raw) -> SingleResult<T> = { raw ->
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

/**
 * Abstract base class for collection option builders (List and Set).
 *
 * This class provides shared functionality for both OptionListBuilder and OptionSetBuilder,
 * including common constructor parameters, constraint handling, and property delegation logic.
 */
abstract class CollectionOptionBuilder<T : Any, C : Collection<T>> internal constructor(
    internal val switches: List<String>,
    internal var helpText: String?,
    internal val domainProps: Set<String>?,
    internal val requiresValue: Boolean,
    internal val single: (Raw) -> SingleResult<T?>,
    internal val booleanFlag: Boolean,
    internal val negationPrefix: String?,
    internal val positionalKind: PositionalKind?,
    internal val positionalSeq: Int?,
    internal val expectedDesc: List<String>?,
    internal var hidden: Boolean = false,
    internal val isNonNullable: Boolean = false,
    internal var callback: ((T) -> Unit)? = null
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, C>> {

    internal val constraintHooks = mutableListOf<(Arguments, owner: String) -> Unit>()
    internal val validators = mutableListOf<Pair<String, (T) -> Boolean>>()
    internal val collectionValidators = mutableListOf<Pair<String, (C) -> Boolean>>()

    /**
     * Abstract method to create the appropriate collection type from a list of values.
     */
    internal abstract fun createCollection(values: List<T>): C

    /**
     * Abstract method to get the appropriate ValueKind for registration.
     */
    internal abstract fun getValueKind(): ValueKind

    /**
     * Provides the property delegate implementation for collection options.
     * This method is called automatically by Kotlin's property delegation system.
     * It registers the option with the Arguments instance and returns a delegate
     * that provides access to the parsed collection value.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property this delegate is assigned to
     * @return A ReadOnlyProperty that provides access to the parsed collection
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, C> {
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

        // Initialize value source - collections don't have defaults
        thisRef.valueSources[name] = ValueSource.MISSING

        thisRef.registerOption(
            ownerName = name,
            switches = regSwitches,
            repeatable = true,
            help = helpText,
            domainProps = domainProps,
            requiresValue = requiresValue,
            valueKind = getValueKind(),
            booleanFlag = booleanFlag,
            negationPrefix = actualNegationPrefix,
            positionalKind = positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = expectedDesc,
            eager = false,
            hidden = hidden,
            parseMany = { raws, baseSwitch, expected ->
                val out = mutableListOf<T>()
                var hasDefaultValues = false
                for (r in raws) {
                    when (val res = single(r)) {
                        is SingleResult.Missing -> {}
                        is SingleResult.Invalid -> {
                            return@registerOption ParseOutcome.Error(
                                invalidValueFragments(
                                    res.original ?: r.value,
                                    baseSwitch,
                                    r.argvIndex,
                                    expected
                                )
                            )
                        }

                        is SingleResult.Value -> {
                            res.value?.let { value ->
                                out += value
                                if (res.source == ValueSource.DEFAULT) {
                                    hasDefaultValues = true
                                }
                            }
                        }
                    }
                }
                // If we have default values but no user input, mark as DEFAULT
                if (hasDefaultValues && out.isNotEmpty()) {
                    thisRef.valueSources[name] = ValueSource.DEFAULT
                }

                ParseOutcome.Success(createCollection(out))
            },
            single = single,
            validators = validators.map { (msg, pred) ->
                msg to { value: Any? -> @Suppress("UNCHECKED_CAST") pred(value as T) }
            },
            collectionValidators = collectionValidators.map { (msg, pred) ->
                msg to { value: Any? -> @Suppress("UNCHECKED_CAST") pred(value as C) }
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
                value as C
            } catch (e: IllegalStateException) {
                throw UninitializedPropertyException(property.name)
            }
        }
    }

    // Shared constraint methods

    /**
     * Adds validation for individual elements in the collection.
     * The validation is applied to each element that is added to the collection.
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the element is valid
     * @return This builder for method chaining
     */
    open fun validate(message: String, predicate: (T) -> Boolean) = apply {
        validators.add(message to predicate)
    }

    /**
     * Adds a validator for the entire collection after all elements are parsed and validated.
     *
     * Collection validators run after all individual element validators succeed and operate on the
     * complete collection. This enables validation of collection-level properties like size, element
     * relationships, uniqueness patterns, or aggregate constraints. Multiple collection validators
     * can be added and all will be checked in order.
     *
     * **Examples**:
     * - Size validation: `.validateCollection("must have 1-3 entries") { it.size in 1..3 }`
     * - Uniqueness: `.validateCollection("must have unique keys") { it.map { kv -> kv.key }.distinct().size == it.size }`
     * - Relationship: `.validateCollection("ranges must not overlap") { /* check overlaps */ }`
     *
     * @param message Error message to display if the collection fails validation
     * @param predicate Function that receives the complete collection and returns true if valid
     * @return This builder for method chaining
     * @see validate For validating individual elements
     */
    open fun validateCollection(message: String, predicate: (C) -> Boolean) = apply {
        collectionValidators.add(message to predicate)
    }

    /**
     * Restricts this collection option to only be available in specific domains.
     * When specified, this option will only be recognized when one of the
     * referenced domains is active.
     *
     * @param refs Property references to domain properties that should allow this option
     * @return This builder for method chaining
     */
    open fun onlyInDomains(vararg refs: KProperty<*>) = run {
        constraintHooks.add { args, owner ->
            args.addRequiredMin(owner, 0, refs.map { it.name }.toSet())
        }
        this
    }

    /**
     * Requires this collection option to have at least the specified number of values.
     * This constraint is validated after parsing completes.
     *
     * @param n Minimum number of values required
     * @return This builder for method chaining
     */
    open fun atLeast(n: Int) = apply {
        addRequiredConstraint(constraintHooks, n)
    }

    /**
     * Specifies that this collection option conflicts with other options.
     * If this option is provided along with any of the conflicting options,
     * parsing will fail with an error.
     *
     * @param refs Property references to options that conflict with this one
     * @return This builder for method chaining
     */
    open fun conflictsWith(vararg refs: KProperty<*>) = apply {
        addConflictsConstraint(constraintHooks, refs)
    }

    /**
     * Makes this collection option required if any of the referenced options are present.
     * This creates a conditional requirement where providing any of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    open fun requireIfAnyPresent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ANY_PRESENT, refs)
    }

    /**
     * Makes this collection option required if all of the referenced options are present.
     * This creates a conditional requirement where providing all of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    open fun requireIfAllPresent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ALL_PRESENT, refs)
    }

    /**
     * Makes this collection option required if any of the referenced options are absent.
     * This creates a conditional requirement where missing any of the trigger
     * options makes this option mandatory.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     */
    open fun requireIfAnyAbsent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ANY_ABSENT, refs)
    }

    /**
     * Makes this collection option required if all of the referenced options are absent.
     * This creates a conditional requirement where all of the trigger options
     * being missing makes this option mandatory.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     */
    open fun requireIfAllAbsent(vararg refs: KProperty<*>) = apply {
        addConditionalConstraint(constraintHooks, CondKind.ALL_ABSENT, refs)
    }

    /**
     * Makes this collection option required when the specified property matches the given predicate.
     * This creates a conditional requirement where the referenced property's value determines
     * if this collection option is mandatory.
     *
     * @param ref Property reference to the option whose value triggers this requirement
     * @param predicate Function that returns true if this option should be required
     * @return This builder for method chaining
     */
    open fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean) = apply {
        addPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Allows this collection option only when the specified property matches the given predicate.
     * If this option is provided but the condition is false, an error is reported.
     *
     * @param ref Property reference to the option whose value determines if this option is allowed
     * @param predicate Function that returns true if this option should be allowed
     * @return This builder for method chaining
     */
    open fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean) = apply {
        addAllowOnlyIfPredicateConstraint(constraintHooks, ref.name) { any -> @Suppress("UNCHECKED_CAST") predicate(any as U?) }
    }

    /**
     * Creates an "exactly one" constraint with other options.
     * Exactly one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the exclusive group
     * @return This builder for method chaining
     */
    open fun exactlyOneWith(vararg refs: KProperty<*>) = apply {
        addGroupConstraint(constraintHooks, "exactly-one", refs)
    }

    /**
     * Creates an "at most one" constraint with other options.
     * At most one option from this group (including this option) may be provided.
     *
     * @param refs Property references to other options in the mutually exclusive group
     * @return This builder for method chaining
     */
    open fun atMostOneWith(vararg refs: KProperty<*>) = apply {
        addGroupConstraint(constraintHooks, "at-most-one", refs)
    }

    /**
     * Creates an "at least one" constraint with other options.
     * At least one option from this group (including this option) must be provided.
     *
     * @param refs Property references to other options in the required group
     * @return This builder for method chaining
     */
    open fun atLeastOneWith(vararg refs: KProperty<*>) = apply {
        addGroupConstraint(constraintHooks, "at-least-one", refs)
    }

    /**
     * Sets the help text for this collection option.
     * The help text is displayed when the user requests help information.
     */
    fun help(text: String) = apply {
        this.helpText = text
    }

    /**
     * Marks this collection option as hidden from help output.
     * Hidden options are not displayed in help text but remain functional.
     */
    fun hidden() = apply {
        this.hidden = true
    }

    /**
     * Registers a callback to be executed when this collection option's values are found during parsing.
     * The callback receives each converted value and is executed after all parsing and validation completes.
     * For collection options, the callback is called once for each element in the collection.
     */
    fun onValue(callback: (T) -> Unit) = apply {
        this.callback = callback
    }
}
