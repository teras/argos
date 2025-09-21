/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

/**
 * Builder for creating command-line domains (subcommands) with various configurations and constraints.
 *
 * This class provides a fluent API for defining domains that can have their own sets of available
 * options and domain-scoped constraints. Domains are useful for creating subcommands or modes
 * in command-line applications.
 *
 * Domains can be:
 * - Regular domains: selectable from command line
 * - Fragments: constraint templates that are never selectable but can be inherited by other domains
 */
class DomainBuilder internal constructor(
    private val id: String?,
    private val isFragment: Boolean = false
) : PropertyDelegateProvider<Arguments, ReadOnlyProperty<Arguments, Boolean>> {

    private var label: String? = null
    private var description: String? = null
    private val aliases: MutableSet<String> = linkedSetOf()

    private val ruleHooks = mutableListOf<(Arguments, domainId: String) -> Unit>()
    private val inheritedDomains = mutableSetOf<String>()

    /**
     * Sets the display label for this domain.
     */
    fun label(text: String) = apply {
        if (isFragment) throw ConfigException("Fragments cannot have labels")
        label = text.ifBlank { null }
    }

    /**
     * Sets help text for this domain.
     */
    fun help(text: String) = apply {
        if (isFragment) throw ConfigException("Fragments cannot have help text")
        description = text.ifBlank { null }
    }

    /**
     * Adds aliases for this domain.
     */
    fun aliases(vararg names: String) = apply {
        if (isFragment) throw ConfigException("Fragments cannot have aliases")
        aliases += names.mapNotNull { it.trim().takeIf(String::isNotBlank) }
    }

    /**
     * Inherits constraints from other domains or fragments.
     *
     * @param domains The domains to inherit from (must be at least 1, duplicates automatically removed)
     * @throws ConfigException if no domains provided, or if this is a fragment (fragments cannot inherit)
     */
    fun inherits(vararg domains: KProperty0<Boolean>) = apply {
        if (domains.isEmpty()) {
            throw ConfigException("inherits() requires at least 1 domain")
        }
        if (isFragment) {
            throw ConfigException("Fragments cannot inherit from other domains")
        }

        // Use Set to automatically handle duplicates
        val domainNames = domains.map { it.name }.toSet()
        inheritedDomains.addAll(domainNames)
    }

    // Domain-scoped rules (fluent on DomainBuilder)

    /**
     * Requires the specified property to be present when this domain is active.
     */
    fun required(ref: KProperty<*>) = apply {
        ruleHooks += { reg, dom -> reg.addRequiredMin(ref.name, 1, scope = setOf(dom)) }
    }

    /**
     * Requires at least n values for the specified property when this domain is active.
     */
    fun atLeast(ref: KProperty<*>, n: Int) = apply {
        if (n <= 0) throw ConfigException("atLeast requires n >= 1 (got $n) for '${ref.name}'")
        ruleHooks += { reg, dom -> reg.addRequiredMin(ref.name, n, scope = setOf(dom)) }
    }

    /**
     * Requires the target property to be present when any of the specified properties are present
     * and this domain is active.
     */
    fun requireIfAnyPresent(target: KProperty<*>, vararg refs: KProperty<*>) = apply {
        val t = target.name
        val names = refs.map { it.name }.toSet()
        if (t in names) throw ConfigException("Self is not allowed in requireIfAnyPresent refs for '$t'")
        ruleHooks += { reg, dom -> reg.addConditional(t, CondKind.ANY_PRESENT, names, null, null, scope = setOf(dom)) }
    }

    /**
     * Requires the target property to be present when all of the specified properties are present
     * and this domain is active.
     */
    fun requireIfAllPresent(target: KProperty<*>, vararg refs: KProperty<*>) = apply {
        val t = target.name
        val names = refs.map { it.name }.toSet()
        if (t in names) throw ConfigException("Self is not allowed in requireIfAllPresent refs for '$t'")
        ruleHooks += { reg, dom -> reg.addConditional(t, CondKind.ALL_PRESENT, names, null, null, scope = setOf(dom)) }
    }

    /**
     * Requires the target property to be present when any of the specified properties are absent
     * and this domain is active.
     */
    fun requireIfAnyAbsent(target: KProperty<*>, vararg refs: KProperty<*>) = apply {
        val t = target.name
        val names = refs.map { it.name }.toSet()
        if (t in names) throw ConfigException("Self is not allowed in requireIfAnyAbsent refs for '$t'")
        ruleHooks += { reg, dom -> reg.addConditional(t, CondKind.ANY_ABSENT, names, null, null, scope = setOf(dom)) }
    }

    /**
     * Requires the target property to be present when all of the specified properties are absent
     * and this domain is active.
     */
    fun requireIfAllAbsent(target: KProperty<*>, vararg refs: KProperty<*>) = apply {
        val t = target.name
        val names = refs.map { it.name }.toSet()
        if (t in names) throw ConfigException("Self is not allowed in requireIfAllAbsent refs for '$t'")
        ruleHooks += { reg, dom -> reg.addConditional(t, CondKind.ALL_ABSENT, names, null, null, scope = setOf(dom)) }
    }

    /**
     * Requires the target property to be present when the specified property matches the given
     * predicate and this domain is active.
     */
    fun <U> requireIfValue(target: KProperty<*>, ref: KProperty<U>, predicate: (U?) -> Boolean) = apply {
        val t = target.name
        ruleHooks += { reg, dom ->
            @Suppress("UNCHECKED_CAST")
            reg.addConditional(
                t,
                CondKind.VALUE_PREDICATE,
                emptySet(),
                ref.name,
                { any -> predicate(any as U?) },
                scope = setOf(dom)
            )
        }
    }

    /**
     * Allows the target property to be used only when the specified property matches the given
     * predicate and this domain is active. If the target option is provided but the condition
     * is false, an error is reported.
     */
    fun <U> allowOnlyIfValue(target: KProperty<*>, ref: KProperty<U>, predicate: (U?) -> Boolean) = apply {
        val t = target.name
        ruleHooks += { reg, dom ->
            @Suppress("UNCHECKED_CAST")
            reg.addConditional(
                t,
                CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE,
                emptySet(),
                ref.name,
                { any -> predicate(any as U?) },
                scope = setOf(dom)
            )
        }
    }

    /**
     * Ensures exactly one of the specified properties is provided when this domain is active.
     */
    fun exactlyOne(vararg refs: KProperty<*>) = apply {
        val owners = refs.map { it.name }.toSet()
        if (owners.size < 2) throw ConfigException("exactlyOne requires at least 2 refs")
        ruleHooks += { reg, dom -> reg.addGroup("exactly-one", owners, scope = setOf(dom)) }
    }

    /**
     * Ensures at most one of the specified properties is provided when this domain is active.
     */
    fun atMostOne(vararg refs: KProperty<*>) = apply {
        val owners = refs.map { it.name }.toSet()
        if (owners.size < 2) throw ConfigException("atMostOne requires at least 2 refs")
        ruleHooks += { reg, dom -> reg.addGroup("at-most-one", owners, scope = setOf(dom)) }
    }

    /**
     * Ensures at least one of the specified properties is provided when this domain is active.
     */
    fun atLeastOne(vararg refs: KProperty<*>) = apply {
        val owners = refs.map { it.name }.toSet()
        if (owners.size < 2) throw ConfigException("atLeastOne requires at least 2 refs")
        ruleHooks += { reg, dom -> reg.addGroup("at-least-one", owners, scope = setOf(dom)) }
    }

    /**
     * Specifies that the specified properties conflict with each other when this domain is active.
     */
    fun conflicts(vararg refs: KProperty<*>) = apply {
        val owners = refs.map { it.name }.toSet()
        if (owners.size < 2) throw ConfigException("conflicts requires at least 2 refs")
        ruleHooks += { reg, dom -> reg.addConflicts(owners, scope = setOf(dom)) }
    }

    /**
     * Provides the property delegate implementation for domain options.
     * This method is called automatically by Kotlin's property delegation system.
     * It registers the domain with the Arguments instance and returns a delegate
     * that indicates whether this domain is currently active/selected.
     *
     * @param thisRef The Arguments instance this domain belongs to
     * @param property The property this delegate is assigned to
     * @return A ReadOnlyProperty that returns true if this domain is active, false otherwise
     */
    override fun provideDelegate(thisRef: Arguments, property: KProperty<*>): ReadOnlyProperty<Arguments, Boolean> {
        val effectiveId = (id ?: property.name).trim()

        // Register domain information (will be processed later for inheritance)
        thisRef.registerDomainBuilder(
            id = effectiveId,
            label = label,
            description = description,
            aliases = aliases.toSet(),
            propName = property.name,
            isFragment = isFragment,
            ruleHooks = ruleHooks.toList(),
            inheritedDomains = inheritedDomains.toSet()
        )

        // Register domain for value source tracking
        thisRef.valueSources[property.name] = ValueSource.MISSING

        return ReadOnlyProperty { _, _ ->
            if (isFragment) {
                false // Fragments are never active
            } else {
                thisRef.selectedDomain() == effectiveId
            }
        }
    }
}