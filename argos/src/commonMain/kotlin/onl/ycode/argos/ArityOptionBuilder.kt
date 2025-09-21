/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import onl.ycode.argos.terminal.ContentStyle
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
    labelFrags: List<TextFragment>,
    expected: String?,
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
                                    messageRuns(
                                        translate(message),
                                        "name" to name,
                                        "value" to completedGroup.toString()
                                    )
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
                val more = expected?.let { " (expected $it)" } ?: ""
                return ParseOutcome.Error(
                    listOf(
                        TextFragment(ContentStyle.PLAIN, "Invalid value '"),
                        TextFragment(ContentStyle.PLAIN, res.original ?: raw.value ?: "null"),
                        TextFragment(ContentStyle.PLAIN, "' for ")
                    ) + labelFrags + listOf(
                        TextFragment(ContentStyle.PLAIN, pos(raw.argvIndex) + more)
                    )
                )
            }
        }
    }

    return ParseOutcome.Success(collection)
}

/**
 * Builder for arity-based options that return nullable lists of values.
 *
 * This class is created when calling `.arity(n)` on an option builder and provides
 * a fluent API for configuring multi-value options that must consume exactly `n` values.
 * Returns `List<T>?` (nullable) - use `.required()` to get non-nullable `List<T>`.
 *
 * @param T The element type of the values in the returned list
 */
class ArityOptionBuilder<T> internal constructor(
    internal val delegate: OptionBuilder<List<T>?>,
    private val originalSingle: (Raw) -> SingleResult<T>,  // Original single converter
    private val arityCount: Int,
    private val validators: List<Pair<String, (List<T>) -> Boolean>> = emptyList(),
    internal val defaultValue: List<T>? = null  // Default value for arity options
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, List<T>?>> {

    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, List<T>?> {

        val name = property.name
        val seq = delegate.positionalSeq ?: if (delegate.positionalKind != null) thisRef.nextPosSeq() else null

        val regSwitches =
            if (delegate.switches.isEmpty() && delegate.positionalKind == null)
                listOf(thisRef.defaultLongPrefix() + name.toKebab())
            else delegate.switches

        // Resolve the actual negation prefix - use custom prefix or default
        val actualNegationPrefix = delegate.negationPrefix ?: if (delegate.booleanNegatable) thisRef.getDefaultNegationPrefix() else null

        // Initialize value source based on whether this option has a default
        thisRef.valueSources[name] = if (delegate.isNonNullable || defaultValue != null) ValueSource.DEFAULT else ValueSource.MISSING

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
            booleanNegatable = delegate.booleanNegatable,
            negationPrefix = actualNegationPrefix,
            positionalKind = delegate.positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = delegate.expectedDesc,
            eager = delegate.eager,
            hidden = delegate.hidden,
            defaultValue = delegate.defaultValue,
            parseMany = { raws, labelFrags, expected ->
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
                                val more = expected?.let { " (expected $it)" } ?: ""
                                return@registerOption ParseOutcome.Error(
                                    listOf(
                                        TextFragment(ContentStyle.PLAIN, "Invalid value '"),
                                        TextFragment(ContentStyle.PLAIN, res.original ?: raw.value ?: "null"),
                                        TextFragment(ContentStyle.PLAIN, "' for ")
                                    ) + labelFrags + listOf(
                                        TextFragment(ContentStyle.PLAIN, pos(raw.argvIndex) + more)
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
        return NonNullableArityOptionBuilder(ArityOptionBuilder(delegate, originalSingle, arityCount, validators, defaultValue))
    }

    /**
     * Marks this arity option as required with a minimum number of occurrences.
     * For arity options, atLeast(n) implies required().
     */
    fun atLeast(n: Int): NonNullableArityOptionBuilder<T> {
        if (n < 1) throw ConfigError("atLeast must require at least 1 occurrence")
        // Apply atLeast constraint to the delegate's constraint hooks for collection arity
        delegate.constraintHooks += { reg, owner -> reg.addRequiredMin(owner, n, scope = null) }
        return NonNullableArityOptionBuilder(ArityOptionBuilder(delegate, originalSingle, arityCount, validators, defaultValue))
    }

    /**
     * Sets a default value for this arity option, making it non-nullable.
     * The default value should be a List<T> with exactly arityCount elements.
     */
    fun default(value: List<T>): NonNullableArityOptionBuilder<T> {
        if (value.size != arityCount) {
            throw ConfigError("default value for arity($arityCount) must contain exactly $arityCount elements (got ${value.size})")
        }

        // UNIFIED: Create a modified copy with the default value
        // This ensures the unified format is preserved with the default value set
        val copyWithDefault = ArityOptionBuilder(delegate, originalSingle, arityCount, validators, value)
        return NonNullableArityOptionBuilder(copyWithDefault)
    }

    // Delegate methods to preserve arity functionality
    fun help(text: String): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.help(text), originalSingle, arityCount, validators, defaultValue)

    fun hidden(): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.hidden(), originalSingle, arityCount, validators, defaultValue)

    fun conflictsWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.conflictsWith(*refs), originalSingle, arityCount, validators, defaultValue)

    fun requireIfAnyPresent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAnyPresent(*refs), originalSingle, arityCount, validators, defaultValue)

    fun requireIfAllPresent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAllPresent(*refs), originalSingle, arityCount, validators, defaultValue)

    fun requireIfAnyAbsent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAnyAbsent(*refs), originalSingle, arityCount, validators, defaultValue)

    fun requireIfAllAbsent(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfAllAbsent(*refs), originalSingle, arityCount, validators, defaultValue)

    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.requireIfValue(ref, predicate), originalSingle, arityCount, validators, defaultValue)

    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.allowOnlyIfValue(ref, predicate), originalSingle, arityCount, validators, defaultValue)

    fun exactlyOneWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.exactlyOneWith(*refs), originalSingle, arityCount, validators, defaultValue)

    fun atMostOneWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.atMostOneWith(*refs), originalSingle, arityCount, validators, defaultValue)

    fun atLeastOneWith(vararg refs: KProperty<*>): ArityOptionBuilder<T> =
        ArityOptionBuilder(delegate.atLeastOneWith(*refs), originalSingle, arityCount, validators, defaultValue)

    fun validate(
        message: String = "Invalid arity group for {name}: {value}",
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

    // Delegate methods to preserve arity functionality - all return NonNullableArityOptionBuilder
    fun help(text: String): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.help(text))

    fun hidden(): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.hidden())

    fun conflictsWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.conflictsWith(*refs))

    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAnyPresent(*refs))

    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAllPresent(*refs))

    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfAllAbsent(*refs))

    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.requireIfValue(ref, predicate))

    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.exactlyOneWith(*refs))

    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.atMostOneWith(*refs))

    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableArityOptionBuilder<T> =
        NonNullableArityOptionBuilder(delegate.atLeastOneWith(*refs))

    fun validate(
        message: String = "Invalid arity group for {name}: {value}",
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
 * Builder for arity-based list options that return nullable lists of lists.
 *
 * This class is created when calling `.arity(n)` on a list option builder.
 * Each invocation of the option consumes exactly `n` values to create a sublist.
 * Returns `List<List<T>>?` (nullable) - use `.required()` to get non-nullable.
 *
 * @param T The element type of the values in the sublists
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

    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, List<List<T>>?> {
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
            booleanNegatable = false,
            negationPrefix = null,
            positionalKind = delegate.positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = delegate.expectedDesc,
            eager = false,
            hidden = delegate.hidden,
            defaultValue = null,
            parseMany = { raws, labelFrags, expected ->
                if (raws.isEmpty()) {
                    if (defaultValue != null) {
                        thisRef.valueSources[name] = ValueSource.DEFAULT
                        ParseOutcome.Success(defaultValue)
                    } else {
                        ParseOutcome.Success(null)
                    }
                } else {
                    val sublists = mutableListOf<List<T>>()
                    when (val result = parseArityGroups(raws, arityCount, originalSingle, name, thisRef.valueSources, labelFrags, expected, validators, sublists, callback)) {
                        is ParseOutcome.Success -> {
                            // Validate the entire collection
                            for ((message, predicate) in collectionValidators) {
                                if (!predicate(result.value)) {
                                    return@registerOption ParseOutcome.Error(
                                        messageRuns(
                                            translate(message),
                                            "name" to name,
                                            "value" to result.value.toString(),
                                            "count" to result.value.size.toString()
                                        )
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

    fun required(): NonNullableArityListOptionBuilder<T> {
        // For collection arity, required() is equivalent to atLeast(1)
        return atLeast(1)
    }

    fun atLeast(n: Int): NonNullableArityListOptionBuilder<T> {
        if (n < 1) throw ConfigError("atLeast must require at least 1 occurrence")
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
                throw ConfigError("default value sublist at index $index for arity($arityCount) must contain exactly $arityCount elements (got ${sublist.size})")
            }
        }

        val copyWithDefault = ArityListOptionBuilder(delegate, originalSingle, arityCount, validators, collectionValidators, callback, value)
        return NonNullableArityListOptionBuilder(copyWithDefault)
    }

    // Delegate methods (simplified - return same instance for chaining)
    fun help(text: String): ArityListOptionBuilder<T> = this
    fun hidden(): ArityListOptionBuilder<T> = this
    fun conflictsWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun requireIfAnyPresent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun requireIfAllPresent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun requireIfAllAbsent(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityListOptionBuilder<T> = this
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): ArityListOptionBuilder<T> = this
    fun exactlyOneWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun atMostOneWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this
    fun atLeastOneWith(vararg refs: KProperty<*>): ArityListOptionBuilder<T> = this

    fun validate(
        message: String = "Invalid arity group for {name}: {value}",
        predicate: (List<T>) -> Boolean
    ): ArityListOptionBuilder<T> =
        ArityListOptionBuilder(delegate, originalSingle, arityCount, validators + (message to predicate), collectionValidators, callback, defaultValue)

    /**
     * Validates the entire collection of arity groups for this list option.
     * The predicate receives the complete List<List<T>>? value.
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the collection is valid
     */
    fun validateCollection(
        message: String = "Invalid collection for {name}: {value}",
        predicate: (List<List<T>>?) -> Boolean
    ): ArityListOptionBuilder<T> =
        ArityListOptionBuilder(delegate, originalSingle, arityCount, validators, collectionValidators + (message to predicate), callback, defaultValue)

}

/**
 * Builder for arity-based list options that return non-nullable lists of lists.
 */
class NonNullableArityListOptionBuilder<T : Any> internal constructor(
    private val delegate: ArityListOptionBuilder<T>
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, List<List<T>>>> {

    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, List<List<T>>> {
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

    // Delegate methods
    fun help(text: String): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.help(text))

    fun hidden(): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.hidden())

    fun conflictsWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.conflictsWith(*refs))

    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAnyPresent(*refs))

    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAllPresent(*refs))

    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfAllAbsent(*refs))

    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.requireIfValue(ref, predicate))

    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.exactlyOneWith(*refs))

    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.atMostOneWith(*refs))

    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.atLeastOneWith(*refs))

    fun validate(
        message: String = "Invalid arity group for {name}: {value}",
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
        message: String = "Invalid collection for {name}: {value}",
        predicate: (List<List<T>>) -> Boolean
    ): NonNullableArityListOptionBuilder<T> =
        NonNullableArityListOptionBuilder(delegate.validateCollection(message) { list ->
            list?.let(predicate) ?: false
        })

}

/**
 * Builder for arity-based set options that return nullable sets of lists.
 *
 * This class is created when calling `.arity(n)` on a set option builder.
 * Each invocation of the option consumes exactly `n` values to create a sublist.
 * Returns `Set<List<T>>?` (nullable) - use `.required()` to get non-nullable.
 *
 * @param T The element type of the values in the sublists
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

    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, Set<List<T>>?> {
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
            booleanNegatable = false,
            negationPrefix = null,
            positionalKind = delegate.positionalKind,
            positionalSeq = seq,
            envVar = null,
            expectedDesc = delegate.expectedDesc,
            eager = false,
            hidden = delegate.hidden,
            defaultValue = null,
            parseMany = { raws, labelFrags, expected ->
                if (raws.isEmpty()) {
                    if (defaultValue != null) {
                        thisRef.valueSources[name] = ValueSource.DEFAULT
                        ParseOutcome.Success(defaultValue)
                    } else {
                        ParseOutcome.Success(null)
                    }
                } else {
                    val sublists = mutableSetOf<List<T>>()
                    when (val result = parseArityGroups(raws, arityCount, originalSingle, name, thisRef.valueSources, labelFrags, expected, validators, sublists, callback)) {
                        is ParseOutcome.Success -> {
                            // Validate the entire collection
                            for ((message, predicate) in collectionValidators) {
                                if (!predicate(result.value)) {
                                    return@registerOption ParseOutcome.Error(
                                        messageRuns(
                                            message,
                                            "name" to name,
                                            "value" to result.value.toString(),
                                            "count" to result.value.size.toString()
                                        )
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

    fun required(): NonNullableAritySetOptionBuilder<T> {
        // For collection arity, required() is equivalent to atLeast(1)
        return atLeast(1)
    }

    fun atLeast(n: Int): NonNullableAritySetOptionBuilder<T> {
        if (n < 1) throw ConfigError("atLeast must require at least 1 occurrence")
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
                throw ConfigError("default value sublist for arity($arityCount) must contain exactly $arityCount elements (got ${sublist.size})")
            }
        }

        val copyWithDefault = AritySetOptionBuilder(delegate, originalSingle, arityCount, validators, collectionValidators, callback, value)
        return NonNullableAritySetOptionBuilder(copyWithDefault)
    }

    // Delegate methods (simplified - return same instance for chaining)
    fun help(text: String): AritySetOptionBuilder<T> = this
    fun hidden(): AritySetOptionBuilder<T> = this
    fun conflictsWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun requireIfAnyPresent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun requireIfAllPresent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun requireIfAnyAbsent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun requireIfAllAbsent(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): AritySetOptionBuilder<T> = this
    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): AritySetOptionBuilder<T> = this
    fun exactlyOneWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun atMostOneWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this
    fun atLeastOneWith(vararg refs: KProperty<*>): AritySetOptionBuilder<T> = this

    fun validate(
        message: String = "Invalid arity group for {name}: {value}",
        predicate: (List<T>) -> Boolean
    ): AritySetOptionBuilder<T> =
        AritySetOptionBuilder(delegate, originalSingle, arityCount, validators + (message to predicate), collectionValidators, callback, defaultValue)

    /**
     * Validates the entire collection of arity groups for this set option.
     * The predicate receives the complete Set<List<T>>? value.
     *
     * @param message Error message to display if validation fails
     * @param predicate Function that returns true if the collection is valid
     */
    fun validateCollection(
        message: String = "Invalid collection for {name}: {value}",
        predicate: (Set<List<T>>?) -> Boolean
    ): AritySetOptionBuilder<T> =
        AritySetOptionBuilder(delegate, originalSingle, arityCount, validators, collectionValidators + (message to predicate), callback, defaultValue)

}

/**
 * Builder for arity-based set options that return non-nullable sets of lists.
 */
class NonNullableAritySetOptionBuilder<T : Any> internal constructor(
    private val delegate: AritySetOptionBuilder<T>
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, Set<List<T>>>> {

    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, Set<List<T>>> {
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

    // Delegate methods
    fun help(text: String): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.help(text))

    fun hidden(): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.hidden())

    fun conflictsWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.conflictsWith(*refs))

    fun requireIfAnyPresent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAnyPresent(*refs))

    fun requireIfAllPresent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAllPresent(*refs))

    fun requireIfAnyAbsent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAnyAbsent(*refs))

    fun requireIfAllAbsent(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfAllAbsent(*refs))

    fun <U> requireIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.requireIfValue(ref, predicate))

    fun <U> allowOnlyIfValue(ref: KProperty<U>, predicate: (U?) -> Boolean): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.allowOnlyIfValue(ref, predicate))

    fun exactlyOneWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.exactlyOneWith(*refs))

    fun atMostOneWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.atMostOneWith(*refs))

    fun atLeastOneWith(vararg refs: KProperty<*>): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.atLeastOneWith(*refs))

    fun validate(
        message: String = "Invalid arity group for {name}: {value}",
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
        message: String = "Invalid collection for {name}: {value}",
        predicate: (Set<List<T>>) -> Boolean
    ): NonNullableAritySetOptionBuilder<T> =
        NonNullableAritySetOptionBuilder(delegate.validateCollection(message) { set ->
            set?.let(predicate) ?: false
        })

}
