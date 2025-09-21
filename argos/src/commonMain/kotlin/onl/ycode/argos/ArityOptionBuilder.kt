/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.ArgosI18n.translate
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Helper function to handle the unified arity storage format.
 * Converts between List<List<T>> (internal storage) and List<T> (user-facing API).
 */
private fun <T> flattenArityValue(value: Any?): List<T>? {
    return when (value) {
        null -> null
        is List<*> -> {
            if (value.isNotEmpty() && value.first() is List<*>) {
                // Nested structure List<List<T>> - flatten to List<T>
                @Suppress("UNCHECKED_CAST")
                (value as List<List<T>>).flatten()
            } else {
                // Already flat List<T> (e.g., default values)
                @Suppress("UNCHECKED_CAST")
                value as List<T>
            }
        }

        else -> {
            // Fallback
            @Suppress("UNCHECKED_CAST")
            value as List<T>
        }
    }
}

/**
 * Common arity parsing logic shared between List and Set arity builders.
 * Groups raw values into sublists of exactly arityCount elements each.
 */
private fun <T, C : MutableCollection<List<T>>> parseArityGroups(
    raws: List<Raw>,
    arityCount: Int,
    originalSingle: (Raw) -> SingleResult<T>,
    name: String,
    valueSources: MutableMap<String, ValueSource>,
    baseSwitch: String,
    expected: List<String>?,
    validators: List<Pair<String, (List<T>) -> Boolean>>,
    collection: C,
    callback: ((List<T>) -> Unit)?
): ParseOutcome<C> {
    var currentSublist = mutableListOf<T>()

    for ((index, raw) in raws.withIndex()) {
        when (val res = originalSingle(raw)) {
            is SingleResult.Missing -> {} // Skip missing values
            is SingleResult.Value<*> -> {
                if (index == 0) {
                    valueSources[name] = res.source
                }
                val value = res.value
                if (value != null) {
                    @Suppress("UNCHECKED_CAST")
                    currentSublist.add(value as T)

                    // Complete sublist when we have enough elements
                    if (currentSublist.size == arityCount) {
                        val completedGroup = currentSublist.toList()

                        // Validate the completed arity group
                        for ((message, predicate) in validators) {
                            if (!predicate(completedGroup)) {
                                return ParseOutcome.Error(
                                    translate(message)
                                        .replace("{option}", name)
                                        .replace("{value}", completedGroup.toString())
                                )
                            }
                        }

                        collection.add(completedGroup)
                        // Trigger callback for the completed arity group
                        callback?.invoke(completedGroup)
                        currentSublist = mutableListOf() // Reset for next group
                    }
                }
            }

            is SingleResult.Invalid -> {
                return ParseOutcome.Error(
                    invalidValueFragments(
                        res.original ?: raw.value,
                        baseSwitch,
                        raw.argvIndex,
                        expected
                    )
                )
            }
        }
    }

    return ParseOutcome.Success(collection)
}

/**
 * Builder for scalar arity-based options that consume exactly N values per invocation.
 *
 * This class is created when calling `.arity(n)` on an option builder. Each time the option
 * appears on the command line, it consumes exactly N values and produces a `List<T>` containing
 * those values. The final result type is `List<T>?` (nullable) since the option may not be provided.
 *
 * **Usage Examples:**
 * ```kotlin
 * // Single 2D coordinate: --point 10 20
 * val point by option("--point").int().arity(2)  // List<Int>? = [10, 20]
 *
 * // Required 3D point: --coords 1 2 3
 * val coords by option("--coords").int().arity(3).required()  // List<Int> = [1, 2, 3]
 *
 * // With default: --color 255 128 0
 * val color by option("--color").int().arity(3).default(listOf(0, 0, 0))  // List<Int>
 * ```
 *
 * **For collecting multiple groups**, use `.list()` or `.set()` to get [ArityListOptionBuilder] or [AritySetOptionBuilder]:
 * ```kotlin
 * // Multiple line segments: --line 0 0 --line 10 10 --line 20 20
 * val lines by option("--line").int().list().arity(2)  // List<List<Int>>
 * ```
 *
 * @param T The element type of the values in the returned list
 * @see ArityListOptionBuilder For collecting multiple arity groups in a list
 * @see AritySetOptionBuilder For collecting unique arity groups in a set
 * @see OptionBuilder.arity For creating arity options
 */
class ArityOptionBuilder<T> internal constructor(
    internal val delegate: OptionBuilder<List<T>?>,
    private val originalSingle: (Raw) -> SingleResult<T>,  // Original single converter
    private val arityCount: Int,
    private val validators: List<Pair<String, (List<T>) -> Boolean>> = emptyList(),
    internal val defaultValue: List<T>? = null  // Default value for arity options
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, List<T>?>> {

    /**
     * Provides the property delegate implementation for arity options.
     * Registers the option with the Arguments instance and returns a delegate that provides
     * access to the parsed list value.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property being delegated
     * @return A ReadOnlyProperty that returns List<T>? when accessed
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, List<T>?> {

        val name = property.name
        val seq = delegate.positionalSeq ?: if (delegate.positionalKind != null) thisRef.nextPosSeq() else null

        val regSwitches =
            if (delegate.switches.isEmpty() && delegate.positionalKind == null)
                listOf(thisRef.defaultLongPrefix() + name.toKebab())
            else delegate.switches

        // Resolve the actual negation prefix
        val actualNegationPrefix = if (delegate.negationPrefix == "") {
            thisRef.getDefaultNegationPrefix()  // Use default when empty string marker present
        } else {
            delegate.negationPrefix  // Use explicit prefix or null (not negatable)
        }

        // Initialize value source based on whether this option has a default
        thisRef.valueSources[name] =
            if (delegate.isNonNullable || defaultValue != null) ValueSource.DEFAULT else ValueSource.MISSING

        thisRef.registerOption(
            ownerName = name,
            switches = regSwitches,
            repeatable = true,  // UNIFIED: All arity options are repeatable like collections
            help = delegate.helpText,
            domainProps = delegate.domainProps,
            requiresValue = delegate.requiresValue,
            valueKind = ValueKind.LIST,  // UNIFIED: All arity options use LIST storage
            arity = arityCount,  // Pass the arity count!
            booleanFlag = delegate.booleanFlag,
            negationPrefix = actualNegationPrefix,
            positionalKind = delegate.positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = delegate.expectedDesc,
            eager = delegate.eager,
            hidden = delegate.hidden,
            defaultValue = delegate.defaultValue,
            parseMany = { raws, baseSwitch, expected ->
                // UNIFIED: Use same logic as collection arity, but flatten result for scalar
                if (raws.isEmpty()) {
                    if (defaultValue != null) {
                        thisRef.valueSources[name] = ValueSource.DEFAULT
                        // For scalar arity with default, wrap in list-of-lists format
                        ParseOutcome.Success(listOf(defaultValue))
                    } else {
                        ParseOutcome.Success(null)
                    }
                } else {
                    // Group raws by invocation (every arityCount values forms one sublist)
                    val sublists = mutableListOf<List<T>>()
                    var currentSublist = mutableListOf<T>()

                    for ((index, raw) in raws.withIndex()) {
                        when (val res = originalSingle(raw)) {
                            is SingleResult.Missing -> {} // Skip missing values
                            is SingleResult.Value<*> -> {
                                if (index == 0) {
                                    thisRef.valueSources[name] = res.source
                                }
                                val value = res.value
                                if (value != null) {
                                    @Suppress("UNCHECKED_CAST")
                                    currentSublist.add(value as T)

                                    // Complete sublist when we have enough elements
                                    if (currentSublist.size == arityCount) {
                                        val completedGroup = currentSublist.toList()
                                        sublists.add(completedGroup)
                                        // Note: Scalar arity callback handling TBD
                                        currentSublist = mutableListOf() // Reset for next group
                                    }
                                }
                            }

                            is SingleResult.Invalid -> {
                                return@registerOption ParseOutcome.Error(
                                    invalidValueFragments(
                                        res.original ?: raw.value,
                                        baseSwitch,
                                        raw.argvIndex,
                                        expected
                                    )
                                )
                            }
                        }
                    }

                    // Check for incomplete group
                    if (currentSublist.isNotEmpty()) {
                        // Incomplete arity group - will be caught by validation
                        ParseOutcome.Success(null)
                    } else {
                        // Return sublists in same format as collection arity
                        ParseOutcome.Success(sublists)
                    }
                }
            },
            single = { raw ->
                // For arity options, if no value is provided (Raw(false, null, null)),
                // check for default value first, then return Missing for constraint validation
                if (!raw.present) {
                    if (defaultValue != null) {
                        SingleResult.Value(defaultValue, ValueSource.DEFAULT)
                    } else {
                        SingleResult.Missing
                    }
                } else {
                    // If a value is provided via single path, delegate to original
                    delegate.single(raw)
                }
            },
            validators = emptyList(),
            collectionValidators = validators.map { (message, predicate) ->
                message to { value: Any? ->
                    @Suppress("UNCHECKED_CAST")
                    val list = value as? List<T>
                    list?.let(predicate) == true
                }
            }
        )

        // Execute constraint hooks from the delegate
        delegate.constraintHooks.forEach { it(thisRef, name) }

        // Register callback if the delegate has one
        delegate.callback?.let { cb ->
            thisRef.registerCallback(name) { value ->
                cb(flattenArityValue<T>(value))
            }
        }

        return ReadOnlyProperty { thisRef, property ->
            try {
                val value = thisRef.valueOf(name)
                flattenArityValue<T>(value)
            } catch (e: Exception) {
                throw UninitializedPropertyException(property.name)
            }
        }
    }

    /**
     * Marks this arity option as required, making it non-nullable.
     * Returns a NonNullableArityOptionBuilder that preserves arity functionality.
     */
    fun required(): NonNullableArityOptionBuilder<T> {
        // Apply required constraint to the delegate's constraint hooks
        addRequiredConstraint(delegate.constraintHooks, 1)
        return NonNullableArityOptionBuilder(
            ArityOptionBuilder(
                delegate,
                originalSingle,
                arityCount,
                validators,
                defaultValue
            )
        )
    }

    /**
     * Marks this arity option as required with a minimum number of occurrences.
     * For arity options, atLeast(n) implies required().
     */
    fun atLeast(n: Int): NonNullableArityOptionBuilder<T> {
        if (n < 1) throw ConfigException("atLeast must require at least 1 occurrence")
        // Apply atLeast constraint to the delegate's constraint hooks for collection arity
        delegate.constraintHooks += { reg, owner -> reg.addRequiredMin(owner, n, scope = null) }
        return NonNullableArityOptionBuilder(
            ArityOptionBuilder(
                delegate,
                originalSingle,
                arityCount,
                validators,
                defaultValue
            )
        )
    }

    /**
     * Sets a default value for this arity option, making it non-nullable.
     * The default value should be a List<T> with exactly arityCount elements.
     */
    fun default(value: List<T>): NonNullableArityOptionBuilder<T> {
        if (value.size != arityCount) {
            throw ConfigException("default value for arity($arityCount) must contain exactly $arityCount elements (got ${value.size})")
        }

        // UNIFIED: Create a modified copy with the default value
        // This ensures the unified format is preserved with the default value set
        val copyWithDefault = ArityOptionBuilder(delegate, originalSingle, arityCount, validators, value)
        return NonNullableArityOptionBuilder(copyWithDefault)
    }

    /**
     * Sets the help text for this arity option.
     *
     * @param text Help text to display in usage information
     * @return This builder for method chaining
     */
    fun help(text: String): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.help(text), originalSingle, arityCount, validators, defaultValue)

    /**
     * Marks this arity option as hidden from help output.
     *
     * @return This builder for method chaining
     */
    fun hidden(): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.hidden(), originalSingle, arityCount, validators, defaultValue)

    /**
     * Specifies that this arity option conflicts with other options.
     *
     * @param refs Property references to conflicting options
     * @return This builder for method chaining
     */
    fun conflictsWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.conflictsWith(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Makes this arity option required if any of the referenced options are present.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAnyPresent(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Makes this arity option required if all of the referenced options are present.
     *
     * @param refs Property references to options that trigger this requirement
     * @return This builder for method chaining
     */
    fun requireIfAllPresent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAllPresent(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Makes this arity option required if any of the referenced options are absent.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAnyAbsent(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Makes this arity option required if all of the referenced options are absent.
     *
     * @param refs Property references to options whose absence triggers this requirement
     * @return This builder for method chaining
     */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAllAbsent(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Makes this arity option required if the referenced option's value matches the predicate.
     *
     * @param ref Property reference to the option whose value is checked
     * @param predicate Function that returns true if this option should be required
     * @return This builder for method chaining
     */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityOptionBuilder<T> =
        ArityOptionBuilder(
            delegate.requireIfValue(ref, predicate),
            originalSingle,
            arityCount,
            validators,
            defaultValue
        )

    /**
     * Allows this arity option only when the referenced option's value matches the predicate.
     *
     * @param ref Property reference to the option whose value is checked
     * @param predicate Function that returns true if this option is allowed
     * @return This builder for method chaining
     */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityOptionBuilder<T> =
        ArityOptionBuilder(
            delegate.allowOnlyIfValue(ref, predicate),
            originalSingle,
            arityCount,
            validators,
            defaultValue
        )

    /**
     * Creates an "exactly one" constraint with other options.
     *
     * @param refs Property references to other options in the exclusive group
     * @return This builder for method chaining
     */
    fun exactlyOneWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.exactlyOneWith(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Creates an "at most one" constraint with other options.
     *
     * @param refs Property references to other options in the mutually exclusive group
     * @return This builder for method chaining
     */
    fun atMostOneWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.atMostOneWith(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Creates an "at least one" constraint with other options.
     *
     * @param refs Property references to other options in the required group
     * @return This builder for method chaining
     */
    fun atLeastOneWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.atLeastOneWith(*refs), originalSingle, arityCount, validators, defaultValue)

    /**
     * Adds validation for each arity group parsed from the command line.
     * The validation is applied to each List<T> group individually.
     *
     * @param message Error message template. Supports placeholders: {option}, {value}
     * @param predicate Function that returns true if the arity group is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid arity group for {option}: {value}",
        predicate: (List<T>) -> Boolean
    ): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate, originalSingle, arityCount, validators + (message to predicate), defaultValue)


    /**
     * Registers a callback to be executed when this arity option receives a value.
     * The callback receives List<T>? - null if the option was not provided, List<T> if it was.
     *
     * @param callback Function to execute with the parsed list value
     * @return This builder for method chaining
     */
    fun onValue(callback: (List<T>?) -> Unit): ArityOptionBuilder<T> =
        ArityOptionBuilder(
            delegate.onValue { value -> callback(flattenArityValue(value)) },
            originalSingle,
            arityCount
        )
}

/**
 * Builder for arity-based options that return non-nullable lists of values.
 *
 * This class is created when calling `.required()` or `.atLeast(n)` on an ArityOptionBuilder.
 * Returns `List<T>` (non-nullable) and preserves all arity functionality.
 * Follows the same pattern as NonNullableOptionBuilder.
 *
 * @param T The element type of the values in the returned list
 */
class NonNullableArityOptionBuilder<T> internal constructor(
    private val delegate: ArityOptionBuilder<T>
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, List<T>>> {

    /**
     * Provides the property delegate implementation for non-nullable arity options.
     * Delegates to the underlying ArityOptionBuilder and ensures the result is non-null.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property being delegated
     * @return A ReadOnlyProperty that returns non-null List<T> when accessed
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, List<T>> {
        val nullableProperty = delegate.provideDelegate(thisRef, property)
        return ReadOnlyProperty { args, prop ->
            val nullableValue = nullableProperty.getValue(args, prop)
            // Non-nullable builder should always have a value (either parsed or default)
            if (nullableValue == null) {
                // Check if the delegate has a defaultValue that wasn't properly handled during parsing
                if (delegate.defaultValue != null) {
                    // For arity options, the default should have been wrapped in the unified format during parsing
                    // If we reach here, it means parsing didn't set a value but we have a default
                    // Return the default value directly (it's already the correct type List<T>)
                    return@ReadOnlyProperty delegate.defaultValue!!
                } else {
                    throw UninitializedPropertyException("Property ${prop.name} was not initialized and has no default value")
                }
            }

            // UNIFIED: Handle values that may need flattening (parsed values in List<List<T>> format)
            val flattened = flattenArityValue<T>(nullableValue)
            flattened ?: throw UninitializedPropertyException("Failed to flatten arity value for property ${prop.name}")
        }
    }

    /** Sets help text for this arity option. */
    fun help(text: String): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.help(text))

    /** Marks this arity option as hidden from help output. */
    fun hidden(): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.hidden())

    /** Specifies that this arity option conflicts with other options. */
    fun conflictsWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.conflictsWith(*refs))

    /** Makes this arity option required if any of the referenced options are present. */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAnyPresent(*refs))

    /** Makes this arity option required if all of the referenced options are present. */
    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAllPresent(*refs))

    /** Makes this arity option required if any of the referenced options are absent. */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    /** Makes this arity option required if all of the referenced options are absent. */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAllAbsent(*refs))

    /** Makes this arity option required if the referenced option's value matches the predicate. */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfValue(ref, predicate))

    /** Allows this arity option only when the referenced option's value matches the predicate. */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    /** Creates an "exactly one" constraint with other options. */
    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.exactlyOneWith(*refs))

    /** Creates an "at most one" constraint with other options. */
    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.atMostOneWith(*refs))

    /** Creates an "at least one" constraint with other options. */
    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.atLeastOneWith(*refs))

    /**
     * Adds validation for each arity group. The predicate receives each List<T> group individually.
     *
     * @param message Error message template
     * @param predicate Function that returns true if the arity group is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid arity group for {option}: {value}",
        predicate: (List<T>) -> Boolean
    ): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.validate(message, predicate))

    /**
     * Registers a callback to be executed when this required arity option receives a value.
     * The callback receives List<T> (non-nullable) since this is a required option.
     *
     * @param callback Function to execute with the parsed list value
     * @return This builder for method chaining
     */
    fun onValue(callback: (List<T>) -> Unit): NonNullableArityOptionBuilder<T> {
        // For required arity options, we need to adapt the nullable callback to non-nullable
        return NonNullableArityOptionBuilder(delegate.onValue { value ->
            if (value != null) {
                callback(value)
            }
        })
    }
}

/**
 * Builder for collecting multiple arity groups in a list with order preservation and duplicate support.
 *
 * This class is created when calling `.arity(n)` on a list option builder (`.list().arity(n)`).
 * Each time the option appears on the command line, it consumes exactly N values to create a sublist,
 * and all sublists are collected in a list maintaining order. The final result type is `List<List<T>>?`
 * (nullable when the option is never provided).
 *
 * **Usage Examples:**
 * ```kotlin
 * // Collect multiple line segments: --line 0 0 --line 10 10 --line 20 20
 * val lines by option("--line").int().list().arity(2)
 * // Result: [[0, 0], [10, 10], [20, 20]] or null
 *
 * // Required with minimum: --rect 0 0 100 100 --rect 50 50 150 150
 * val rectangles by option("--rect").int().list().arity(4).atLeast(1)
 * // Result: [[0, 0, 100, 100], [50, 50, 150, 150]]
 *
 * // Validate each group: --range 1 10 --range 20 30
 * val ranges by option("--range").int().list().arity(2)
 *     .validate("range must be valid (start < end)") { it[0] < it[1] }
 * // Result: [[1, 10], [20, 30]]
 * ```
 *
 * @param T The element type of the values in the sublists
 * @see AritySetOptionBuilder For collecting unique arity groups
 * @see ArityOptionBuilder For scalar arity (single group)
 */
class ArityListOptionBuilder<T : Any> internal constructor(
    internal val delegate: OptionListBuilder<T>,
    private val originalSingle: (Raw) -> SingleResult<T>,
    private val arityCount: Int,
    private val validators: List<Pair<String, (List<T>) -> Boolean>> = emptyList(),
    private val collectionValidators: List<Pair<String, (List<List<T>>?) -> Boolean>> = emptyList(),
    private val callback: ((List<T>) -> Unit)? = null,
    internal val defaultValue: List<List<T>>? = null
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, List<List<T>>?>> {

    /**
     * Provides the property delegate implementation for list arity options.
     * Registers the option with the Arguments instance and returns a delegate.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property being delegated
     * @return A ReadOnlyProperty that returns List<List<T>>? when accessed
     */
    override fun provideDelegate(
        thisRef: Arguments,
        property: KProperty<*>
    ): ReadOnlyProperty<Arguments, List<List<T>>?> {
        val name = property.name
        val seq = delegate.positionalSeq ?: if (delegate.positionalKind != null) thisRef.nextPosSeq() else null

        val regSwitches =
            if (delegate.switches.isEmpty() && delegate.positionalKind == null)
                listOf(thisRef.defaultLongPrefix() + name.toKebab())
            else delegate.switches

        // Initialize value source based on whether this option has a default
        thisRef.valueSources[name] = if (defaultValue != null) ValueSource.DEFAULT else ValueSource.MISSING

        thisRef.registerOption(
            ownerName = name,
            switches = regSwitches,
            repeatable = true,  // List options are repeatable
            help = delegate.helpText,
            domainProps = delegate.domainProps,
            requiresValue = delegate.requiresValue,
            valueKind = ValueKind.LIST,
            arity = arityCount,
            booleanFlag = delegate.booleanFlag,
                        negationPrefix = null,
            positionalKind = delegate.positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = delegate.expectedDesc,
            eager = false,
            hidden = delegate.hidden,
            defaultValue = null,
            parseMany = { raws, baseSwitch, expected ->
                if (raws.isEmpty()) {
                    if (defaultValue != null) {
                        thisRef.valueSources[name] = ValueSource.DEFAULT
                        ParseOutcome.Success(defaultValue)
                    } else {
                        ParseOutcome.Success(null)
                    }
                } else {
                    val sublists = mutableListOf<List<T>>()
                    when (val result = parseArityGroups(
                        raws,
                        arityCount,
                        originalSingle,
                        name,
                        thisRef.valueSources,
                        baseSwitch,
                        expected,
                        validators,
                        sublists,
                        callback
                    )) {
                        is ParseOutcome.Success -> {
                            // Validate the entire collection
                            for ((message, predicate) in collectionValidators) {
                                if (!predicate(result.value)) {
                                    return@registerOption ParseOutcome.Error(
                                        translate(message)
                                            .replace("{option}", name.asOption())
                                            .replace("{value}", result.value.toString().asValue())
                                    )
                                }
                            }
                            result
                        }

                        is ParseOutcome.Error -> result
                    }
                }
            },
            single = { raw ->
                // For arity list options, if no value is provided, return Missing
                if (!raw.present) {
                    SingleResult.Missing
                } else {
                    delegate.single(raw)
                }
            }
        )

        // Execute constraint hooks from the delegate
        delegate.constraintHooks.forEach { it(thisRef, name) }

        // Note: Callback is triggered during parsing when each arity group is completed

        return ReadOnlyProperty { thisRef, property ->
            try {
                val value = thisRef.valueOf(name)
                @Suppress("UNCHECKED_CAST")
                value as List<List<T>>?
            } catch (e: Exception) {
                throw UninitializedPropertyException(property.name)
            }
        }
    }

    /**
     * Registers a callback to be invoked when this list arity option receives values.
     * The callback receives the complete List<List<T>>? value.
     */
    fun onValue(callback: (List<T>) -> Unit): ArityListOptionBuilder<T> {
        return ArityListOptionBuilder(delegate, originalSingle, arityCount, validators, collectionValidators, callback)
    }

    /**
     * Marks this list arity option as required (at least one arity group must be provided).
     * This is equivalent to calling atLeast(1).
     *
     * @return NonNullableArityListOptionBuilder for method chaining
     */
    fun required(): NonNullableArityListOptionBuilder<T> {
        // For collection arity, required() is equivalent to atLeast(1)
        return atLeast(1)
    }

    /**
     * Requires at least n arity groups to be provided for this list arity option.
     *
     * @param n Minimum number of arity groups required (must be >= 1)
     * @return NonNullableArityListOptionBuilder for method chaining
     * @throws ConfigException if n < 1
     */
    fun atLeast(n: Int): NonNullableArityListOptionBuilder<T> {
        if (n < 1) throw ConfigException("atLeast must require at least 1 occurrence")
        // Apply atLeast constraint to the delegate using the standard pattern
        addRequiredConstraint(delegate.constraintHooks, n)
        return NonNullableArityListOptionBuilder(this)
    }

    /**
     * Sets a default value for this list arity option, making it non-nullable.
     * The default value should be a List<List<T>> where each sublist has exactly arityCount elements.
     */
    fun default(value: List<List<T>>): NonNullableArityListOptionBuilder<T> {
        // Validate that each sublist has the correct arity
        for ((index, sublist) in value.withIndex()) {
            if (sublist.size != arityCount) {
                throw ConfigException("default value sublist at index $index for arity($arityCount) must contain exactly $arityCount elements (got ${sublist.size})")
            }
        }

        val copyWithDefault = ArityListOptionBuilder(
            delegate,
            originalSingle,
            arityCount,
            validators,
            collectionValidators,
            callback,
            value
        )
        return NonNullableArityListOptionBuilder(copyWithDefault)
    }

    /** Sets help text for this list arity option. */
    fun help(text: String): ArityListOptionBuilder<T> = this
    /** Marks this list arity option as hidden from help output. */
    fun hidden(): ArityListOptionBuilder<T> = this
    /** Specifies that this list arity option conflicts with other options. */
    fun conflictsWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Makes this list arity option required if any of the referenced options are present. */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Makes this list arity option required if all of the referenced options are present. */
    fun requireIfAllPresent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Makes this list arity option required if any of the referenced options are absent. */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Makes this list arity option required if all of the referenced options are absent. */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Makes this list arity option required if the referenced option's value matches the predicate. */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityListOptionBuilder<T> = this
    /** Allows this list arity option only when the referenced option's value matches the predicate. */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityListOptionBuilder<T> = this
    /** Creates an "exactly one" constraint with other options. */
    fun exactlyOneWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Creates an "at most one" constraint with other options. */
    fun atMostOneWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    /** Creates an "at least one" constraint with other options. */
    fun atLeastOneWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this

    /**
     * Adds validation for each arity group in the list.
     *
     * @param message Error message template
     * @param predicate Function that returns true if the arity group is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid arity group for {option}: {value}",
        predicate: (List<T>) -> Boolean
    ): ArityListOptionBuilder<T> =
        ArityListOptionBuilder(
            delegate,
            originalSingle,
            arityCount,
            validators + (message to predicate),
            collectionValidators,
            callback,
            defaultValue
        )

    /**
     * Validates the entire collection of arity groups for this list option.
     * The predicate receives the complete List<List<T>>? value.
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the collection is valid
     */
    fun validateCollection(
        message: String = "Invalid collection for {option}: {value}",
        predicate: (List<List<T>>?) -> Boolean
    ): ArityListOptionBuilder<T> =
        ArityListOptionBuilder(
            delegate,
            originalSingle,
            arityCount,
            validators,
            collectionValidators + (message to predicate),
            callback,
            defaultValue
        )

}

/**
 * Builder for arity-based list options that return non-nullable lists of lists.
 *
 * This class is created when calling `.required()` or `.atLeast(n)` on an ArityListOptionBuilder.
 * Returns `List<List<T>>` (non-nullable) and preserves all arity functionality.
 *
 * @param T The element type of the values in the sublists
 */
class NonNullableArityListOptionBuilder<T : Any> internal constructor(
    private val delegate: ArityListOptionBuilder<T>
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, List<List<T>>>> {

    /**
     * Provides the property delegate implementation for non-nullable list arity options.
     * Delegates to the underlying ArityListOptionBuilder and ensures the result is non-null.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property being delegated
     * @return A ReadOnlyProperty that returns non-null List<List<T>> when accessed
     */
    override fun provideDelegate(
        thisRef: Arguments,
        property: KProperty<*>
    ): ReadOnlyProperty<Arguments, List<List<T>>> {
        val nullableProperty = delegate.provideDelegate(thisRef, property)
        return ReadOnlyProperty { args, prop ->
            val nullableValue = nullableProperty.getValue(args, prop)
            if (nullableValue == null) {
                // Check if the delegate has a defaultValue that should be returned
                if (delegate.defaultValue != null) {
                    return@ReadOnlyProperty delegate.defaultValue!!
                } else {
                    return@ReadOnlyProperty emptyList()
                }
            }
            nullableValue
        }
    }

    /** Sets help text for this list arity option. */
    fun help(text: String): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.help(text))

    /** Marks this list arity option as hidden from help output. */
    fun hidden(): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.hidden())

    /** Specifies that this list arity option conflicts with other options. */
    fun conflictsWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.conflictsWith(*refs))

    /** Makes this list arity option required if any of the referenced options are present. */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAnyPresent(*refs))

    /** Makes this list arity option required if all of the referenced options are present. */
    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAllPresent(*refs))

    /** Makes this list arity option required if any of the referenced options are absent. */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    /** Makes this list arity option required if all of the referenced options are absent. */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAllAbsent(*refs))

    /** Makes this list arity option required if the referenced option's value matches the predicate. */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfValue(ref, predicate))

    /** Allows this list arity option only when the referenced option's value matches the predicate. */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    /** Creates an "exactly one" constraint with other options. */
    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.exactlyOneWith(*refs))

    /** Creates an "at most one" constraint with other options. */
    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.atMostOneWith(*refs))

    /** Creates an "at least one" constraint with other options. */
    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.atLeastOneWith(*refs))

    /**
     * Adds validation for each arity group. The predicate receives each List<T> group individually.
     *
     * @param message Error message template
     * @param predicate Function that returns true if the arity group is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid arity group for {option}: {value}",
        predicate: (List<T>) -> Boolean
    ): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.validate(message, predicate))

    /**
     * Validates the entire collection of arity groups for this non-nullable list option.
     * The predicate receives the complete List<List<T>> value (never null).
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the collection is valid
     */
    fun validateCollection(
        message: String = "Invalid collection for {option}: {value}",
        predicate: (List<List<T>>) -> Boolean
    ): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.validateCollection(message) { list ->
            list?.let(predicate) ?: false
        })

}

/**
 * Builder for collecting unique arity groups in a set with automatic deduplication.
 *
 * This class is created when calling `.arity(n)` on a set option builder (`.set().arity(n)`).
 * Each time the option appears on the command line, it consumes exactly N values to create a sublist,
 * and all unique sublists are collected in a set (duplicates are automatically filtered). The set
 * preserves insertion order using LinkedHashSet. The final result type is `Set<List<T>>?`
 * (nullable when the option is never provided).
 *
 * **Usage Examples:**
 * ```kotlin
 * // Collect unique triangles: --tri 0 0 1 --tri 1 1 2 --tri 0 0 1 (duplicate ignored)
 * val triangles by option("--tri").float().set().arity(3)
 * // Result: {[0.0, 0.0, 1.0], [1.0, 1.0, 2.0]} or null
 *
 * // Required unique edges: --edge 1 2 --edge 2 3 --edge 1 2 (duplicate ignored)
 * val edges by option("--edge").int().set().arity(2).required()
 * // Result: {[1, 2], [2, 3]}
 *
 * // Validate uniqueness constraint
 * val points by option("--point").int().set().arity(2)
 *     .validateCollection("must have at least 3 unique points") { it.size >= 3 }
 * ```
 *
 * @param T The element type of the values in the sublists
 * @see ArityListOptionBuilder For collecting groups with duplicates allowed
 * @see ArityOptionBuilder For scalar arity (single group)
 */
class AritySetOptionBuilder<T : Any> internal constructor(
    internal val delegate: OptionSetBuilder<T>,
    private val originalSingle: (Raw) -> SingleResult<T>,
    private val arityCount: Int,
    private val validators: List<Pair<String, (List<T>) -> Boolean>> = emptyList(),
    private val collectionValidators: List<Pair<String, (Set<List<T>>?) -> Boolean>> = emptyList(),
    private val callback: ((List<T>) -> Unit)? = null,
    internal val defaultValue: Set<List<T>>? = null
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, Set<List<T>>?>> {

    /**
     * Provides the property delegate implementation for set arity options.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property being delegated
     * @return A ReadOnlyProperty that returns Set<List<T>>? when accessed
     */
    override fun provideDelegate(
        thisRef: Arguments,
        property: KProperty<*>
    ): ReadOnlyProperty<Arguments, Set<List<T>>?> {
        val name = property.name
        val seq = delegate.positionalSeq ?: if (delegate.positionalKind != null) thisRef.nextPosSeq() else null

        val regSwitches =
            if (delegate.switches.isEmpty() && delegate.positionalKind == null)
                listOf(thisRef.defaultLongPrefix() + name.toKebab())
            else delegate.switches

        // Initialize value source based on whether this option has a default
        thisRef.valueSources[name] = if (defaultValue != null) ValueSource.DEFAULT else ValueSource.MISSING

        thisRef.registerOption(
            ownerName = name,
            switches = regSwitches,
            repeatable = true,  // Set options are repeatable
            help = delegate.helpText,
            domainProps = delegate.domainProps,
            requiresValue = delegate.requiresValue,
            valueKind = ValueKind.SET,
            arity = arityCount,
            booleanFlag = delegate.booleanFlag,
                        negationPrefix = null,
            positionalKind = delegate.positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = delegate.expectedDesc,
            eager = false,
            hidden = delegate.hidden,
            defaultValue = null,
            parseMany = { raws, baseSwitch, expected ->
                if (raws.isEmpty()) {
                    if (defaultValue != null) {
                        thisRef.valueSources[name] = ValueSource.DEFAULT
                        ParseOutcome.Success(defaultValue)
                    } else {
                        ParseOutcome.Success(null)
                    }
                } else {
                    val sublists = mutableSetOf<List<T>>()
                    when (val result = parseArityGroups(
                        raws,
                        arityCount,
                        originalSingle,
                        name,
                        thisRef.valueSources,
                        baseSwitch,
                        expected,
                        validators,
                        sublists,
                        callback
                    )) {
                        is ParseOutcome.Success -> {
                            // Validate the entire collection
                            for ((message, predicate) in collectionValidators) {
                                if (!predicate(result.value)) {
                                    return@registerOption ParseOutcome.Error(
                                        translate(message)
                                            .replace("{option}", name.asOption())
                                            .replace("{value}", result.value.toString().asValue())
                                    )
                                }
                            }
                            result
                        }

                        is ParseOutcome.Error -> result
                    }
                }
            },
            single = { raw ->
                // For arity set options, if no value is provided, return Missing
                if (!raw.present) {
                    SingleResult.Missing
                } else {
                    delegate.single(raw)
                }
            }
        )

        // Execute constraint hooks from the delegate
        delegate.constraintHooks.forEach { it(thisRef, name) }

        // Register callback if the delegate has one
        delegate.callback?.let { cb ->
            thisRef.registerCallback(name) { value ->
                @Suppress("UNCHECKED_CAST")
                val set = value as Set<List<T>>?
                // For arity set options, callback is triggered for each sublist during parsing
                // The full set callback isn't needed here since individual callbacks are triggered in parseMany
            }
        }

        // Note: Callback is triggered during parsing when each arity group is completed

        return ReadOnlyProperty { thisRef, property ->
            try {
                val value = thisRef.valueOf(name)
                @Suppress("UNCHECKED_CAST")
                value as Set<List<T>>?
            } catch (e: Exception) {
                throw UninitializedPropertyException(property.name)
            }
        }
    }

    /**
     * Registers a callback to be invoked when this set arity option receives values.
     * The callback receives the complete Set<List<T>>? value.
     */
    fun onValue(callback: (List<T>) -> Unit): AritySetOptionBuilder<T> {
        return AritySetOptionBuilder(delegate, originalSingle, arityCount, validators, collectionValidators, callback)
    }

    /**
     * Marks this set arity option as required (at least one arity group must be provided).
     * This is equivalent to calling atLeast(1).
     *
     * @return NonNullableAritySetOptionBuilder for method chaining
     */
    fun required(): NonNullableAritySetOptionBuilder<T> {
        // For collection arity, required() is equivalent to atLeast(1)
        return atLeast(1)
    }

    /**
     * Requires at least n arity groups to be provided for this set arity option.
     *
     * @param n Minimum number of arity groups required (must be >= 1)
     * @return NonNullableAritySetOptionBuilder for method chaining
     * @throws ConfigException if n < 1
     */
    fun atLeast(n: Int): NonNullableAritySetOptionBuilder<T> {
        if (n < 1) throw ConfigException("atLeast must require at least 1 occurrence")
        // Apply atLeast constraint to the delegate using the standard pattern
        addRequiredConstraint(delegate.constraintHooks, n)
        return NonNullableAritySetOptionBuilder(this)
    }

    /**
     * Sets a default value for this set arity option, making it non-nullable.
     * The default value should be a Set<List<T>> where each sublist has exactly arityCount elements.
     */
    fun default(value: Set<List<T>>): NonNullableAritySetOptionBuilder<T> {
        // Validate that each sublist has the correct arity
        for (sublist in value) {
            if (sublist.size != arityCount) {
                throw ConfigException("default value sublist for arity($arityCount) must contain exactly $arityCount elements (got ${sublist.size})")
            }
        }

        val copyWithDefault = AritySetOptionBuilder(
            delegate,
            originalSingle,
            arityCount,
            validators,
            collectionValidators,
            callback,
            value
        )
        return NonNullableAritySetOptionBuilder(copyWithDefault)
    }

    /** Sets help text for this set arity option. */
    fun help(text: String): AritySetOptionBuilder<T> = this
    /** Marks this set arity option as hidden from help output. */
    fun hidden(): AritySetOptionBuilder<T> = this
    /** Specifies that this set arity option conflicts with other options. */
    fun conflictsWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Makes this set arity option required if any of the referenced options are present. */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Makes this set arity option required if all of the referenced options are present. */
    fun requireIfAllPresent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Makes this set arity option required if any of the referenced options are absent. */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Makes this set arity option required if all of the referenced options are absent. */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Makes this set arity option required if the referenced option's value matches the predicate. */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): AritySetOptionBuilder<T> = this
    /** Allows this set arity option only when the referenced option's value matches the predicate. */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): AritySetOptionBuilder<T> = this
    /** Creates an "exactly one" constraint with other options. */
    fun exactlyOneWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Creates an "at most one" constraint with other options. */
    fun atMostOneWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    /** Creates an "at least one" constraint with other options. */
    fun atLeastOneWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this

    /**
     * Adds validation for each arity group in the set.
     *
     * @param message Error message template
     * @param predicate Function that returns true if the arity group is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid arity group for {option}: {value}",
        predicate: (List<T>) -> Boolean
    ): AritySetOptionBuilder<T> =
        AritySetOptionBuilder(
            delegate,
            originalSingle,
            arityCount,
            validators + (message to predicate),
            collectionValidators,
            callback,
            defaultValue
        )

    /**
     * Validates the entire collection of arity groups for this set option.
     * The predicate receives the complete Set<List<T>>? value.
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the collection is valid
     */
    fun validateCollection(
        message: String = "Invalid collection for {option}: {value}",
        predicate: (Set<List<T>>?) -> Boolean
    ): AritySetOptionBuilder<T> =
        AritySetOptionBuilder(
            delegate,
            originalSingle,
            arityCount,
            validators,
            collectionValidators + (message to predicate),
            callback,
            defaultValue
        )

}

/**
 * Builder for arity-based set options that return non-nullable sets of lists.
 *
 * This class is created when calling `.required()` or `.atLeast(n)` on an AritySetOptionBuilder.
 * Returns `Set<List<T>>` (non-nullable) and preserves all arity functionality.
 *
 * @param T The element type of the values in the sublists
 */
class NonNullableAritySetOptionBuilder<T : Any> internal constructor(
    private val delegate: AritySetOptionBuilder<T>
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, Set<List<T>>>> {

    /**
     * Provides the property delegate implementation for non-nullable set arity options.
     *
     * @param thisRef The Arguments instance this option belongs to
     * @param property The property being delegated
     * @return A ReadOnlyProperty that returns non-null Set<List<T>> when accessed
     */
    override fun provideDelegate(
        thisRef: Arguments,
        property: KProperty<*>
    ): ReadOnlyProperty<Arguments, Set<List<T>>> {
        val nullableProperty = delegate.provideDelegate(thisRef, property)
        return ReadOnlyProperty { args, prop ->
            val nullableValue = nullableProperty.getValue(args, prop)
            if (nullableValue == null) {
                // Check if the delegate has a defaultValue that should be returned
                if (delegate.defaultValue != null) {
                    return@ReadOnlyProperty delegate.defaultValue!!
                } else {
                    return@ReadOnlyProperty emptySet()
                }
            }
            nullableValue
        }
    }

    /** Sets help text for this set arity option. */
    fun help(text: String): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.help(text))

    /** Marks this set arity option as hidden from help output. */
    fun hidden(): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.hidden())

    /** Specifies that this set arity option conflicts with other options. */
    fun conflictsWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.conflictsWith(*refs))

    /** Makes this set arity option required if any of the referenced options are present. */
    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAnyPresent(*refs))

    /** Makes this set arity option required if all of the referenced options are present. */
    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAllPresent(*refs))

    /** Makes this set arity option required if any of the referenced options are absent. */
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    /** Makes this set arity option required if all of the referenced options are absent. */
    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAllAbsent(*refs))

    /** Makes this set arity option required if the referenced option's value matches the predicate. */
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfValue(ref, predicate))

    /** Allows this set arity option only when the referenced option's value matches the predicate. */
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    /** Creates an "exactly one" constraint with other options. */
    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.exactlyOneWith(*refs))

    /** Creates an "at most one" constraint with other options. */
    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.atMostOneWith(*refs))

    /** Creates an "at least one" constraint with other options. */
    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.atLeastOneWith(*refs))

    /**
     * Adds validation for each arity group. The predicate receives each List<T> group individually.
     *
     * @param message Error message template
     * @param predicate Function that returns true if the arity group is valid
     * @return This builder for method chaining
     */
    fun validate(
        message: String = "Invalid arity group for {option}: {value}",
        predicate: (List<T>) -> Boolean
    ): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.validate(message, predicate))

    /**
     * Validates the entire collection of arity groups for this non-nullable set option.
     * The predicate receives the complete Set<List<T>> value (never null).
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the collection is valid
     */
    fun validateCollection(
        message: String = "Invalid collection for {option}: {value}",
        predicate: (Set<List<T>>) -> Boolean
    ): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.validateCollection(message) { set ->
            set?.let(predicate) ?: false
        })

}
