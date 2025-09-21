/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

// Arguments.kt
package onl.ycode.argos

import onl.ycode.argos.ArgosI18n.translate
import onl.ycode.argos.snapshot.ConditionalInfo
import onl.ycode.argos.snapshot.DomainInfo
import onl.ycode.argos.snapshot.GroupRuleInfo
import onl.ycode.argos.snapshot.PositionalInfo
import onl.ycode.argos.snapshot.PositionalKind
import onl.ycode.argos.snapshot.Settings
import onl.ycode.argos.snapshot.Snapshot
import onl.ycode.argos.snapshot.SpecInfo
import onl.ycode.argos.terminal.ANSITerminal
import onl.ycode.argos.terminal.ContentStyle
import onl.ycode.argos.terminal.PlainTerminal
import onl.ycode.argos.terminal.Terminal
import kotlin.math.max
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

// ============================ ArgosProperty ============================


// ============================ Domain & Errors ============================

internal data class Domain(
    val id: String,
    val label: String? = null,
    val description: String? = null,
    val aliases: Set<String> = emptySet()
)

internal data class DomainBuilderInfo(
    val id: String,
    val label: String?,
    val description: String?,
    val aliases: Set<String>,
    val propName: String,
    val isFragment: Boolean,
    val ruleHooks: List<(Arguments, String) -> Unit>,
    val inheritedDomains: Set<String>
)

// ============================ Core data & helpers ============================

internal data class Raw(val present: Boolean, val value: String?, val argvIndex: Int?, val negated: Boolean = false)

internal enum class ValueKind { SINGLE, LIST, SET }

internal enum class CondKind { ANY_PRESENT, ALL_PRESENT, ANY_ABSENT, ALL_ABSENT, VALUE_PREDICATE, ALLOW_ONLY_IF_VALUE_PREDICATE }

internal sealed class SingleResult<out T> {
    data object Missing : SingleResult<Nothing>()
    data class Value<T>(val value: T, val source: ValueSource = ValueSource.USER) : SingleResult<T>()
    data class Invalid(val original: String?) : SingleResult<Nothing>()
}

internal sealed class ParseOutcome<out T> {
    data class Success<T>(val value: T) : ParseOutcome<T>()
    data class Error(val error: String) : ParseOutcome<Nothing>()
}

// ============================ Constraints model (instance-scoped) ============================

internal data class ConditionalRequired(
    val targetOwner: String,
    val kind: CondKind,
    val refs: Set<String>,
    val predicateRef: String?,
    val predicate: ((Any?) -> Boolean)?,
    val limitToDomains: Set<String>?
)

internal data class RequiredMin(
    val owner: String,
    val min: Int,
    val limitToDomains: Set<String>? = null
)

internal data class Conflicts(
    val owners: Set<String>,
    val limitToDomains: Set<String>? = null
)

internal sealed class GroupRule {
    abstract val owners: Set<String>
    abstract val limitToDomains: Set<String>?

    data class ExactlyOne(override val owners: Set<String>, override val limitToDomains: Set<String>? = null) :
        GroupRule()

    data class AtMostOne(override val owners: Set<String>, override val limitToDomains: Set<String>? = null) :
        GroupRule()

    data class AtLeastOne(override val owners: Set<String>, override val limitToDomains: Set<String>? = null) :
        GroupRule()
}

internal data class TextFragment(val style: ContentStyle, val text: String)

// ============================ Utils ============================

internal fun invalidValueFragments(
    value: String?,
    baseSwitch: String,
    position: Int?,
    expectedDesc: List<String>?
): String {
    return (if (value == null) {
        if (position == null) {
            if (expectedDesc == null) translate("Missing value for {option}")
            else translate("Missing value for {option}, expected {expected}")
        } else {
            if (expectedDesc == null) translate("Missing value for {option} at position {position}")
            else translate("Missing value for {option} at position {position}, expected {expected}")
        }
    } else {
        if (position == null) {
            if (expectedDesc == null) translate("Invalid value '{value}' for {option}")
            else translate("Invalid value '{value}' for {option}, expected {expected}")
        } else {
            if (expectedDesc == null) translate("Invalid value '{value}' for {option} at position {position}")
            else translate("Invalid value '{value}' for {option} at position {position}, expected {expected}")
        }
    })
        .replace("{option}", baseSwitch.asOption())
        .replace("{expected}", expectedDesc.asValueListExpected())
        .replace("{position}", position.asPosition())
        .replace("{value}", value.asValue())
}

internal val TRUE_LITERALS = setOf("true", "1", "yes", "on")
internal val FALSE_LITERALS = setOf("false", "0", "no", "off")
internal fun parseBooleanLiteral(s: String?): Boolean? {
    val v = s?.lowercase() ?: return null
    return when (v) {
        in TRUE_LITERALS -> true
        in FALSE_LITERALS -> false
        else -> null
    }
}


// ============================ Rule keying (dup detection) ============================

private sealed interface RuleKey {
    data class RequiredMinKey(val owner: String, val min: Int, val scope: Set<String>?) : RuleKey
    data class ConditionalKey(
        val owner: String,
        val kind: CondKind,
        val refs: Set<String>,
        val predicateRef: String?,
        val scope: Set<String>?
    ) : RuleKey

    data class GroupKey(val kind: String, val owners: Set<String>, val scope: Set<String>?) : RuleKey
    data class ConflictsKey(val owners: Set<String>, val scope: Set<String>?) : RuleKey
}

private fun canonNames(names: Collection<String>): Set<String> = names.toSet()
private fun canonScope(scope: Set<String>?): Set<String>? = scope?.toSet()

// ============================ Constraint helpers ============================

internal fun addRequiredConstraint(constraintHooks: MutableList<(Arguments, String) -> Unit>, n: Int) {
    if (n <= 0) throw ConfigException("atLeast requires n >= 1 (got $n)")
    constraintHooks += { reg, owner ->
        reg.addRequiredMin(owner, n, scope = null)
    }
}

internal fun addConditionalConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    kind: CondKind, refs: Array<out KProperty<*>>
) {
    constraintHooks += { reg, owner ->
        val names = refs.map { it.name }.toSet()
        if (owner in names) throw ConfigException("Self is not allowed in constraint refs for '$owner'")
        reg.addConditional(owner, kind, names, null, null, scope = null)
    }
}

internal fun addPredicateConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    predicateRef: String, predicate: (Any?) -> Boolean
) {
    constraintHooks += { reg, owner ->
        reg.addConditional(owner, CondKind.VALUE_PREDICATE, emptySet(), predicateRef, predicate, scope = null)
    }
}

internal fun addAllowOnlyIfPredicateConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    predicateRef: String, predicate: (Any?) -> Boolean
) {
    constraintHooks += { reg, owner ->
        reg.addConditional(
            owner,
            CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE,
            emptySet(),
            predicateRef,
            predicate,
            scope = null
        )
    }
}

internal fun addGroupConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    groupType: String,
    refs: Array<out KProperty<*>>
) {
    constraintHooks += { reg, owner ->
        val others = refs.map { it.name }.toSet()
        if (owner in others) throw ConfigException("Owner '$owner' must not be passed to $groupType(...)")
        val owners = buildSet { add(owner); addAll(others) }
        if (owners.size < 2) throw ConfigException("$groupType requires at least 1 other ref")
        reg.addGroup(groupType, owners, scope = null)
    }
}

internal fun addConflictsConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    refs: Array<out KProperty<*>>
) {
    constraintHooks += { reg, owner ->
        val others = refs.map { it.name }.toSet()
        if (owner in others) throw ConfigException("Owner '$owner' must not be passed to conflictsWith(...)")
        val owners = buildSet { add(owner); addAll(others) }
        if (owners.size < 2) throw ConfigException("conflictsWith requires at least 1 other ref")
        reg.addConflicts(owners, scope = null)
    }
}


// ============================ Arguments ============================

/**
 * Base class for defining command-line argument specifications using a type-safe DSL.
 *
 * Extend this class and use property delegation to define options, positional arguments, and subcommands (domains).
 *
 * ## Basic Usage
 *
 * ```kotlin
 * class MyArgs : Arguments(appName = "myapp", appDescription = "My application") {
 *     val port by option("--port", "-p").int().default(8080)
 *     val config by option("--config").required()
 *     val verbose by option("--verbose", "-v").bool()
 *     val inputFile by positional()
 * }
 *
 * fun main(args: Array<String>) {
 *     val myArgs = MyArgs().parse(args) ?: return
 *     println("Port: ${myArgs.port}, Config: ${myArgs.config}")
 * }
 * ```
 *
 * @param appName Application name displayed in help text (defaults to class name)
 * @param appDescription Optional application description displayed in help text
 * @param unknownOptionsAsPositionals If true, treats unknown options as positional arguments instead of errors
 * @param defaultLongPrefix Default prefix for long options (default: "--")
 * @param clusterChar Character that enables short option clustering like `-abc` for `-a -b -c` (default: '-')
 * @param valueSeparators Characters that can separate option names from values like `--opt=value` (default: '=' and ':')
 * @param didYouMean Enable "did you mean" suggestions for typos in option names (default: true)
 * @param didYouMeanMax Maximum edit distance for "did you mean" suggestions (default: 2)
 * @param aggregateErrors Show multiple errors at once instead of stopping at first error (default: true)
 * @param maxAggregatedErrors Maximum number of errors to show when aggregating (default: 20)
 * @param terminal Custom terminal implementation for output formatting (default: auto-detect ANSI support)
 * @param useANSITerminal Use ANSI color codes when terminal is auto-detected (default: true)
 * @param argumentFilePrefix Prefix character for argument files like `@file.txt` to read arguments from file (default: '@', null to disable)
 * @param argumentSeparator Separator used in help text between option aliases (default: "|")
 */
abstract class Arguments(
    appName: String = "",
    appDescription: String? = null,
    private val unknownOptionsAsPositionals: Boolean = false,
    defaultLongPrefix: String = "--",
    clusterChar: Char? = '-',
    valueSeparators: Set<Char> = setOf('=', ':'),
    private val didYouMean: Boolean = true,
    didYouMeanMax: Int = 2,
    private val aggregateErrors: Boolean = true,
    maxAggregatedErrors: Int = 20,
    terminal: Terminal? = null,
    useANSITerminal: Boolean = true,
    private val argumentFilePrefix: Char? = '@',
    argumentSeparator: String = "|",
) {

    private val appName =
        appName.trim().ifEmpty { this::class.simpleName?.lowercase() ?: "app" }

    private val appDescription: String? = if (appDescription.isNullOrBlank()) null else appDescription.trim()

    private val defaultNegationPrefix: String = "no-"

    private val defaultLongPrefix =
        if (defaultLongPrefix.length in 1..2 && defaultLongPrefix.all { it.isSymbol() }) defaultLongPrefix
        else throw ConfigException("Invalid defaultLongPrefix: $defaultLongPrefix")

    private val clusterChar = if (clusterChar == null || clusterChar.isSymbol()) clusterChar
    else throw ConfigException("Invalid clusterChar: $clusterChar")

    private val valueSeparators = if (valueSeparators.all { it.isSymbol() }) valueSeparators
    else throw ConfigException("Invalid valueSeparators: $valueSeparators")

    private val argumentSeparator =
        argumentSeparator.ifEmpty { throw ConfigException("Invalid argumentSeparator: cannot be empty") }

    private val didYouMeanMax: Int =
        didYouMeanMax.takeIf { it >= 0 } ?: throw ConfigException("Invalid didYouMeanMax: $didYouMeanMax")

    private val maxAggregatedErrors: Int =
        maxAggregatedErrors.takeIf { it >= 0 }
            ?: throw ConfigException("Invalid maxAggregatedErrors: $maxAggregatedErrors")

    private val specs = linkedMapOf<String, Spec<*>>()
    private val values = mutableMapOf<String, Any?>()
    internal val valueSources = mutableMapOf<String, ValueSource>()
    private val switchToOwner = mutableMapOf<String, String>()
    private val callbackRegistry = mutableMapOf<String, (Any?) -> Unit>()

    private var configValidated = false
    private val knownPrefixes = linkedSetOf<String>()

    private val requirements = mutableListOf<ConditionalRequired>()
    private val requiredMins = mutableListOf<RequiredMin>()
    private val conflicts = mutableListOf<Conflicts>()
    private val groups = mutableListOf<GroupRule>()
    private val ruleKeys = mutableSetOf<RuleKey>()
    internal val inputConfigs = mutableMapOf<String, InputConfig>()

    private val domainDefs = mutableListOf<Domain>()
    private val domainIds = mutableSetOf<String>()
    private val aliasToId = mutableMapOf<String, String>()
    private val domainPropNameToId = mutableMapOf<String, String>()

    // Domain builder information for late resolution
    private val domainBuilders = mutableMapOf<String, DomainBuilderInfo>()
    private val fragmentToInheritors = mutableMapOf<String, MutableSet<String>>()
    private val expandedDomainProps = mutableMapOf<String, Set<String>>()

    private var positionalSeqCounter = 0
    private val errorLines = mutableListOf<String>()

    private var terminal: Terminal = terminal ?: if (useANSITerminal) ANSITerminal() else PlainTerminal()
    private var selectedDomain: String? = null

    internal fun defaultLongPrefix() = defaultLongPrefix
    internal fun nextPosSeq(): Int = positionalSeqCounter++

    // ======== Registration ========

    internal fun registerDomain(
        id: String,
        label: String?,
        description: String?,
        aliases: Set<String>,
        propName: String
    ) {
        ensureConfigMutable()
        if (id.isBlank()) throw ConfigException("Domain id defined in property $propName cannot be blank")
        if (!id[0].isLetterOrDigit()) throw ConfigException("Domain id '$id' defined in property $propName must begin with a letter or digit")
        if (domainIds.contains(id)) throw ConfigException("Duplicate domain id '$id' defined in property $propName")
        if (aliasToId.containsKey(id)) throw ConfigException("Domain id '$id' defined in property $propName conflicts with an already existing domain alias")
        domainDefs += Domain(id, label, description, aliases)
        domainIds += id
        aliases.forEach { al ->
            if (al.isBlank()) throw ConfigException("Alias defined in domain id '$id', property $propName, cannot be blank")
            if (!al[0].isLetterOrDigit()) throw ConfigException("Alias $al defined in domain id '$id', property $propName, must begin with a letter or digit")
            if (domainIds.contains(al)) throw ConfigException("Alias $al defined in domain id '$id', property $propName, conflicts with an already existing domain id")
            if (aliasToId.containsKey(al)) throw ConfigException("Alias $al defined in domain id '$id', property $propName, conflicts with an already existing domain alias")
            aliasToId[al] = id
        }
        domainPropNameToId[propName] = id
    }

    internal fun registerDomainBuilder(
        id: String,
        label: String?,
        description: String?,
        aliases: Set<String>,
        propName: String,
        isFragment: Boolean,
        ruleHooks: List<(Arguments, String) -> Unit>,
        inheritedDomains: Set<String>
    ) {
        ensureConfigMutable()

        val builderInfo = DomainBuilderInfo(
            id = id,
            label = label,
            description = description,
            aliases = aliases,
            propName = propName,
            isFragment = isFragment,
            ruleHooks = ruleHooks,
            inheritedDomains = inheritedDomains
        )

        domainBuilders[id] = builderInfo
    }

    internal fun registerOption(
        ownerName: String,
        switches: List<String>,
        repeatable: Boolean,
        help: String?,
        domainProps: Set<String>?,
        requiresValue: Boolean,
        valueKind: ValueKind,
        arity: Int = 1,
        booleanFlag: Boolean,
        negationPrefix: String?,
        positionalKind: PositionalKind?,
        positionalSeq: Int?,
        envVar: String?,
        expectedDesc: List<String>?,
        eager: Boolean,
        hidden: Boolean = false,
        defaultValue: String? = null,
        parseMany: (List<Raw>, baseSwitch: String, expectedDesc: List<String>?) -> ParseOutcome<Any?>,
        single: (Raw) -> SingleResult<Any?>,
        validators: List<Pair<String, (Any?) -> Boolean>> = emptyList(),
        collectionValidators: List<Pair<String, (Any?) -> Boolean>> = emptyList()
    ) {
        ensureConfigMutable()

        for (sw in switches) {
            if (sw == "--") throw ConfigException("Switch '$sw' is reserved.")
            val prefix = sw.takeWhile { !it.isLetterOrDigit() && it != '_' }
            if (prefix.isEmpty()) throw ConfigException("Switch '$sw' must begin with a non-alphanumeric prefix.")
            if (prefix.length !in 1..2) throw ConfigException("Unsupported switch prefix length for '$sw' (${prefix.length}).")
            knownPrefixes += prefix

            val prev = switchToOwner[sw]
            if (prev != null && prev != ownerName) throw ConfigException("Switch '$sw' already bound to '$prev'.")

            switchToOwner[sw] = ownerName

            if (booleanFlag && negationPrefix != null) {
                if (prefix == this.defaultLongPrefix) {
                    val base = sw.drop(prefix.length)
                    if (!base.startsWith(negationPrefix)) {
                        val neg = prefix + negationPrefix + base
                        val prev2 = switchToOwner[neg]
                        if (prev2 != null && prev2 != ownerName) throw ConfigException("Auto-negation '$neg' conflicts with '$prev2'.")
                        switchToOwner[neg] = ownerName
                    }
                }
            }
        }

        val baseSwitch = if (switches.isNotEmpty()) switches.maxBy { it.length } else "<${ownerName}>"

        specs[ownerName] = Spec(
            ownerName,
            switches,
            baseSwitch,
            repeatable,
            help,
            domainProps,
            null,
            requiresValue,
            valueKind,
            arity,
            booleanFlag,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar,
            expectedDesc,
            eager,
            hidden,
            defaultValue,
            { raws -> parseMany(raws, baseSwitch, expectedDesc) },
            single,
            validators,
            collectionValidators
        )
        // Initialize value source as MISSING (will be updated during parsing if value is provided)
        valueSources[ownerName] = ValueSource.MISSING
    }

    internal fun valueOf(name: String): Any? {
        return values[name]
    }

    /**
     * Registers a callback for an option.
     * Used internally by the onValue mechanism.
     */
    internal fun registerCallback(optionName: String, callback: (Any?) -> Unit) {
        callbackRegistry[optionName] = callback
    }


    /**
     * Returns the source of an option's value, enabling provenance tracking for debugging and auditing.
     *
     * This method allows you to determine where an option's value originated, which is useful for:
     * - Debugging configuration issues and understanding why a value was used
     * - Implementing custom logging to track configuration sources
     * - Auditing security-sensitive configurations
     * - Providing user feedback about configuration precedence
     *
     * **Value Sources (in priority order)**:
     * - [ValueSource.USER] - Explicitly provided via command-line arguments (highest priority)
     * - [ValueSource.ENVIRONMENT] - Read from environment variable via `.fromEnv()`
     * - [ValueSource.DEFAULT] - Using default value from `.default()`
     * - [ValueSource.MISSING] - No value provided (nullable options only)
     *
     * **Usage Example:**
     * ```kotlin
     * class MyApp : Arguments() {
     *     val port by option("--port").int().default(8080)
     *     val apiKey by option("--api-key").fromEnv("API_KEY")
     * }
     *
     * fun main(args: Array<String>) {
     *     val app = MyApp().parse(args) ?: return
     *
     *     when (app.valueSourceOf(app::port)) {
     *         ValueSource.USER -> println("Port from command line: ${app.port}")
     *         ValueSource.DEFAULT -> println("Using default port: ${app.port}")
     *         ValueSource.ENVIRONMENT -> println("Port from env: ${app.port}")
     *         ValueSource.MISSING -> println("No port configured")
     *     }
     *
     *     // Check if API key came from secure source
     *     if (app.valueSourceOf(app::apiKey) == ValueSource.ENVIRONMENT) {
     *         println("API key loaded securely from environment")
     *     }
     * }
     * ```
     *
     * @param T The type of the property value
     * @param property Property reference obtained using `::propertyName` syntax
     * @return The [ValueSource] indicating how the property's value was obtained
     * @throws IllegalArgumentException If the property is not an Argos-managed option or is a domain property (domains don't support value source tracking)
     * @see ValueSource For detailed source type documentation
     */
    fun <T> valueSourceOf(property: KProperty0<T>): ValueSource {
        return valueSources[property.name]
            ?: throw IllegalArgumentException("Property '${property.name}' is not managed by Argos or may be a domain property (domains are not supported for valueSource tracking).")
    }


    // ======== Rule registration helpers (called from hooks) ========

    internal fun addRequiredMin(owner: String, min: Int, scope: Set<String>?) {
        ensureConfigMutable()
        val key = RuleKey.RequiredMinKey(owner, min, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigException("Duplicate required/atLeast rule for '$owner' in scope $scope")
        requiredMins += RequiredMin(owner, min, limitToDomains = scope)
    }

    internal fun addConditional(
        owner: String,
        kind: CondKind,
        refs: Set<String>,
        predicateRef: String?,
        predicate: ((Any?) -> Boolean)?,
        scope: Set<String>?
    ) {
        ensureConfigMutable()
        if (kind != CondKind.VALUE_PREDICATE && kind != CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE && refs.isEmpty())
            throw ConfigException("Conditional refs cannot be empty for '$owner'")
        val key = RuleKey.ConditionalKey(owner, kind, canonNames(refs), predicateRef, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigException("Duplicate conditional for '$owner' in scope $scope")
        requirements += ConditionalRequired(
            owner,
            kind,
            canonNames(refs),
            predicateRef,
            predicate,
            limitToDomains = scope
        )
    }

    internal fun addGroup(kind: String, owners: Set<String>, scope: Set<String>?) {
        ensureConfigMutable()
        val canon = canonNames(owners)
        if (canon.size < 2) throw ConfigException("$kind requires at least 2 distinct owners")
        val key = RuleKey.GroupKey(kind, canon, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigException("Duplicate group rule for $canon in scope $scope")
        groups += when (kind) {
            "exactly-one" -> GroupRule.ExactlyOne(canon, scope)
            "at-most-one" -> GroupRule.AtMostOne(canon, scope)
            "at-least-one" -> GroupRule.AtLeastOne(canon, scope)
            else -> throw ConfigException("Unknown group kind '$kind'")
        }
    }

    internal fun addConflicts(owners: Set<String>, scope: Set<String>?) {
        ensureConfigMutable()
        val canon = canonNames(owners)
        if (canon.size < 2) throw ConfigException("conflicts requires at least 2 distinct owners")
        val key = RuleKey.ConflictsKey(canon, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigException("Duplicate conflicts rule for $canon in scope $scope")
        conflicts += Conflicts(canon, limitToDomains = scope)
    }

    // ======== Validation ========

    private fun resolveDomainInheritance() {
        // Step 1: Validate inheritance references
        for ((domainId, builderInfo) in domainBuilders) {
            for (inheritedDomainPropName in builderInfo.inheritedDomains) {
                val inheritedDomainId = domainBuilders.values
                    .find { it.propName == inheritedDomainPropName }?.id
                if (inheritedDomainId == null) {
                    throw ConfigException("Domain '$domainId' inherits from unknown domain property '$inheritedDomainPropName'")
                }

                // Track fragment-to-inheritor relationships
                val inheritedBuilder = domainBuilders[inheritedDomainId]!!
                if (inheritedBuilder.isFragment) {
                    fragmentToInheritors.getOrPut(inheritedDomainId) { mutableSetOf() }.add(domainId)
                }
            }
        }

        // Step 2: Apply inherited constraints to concrete domains
        for ((domainId, builderInfo) in domainBuilders) {
            if (!builderInfo.isFragment) {
                // Apply inherited rule hooks from all inherited domains/fragments
                for (inheritedDomainPropName in builderInfo.inheritedDomains) {
                    val inheritedDomainId = domainBuilders.values
                        .find { it.propName == inheritedDomainPropName }?.id!!
                    val inheritedBuilder = domainBuilders[inheritedDomainId]!!

                    // Apply inherited constraints to this concrete domain
                    inheritedBuilder.ruleHooks.forEach { hook ->
                        hook(this, domainId) // Apply with this domain's ID as scope
                    }
                }

                // Apply this domain's own constraints
                builderInfo.ruleHooks.forEach { hook ->
                    hook(this, domainId)
                }

                // Register this concrete domain for CLI
                registerDomain(
                    id = domainId,
                    label = builderInfo.label,
                    description = builderInfo.description,
                    aliases = builderInfo.aliases,
                    propName = builderInfo.propName
                )
            }
        }

        // Step 3: Create expanded domain props map for onlyInDomains() processing
        // (We can't modify the immutable declaredDomainProps, so we'll use this map in validation)
        expandedDomainProps.clear() // Clear any previous state

        for ((ownerName, spec) in specs) {
            spec.declaredDomainProps?.let { domainProps ->
                val expandedProps = mutableSetOf<String>()

                for (domainPropName in domainProps) {
                    val domainBuilder = domainBuilders.values.find { it.propName == domainPropName }
                    if (domainBuilder != null && domainBuilder.isFragment) {
                        // This is a fragment - expand to all concrete domains that inherit from it
                        val inheritors = fragmentToInheritors[domainBuilder.id] ?: emptySet()
                        val inheritorPropNames = inheritors.mapNotNull { inheritedDomainId ->
                            domainBuilders[inheritedDomainId]?.propName
                        }
                        expandedProps.addAll(inheritorPropNames)
                    } else {
                        // This is a concrete domain - keep as is
                        expandedProps.add(domainPropName)
                    }
                }

                expandedDomainProps[ownerName] = expandedProps
            }
        }
    }

    private fun ensureConfigMutable() {
        if (configValidated) throw ConfigException("Configuration is frozen; modify DSL before first parse.")
    }

    private fun validateConfig() {
        if (configValidated) return

        // Resolve domain inheritance and fragments
        resolveDomainInheritance()

        // Validate that all refs point to existing specs
        run {
            val owners = specs.keys
            requiredMins.forEach {
                if (it.owner !in owners) throw ConfigException("Unknown option '${it.owner}' referenced in require/atLeast")
            }
            requirements.forEach { r ->
                if (r.targetOwner !in owners)
                    throw ConfigException("Unknown option '${r.targetOwner}' referenced in conditional requirement")
                r.refs.forEach { ref ->
                    if (ref !in owners) throw ConfigException("Unknown option '$ref' referenced in conditional requirement for '${r.targetOwner}'")
                }
                r.predicateRef?.let {
                    if (it !in owners) throw ConfigException("Unknown option '$it' used as predicate in conditional requirement for '${r.targetOwner}'")
                }
            }
            conflicts.forEach { c ->
                c.owners.forEach { if (it !in owners) throw ConfigException("Unknown option '$it' referenced in conflicts()") }
            }
            groups.forEach { g ->
                g.owners.forEach { if (it !in owners) throw ConfigException("Unknown option '$it' referenced in group rule") }
            }
        }

        // onlyInDomains(...) checks - use expanded domain props if available
        specs.values.filter { it.declaredDomainProps != null }.forEach { spec ->
            val propsToProcess = expandedDomainProps[spec.ownerName] ?: spec.declaredDomainProps!!
            spec.declaredDomains = propsToProcess.map {
                val id = domainPropNameToId[it]
                if (id == null) {
                    val label = spec.baseSwitch
                    throw ConfigException("Item $label requested property '$it' which is not a domain property.")
                }
                id
            }.toSet()
        }

        // Normalize RequiredMin limits
        for (i in requiredMins.indices) {
            requiredMins[i] = requiredMins[i].copy(
                limitToDomains = requiredMins[i].limitToDomains?.map { prop ->
                    domainPropNameToId[prop] ?: prop
                }?.toSet()
            )
        }

        // Normalize ConditionalRequired limits
        for (i in requirements.indices) {
            val lim = requirements[i].limitToDomains?.map { prop ->
                domainPropNameToId[prop] ?: prop
            }?.toSet()
            requirements[i] = requirements[i].copy(limitToDomains = lim)
        }

        // Normalize Conflicts limits
        for (i in conflicts.indices) {
            conflicts[i] = conflicts[i].copy(
                limitToDomains = conflicts[i].limitToDomains?.map { prop ->
                    domainPropNameToId[prop] ?: prop
                }?.toSet()
            )
        }

        // Normalize Group limits
        for (i in groups.indices) {
            groups[i] = when (val g = groups[i]) {
                is GroupRule.ExactlyOne -> g.copy(limitToDomains = g.limitToDomains?.map {
                    domainPropNameToId[it] ?: it
                }?.toSet())

                is GroupRule.AtMostOne -> g.copy(limitToDomains = g.limitToDomains?.map { domainPropNameToId[it] ?: it }
                    ?.toSet())

                is GroupRule.AtLeastOne -> g.copy(limitToDomains = g.limitToDomains?.map {
                    domainPropNameToId[it] ?: it
                }?.toSet())
            }
        }

        // Positional schema per domain
        fun validateSchema(active: List<Spec<*>>, label: String) {
            val pos = active.filter { it.positionalKind != null }.sortedBy { it.positionalSeq }
            val repeatables =
                pos.filter { it.positionalKind == PositionalKind.LIST || it.positionalKind == PositionalKind.SET }
            if (repeatables.size > 1) throw ConfigException("Multiple repeatable positionals in $label schema are not allowed")
            if (repeatables.size == 1) {
                val tailSeq = repeatables.first().positionalSeq!!
                val lastSeq = pos.maxOf { it.positionalSeq!! }
                if (tailSeq != lastSeq) throw ConfigException("Repeatable positional must be the last positional in $label schema")
            }
        }
        if (domainIds.isEmpty()) {
            validateSchema(specs.values.toList(), "global")
        } else {
            for (d in domainIds) {
                val active = specs.values.filter {
                    val dd = it.declaredDomains
                    it.positionalKind != null && (dd == null || d in dd)
                }
                if (active.isNotEmpty()) validateSchema(active, "domain '$d'")
            }
        }

        configValidated = true
    }

    // ======== Snapshot (unchanged structures) ========

    /**
     * Creates an immutable snapshot of the argument specification structure.
     *
     * This method generates a complete representation of all options, domains, constraints, and settings
     * configured for this Arguments instance. Useful for serialization, introspection, or generating
     * documentation from the argument specification.
     *
     * @return Snapshot containing all argument specifications, domains, and constraints
     * @throws ConfigException if the configuration is invalid
     */
    fun snapshot(): Snapshot {
        validateConfig()
        val settings = Settings(
            appName = appName,
            appDescription = appDescription,
            defaultLongPrefix = defaultLongPrefix,
            clusterChar = clusterChar,
            valueSeparators = valueSeparators,
            unknownOptionsAsPositionals = unknownOptionsAsPositionals,
            argumentSeparator = argumentSeparator
        )
        val domainsSnap = domainDefs.map { DomainInfo(it.id, it.label, it.description, it.aliases) }

        fun minRequiredGlobalFor(owner: String): Int =
            requiredMins.filter { it.owner == owner && it.limitToDomains == null }.maxOfOrNull { it.min } ?: 0

        val optionsSnap = specs.values.filter { it.switches.isNotEmpty() }.map {
            SpecInfo(
                ownerName = it.ownerName,
                switches = it.switches.toList(),
                booleanFlag = it.booleanFlag,
                negationPrefix = it.negationPrefix,
                requiresValue = it.requiresValue,
                repeatable = it.repeatable,
                declaredDomains = it.declaredDomains,
                help = it.help,
                expectedDesc = it.expectedDesc,
                minRequiredGlobal = minRequiredGlobalFor(it.ownerName),
                hidden = it.hidden,
                defaultValue = it.defaultValue,
                envVar = it.envVar
            )
        }

        val posSnap = specs.values.filter { it.switches.isEmpty() && it.positionalKind != null }.map {
            PositionalInfo(
                name = it.ownerName,
                kind = it.positionalKind!!,
                repeatable = it.positionalKind == PositionalKind.LIST || it.positionalKind == PositionalKind.SET,
                declaredDomains = it.declaredDomains,
                help = it.help,
                expectedDesc = it.expectedDesc,
                seq = it.positionalSeq ?: Int.MAX_VALUE,
                minRequiredGlobal = minRequiredGlobalFor(it.ownerName)
            )
        }.sortedBy { it.seq }

        val groupsSnap = groups.map {
            when (it) {
                is GroupRule.ExactlyOne -> GroupRuleInfo("exactly-one", it.owners)
                is GroupRule.AtMostOne -> GroupRuleInfo("at-most-one", it.owners)
                is GroupRule.AtLeastOne -> GroupRuleInfo("at-least-one", it.owners)
            }
        }
        val conflictsSnap = conflicts.map { it.owners }
        val condSnap = requirements.map {
            ConditionalInfo(
                targetOwner = it.targetOwner,
                kind = when (it.kind) {
                    CondKind.ANY_PRESENT -> "if-any-present"
                    CondKind.ALL_PRESENT -> "if-all-present"
                    CondKind.ANY_ABSENT -> "if-any-absent"
                    CondKind.ALL_ABSENT -> "if-all-absent"
                    CondKind.VALUE_PREDICATE -> "if-value"
                    CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE -> "allow-only-if-value"
                },
                refs = it.refs,
                predicateRef = it.predicateRef,
                limitToDomains = it.limitToDomains
            )
        }
        return Snapshot(settings, domainsSnap, optionsSnap, posSnap, groupsSnap, conflictsSnap, condSnap, errorLines)
    }

    // ============================ Internal helpers ============================

    internal fun getDefaultNegationPrefix(): String = defaultNegationPrefix

    // ============================ Public factories ============================

    /**
     * Creates a command-line option with the specified switches.
     *
     * This is the primary factory method for defining options. Options are identified by one or more switches
     * (e.g., `-f`, `--file`) and can be configured using fluent builder methods for type conversion, validation,
     * constraints, and more. If no switches are provided, the option name is auto-generated from the property name.
     *
     * **Basic Usage:**
     * ```kotlin
     * val verbose by option("-v", "--verbose").bool()
     * val count by option("--count").int().required()
     * val files by option("--file").list()
     * ```
     *
     * **Common Patterns:**
     * - Type refinement: `.int()`, `.bool()`, `.enum<E>()`, `.keyvalue()`
     * - Nullability: `.required()`, `.default(value)`
     * - Collections: `.list()`, `.set()`
     * - Multi-value: `.arity(n)`
     * - Validation: `.validate(message) { predicate }`
     * - Constraints: `.requireIfAnyPresent(::other)`, `.conflictsWith(::other)`
     *
     * @param switches One or more option switches (e.g., "-f", "--file"). If empty, auto-generated from property name
     * @return An [OptionBuilder] for configuring the option
     * @see positional For positional arguments
     * @see OptionBuilder For all available configuration methods
     */
    protected fun option(vararg switches: String): OptionBuilder<String?> =
        OptionBuilder(
            switches = switches.toList(),
            repeatable = false,
            helpText = null,
            domainProps = null,
            requiresValue = true,
            single = { raw -> if (!raw.present) SingleResult.Missing else SingleResult.Value(raw.value) },
            booleanFlag = false,
                negationPrefix = null,
            positionalKind = null,
            positionalSeq = null,
            envVar = null,
            expectedDesc = null,
            eager = false
        )

    /**
     * Creates a positional argument that captures values based on position rather than switches.
     *
     * Positional arguments are matched by their position in the command line after all options are consumed.
     * They don't require a switch (like `--file`) and are identified solely by order. Positional arguments
     * are automatically assigned sequential positions in the order they are declared in the class.
     *
     * **Basic Usage:**
     * ```kotlin
     * val inputFile by positional()                    // First positional
     * val outputFile by positional()                   // Second positional
     * val sources by positional().list()               // Collect remaining
     * ```
     *
     * **Usage Patterns:**
     * ```bash
     * myapp input.txt output.txt           # Two positional arguments
     * myapp --verbose input.txt output.txt # Options before positionals
     * myapp input.txt src1.txt src2.txt    # First is inputFile, rest collected by list
     * ```
     *
     * **Important Notes:**
     * - Positionals are matched AFTER all options are processed
     * - Order of declaration determines matching order
     * - Use `.list()` or `.set()` to collect multiple remaining positionals
     * - Use `.required()` to enforce that a positional must be provided
     *
     * @return An [OptionBuilder] configured as a positional argument
     * @see option For named options with switches
     */
    protected fun positional(): OptionBuilder<String?> =
        OptionBuilder(
            switches = emptyList(),
            repeatable = false,
            helpText = null,
            domainProps = null,
            requiresValue = true,
            single = { raw -> if (!raw.present) SingleResult.Missing else SingleResult.Value(raw.value) },
            booleanFlag = false,
                negationPrefix = null,
            positionalKind = PositionalKind.SINGLE,
            positionalSeq = null,
            envVar = null,
            expectedDesc = null,
            eager = false
        )

    /**
     * Creates a domain (subcommand) that groups related functionality and constraints.
     *
     * Domains enable subcommand-style CLIs where different commands have different options and behavior.
     * When a domain is selected on the command line, only that domain's constraints are enforced, allowing
     * options to have different requirements in different contexts. Domains can also serve as constraint
     * templates (fragments) that other domains can inherit from.
     *
     * **Basic Domain Usage:**
     * ```kotlin
     * val buildDomain by domain("build")
     *     .label("Build the project")
     *     .help("Compiles and packages the project")
     *     .aliases("compile", "make")
     *     .required(::target)
     *     .conflicts(::clean, ::incremental)
     * ```
     *
     * **Fragment (Template) Usage:**
     * ```kotlin
     * // Define reusable constraint template
     * val commonTest by domain(fragment = true)
     *     .required(::testDir)
     *     .atMostOne(::parallel, ::sequential)
     *
     * // Apply template to actual domains
     * val unitTest by domain("unit-test").inherits(::commonTest)
     * val integrationTest by domain("integration-test").inherits(::commonTest)
     * ```
     *
     * **Command Line Usage:**
     * ```bash
     * myapp build --target release          # Activates build domain
     * myapp test --test-dir tests/          # Activates test domain
     * myapp compile --target debug          # "compile" alias for build
     * ```
     *
     * @param id The domain identifier used on the command line. If null, auto-generated from property name
     * @param fragment If true, creates a constraint template (not selectable on command line) that other domains can inherit from
     * @return A [DomainBuilder] for configuring the domain with constraints and metadata
     * @see DomainBuilder For all available domain configuration methods
     */
    protected fun domain(id: String? = null, fragment: Boolean = false): DomainBuilder = DomainBuilder(id, fragment)

    /**
     * Creates a standard help option that displays usage information and optionally exits.
     *
     * The help option is a boolean flag that, when triggered, prints the application's usage information
     * including all options, their descriptions, and constraints. By default, it uses switches `-h` and
     * `--help`, and exits the program after printing help. The option is eager-evaluated, meaning it's
     * processed before other options and can trigger early exit.
     *
     * **Basic Usage:**
     * ```kotlin
     * class MyApp : Arguments() {
     *     val help by help()  // Standard -h/--help
     *     // ... other options ...
     * }
     * ```
     *
     * **Custom Switches:**
     * ```kotlin
     * val help by help("--usage", "--help")  // Custom switches
     * val help by help(exitOnTrigger = false)  // Don't exit, just print
     * ```
     *
     * @param switches The command-line switches for the help option (default: "-h", "--help")
     * @param exitOnTrigger If true, exits the program with status 0 after printing help (default: true)
     * @return A non-nullable boolean option builder configured as a help option
     * @see printUsage For manually printing usage information
     * @see version For creating a version option
     */
    protected fun help(
        vararg switches: String = arrayOf("-h", "${defaultLongPrefix()}help"),
        exitOnTrigger: Boolean = true
    ): NonNullableOptionBuilder<Boolean> =
        option(*switches).bool().eager().default(false).help("Show this help message").onValue {
            printUsage()
            if (exitOnTrigger) OsBound.exit(0)
        }

    /**
     * Creates a standard version option that displays version information and optionally exits.
     *
     * The version option is a boolean flag that, when triggered, prints the specified version string
     * and optionally exits the program. By default, it uses the `--version` switch and exits after
     * printing. The option is eager-evaluated, meaning it's processed before other options and can
     * trigger early exit.
     *
     * **Basic Usage:**
     * ```kotlin
     * class MyApp : Arguments() {
     *     val version by version("1.0.0")  // Standard --version
     *     // ... other options ...
     * }
     * ```
     *
     * **Custom Switches:**
     * ```kotlin
     * val version by version("1.0.0", "-v", "--version")  // Multiple switches
     * val version by version("1.0.0", exitOnTrigger = false)  // Don't exit
     * ```
     *
     * @param version The version string to display when the option is triggered
     * @param switches The command-line switches for the version option (default: "--version")
     * @param exitOnTrigger If true, exits the program with status 0 after printing version (default: true)
     * @return A non-nullable boolean option builder configured as a version option
     * @see help For creating a help option
     */
    protected fun version(
        version: String,
        vararg switches: String = arrayOf("${defaultLongPrefix()}version"),
        exitOnTrigger: Boolean = true
    ): NonNullableOptionBuilder<Boolean> =
        option(*switches).bool().eager().default(false).help("Show version information").onValue {
            println(version)
            if (exitOnTrigger) OsBound.exit(0)
        }

    // ======== Spec model ========


    internal data class Spec<T>(
        val ownerName: String,
        val switches: List<String>,
        val baseSwitch: String,
        val repeatable: Boolean,
        val help: String?,
        val declaredDomainProps: Set<String>?,
        var declaredDomains: Set<String>?,
        val requiresValue: Boolean,
        val valueKind: ValueKind,
        val arity: Int = 1,
        val booleanFlag: Boolean,
        val negationPrefix: String?,
        val positionalKind: PositionalKind?,
        val positionalSeq: Int?,
        val envVar: String?,
        val expectedDesc: List<String>?,
        val eager: Boolean,
        val hidden: Boolean = false,
        val defaultValue: String? = null,
        val parseMany: (List<Raw>) -> ParseOutcome<T>,
        val single: (Raw) -> SingleResult<T?>,
        val validators: List<Pair<String, (Any?) -> Boolean>> = emptyList(),
        val collectionValidators: List<Pair<String, (Any?) -> Boolean>> = emptyList()
    )

    // ---- Public info ----

    /**
     * Returns the terminal instance used for formatted output.
     * This is either the user-provided terminal or auto-detected based on ANSI support.
     * @return The terminal instance used for output formatting
     */
    fun terminal() = terminal

    /**
     * Sets the terminal instance used for formatted output. This is useful for testing or when you
     * want to use a different terminal implementation, i.e. when you don't want to use ANSI based
     * on command line arguments.
     * @param term The terminal instance to use for output formatting
     */
    fun setTerminal(term: Terminal) {
        terminal = term
    }

    /**
     * Returns the currently selected domain/subcommand, or null if no domain is active.
     * This reflects which subcommand was parsed from the command line.
     * @return The ID of the selected domain, or null if no domain is selected
     */
    fun selectedDomain() = selectedDomain

    private fun updateDomainValueSources() {
        // Update value sources for all domain properties based on current selection
        for ((propName, domainId) in domainPropNameToId) {
            valueSources[propName] = if (selectedDomain == domainId) ValueSource.USER else ValueSource.MISSING
        }
    }

    // ============================ Parsing ============================


    /**
     * Prints the basic usage information for this command-line application.
     * Shows usage line, options, and domains but not detailed constraint information.
     */
    fun printUsage() = Help.printUsage(this)

    /**
     * Prints extended usage information including constraint details.
     * Shows usage line, options, domains, and detailed constraint information.
     */
    fun printUsageExtended() = Help.printUsageWithConstraints(this)

    private fun Char.isSymbol(): Boolean = !isLetterOrDigit() && this != '_' && !isWhitespace()

    // ======== Argument File Support ========

    private fun parseArgumentFile(content: String): List<String> {
        return content
            .lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }  // Skip blank lines and # comments
            .joinToString(" ")           // Join all lines
            .split(' ', '\t', '\n', '\r')   // Split by whitespace
            .filter { it.isNotEmpty() }      // Remove empty tokens
    }

    private fun expandArgumentFiles(args: Array<String>): Array<String> {
        if (argumentFilePrefix == null) return args

        return args.flatMap { arg ->
            if (arg.startsWith(argumentFilePrefix) && arg.length > 1) {
                val filePath = arg.substring(1)
                val content = OsBound.readFile(filePath)
                    ?: throw ParseError("Cannot read argument file: $filePath")
                parseArgumentFile(content)
            } else {
                listOf(arg)
            }
        }.toTypedArray()
    }

    /**
     * Parses command-line arguments and throws a ParseError if parsing fails.
     * This method validates configuration, expands argument files, and performs
     * complete argument parsing including constraint validation.
     *
     * @param argv Array of command-line arguments to parse
     * @return This Arguments instance for method chaining
     * @throws ParseError if parsing fails due to invalid arguments or constraint violations
     */
    fun parseWithException(argv: Array<String>): Arguments {
        validateConfig()

        // Expand argument files first
        val processedArgs = expandArgumentFiles(argv)

        values.clear(); selectedDomain = null
        // Clear all stored values and value sources
        errorLines.clear()

        // Callback execution order - store individual callback executions with their converted values
        val callbackExecutions = mutableListOf<Pair<String, Any?>>()

        // Track raw values for validation (while keeping immediate conversion for callbacks)
        val rawsByOwner = mutableMapOf<String, MutableList<Raw>>()

        // Helper function to track callback position (revert to simple tracking)

        fun fail(failText: String) {
            errorLines += failText
            if (!aggregateErrors)
                throw ParseError(failText.stripStyles())
        }

        // Track option occurrences for duplicate validation
        val optionOccurrences = mutableMapOf<String, Int>()
        // Track which options were actually provided by user (not just initialized)
        val userProvidedOptions = mutableSetOf<String>()

        // Helper function to convert and accumulate values immediately
        fun storeConvertedValue(
            owner: String,
            present: Boolean,
            rawValue: String?,
            argvIndex: Int?,
            negated: Boolean = false
        ) {
            if (!present) return

            val spec = specs[owner]!!

            // Track occurrences for duplicate checking
            optionOccurrences[owner] = (optionOccurrences[owner] ?: 0) + 1
            // Mark as user-provided
            userProvidedOptions.add(owner)

            val raw = Raw(present, rawValue, argvIndex, negated)

            // Track raw values for later validation
            rawsByOwner.getOrPut(owner) { mutableListOf() }.add(raw)

            // Call the single conversion function
            when (val singleResult = spec.single(raw)) {
                is SingleResult.Value<*> -> {
                    // For immediate conversion, store the value and track callback
                    // Validation will happen when parseMany is called at the end
                    when (spec.valueKind) {
                        ValueKind.SINGLE -> {
                            values[owner] = singleResult.value
                        }


                        ValueKind.LIST -> {
                            @Suppress("UNCHECKED_CAST")
                            val currentList = values[owner] as? MutableList<Any?> ?: mutableListOf()
                            currentList.add(singleResult.value)
                            values[owner] = currentList
                        }

                        ValueKind.SET -> {
                            @Suppress("UNCHECKED_CAST")
                            if (singleResult.value is KeyValue) {
                                // For KeyValue, use a temporary list to collect values, convert to KeyValueSet later
                                val currentList = values[owner] as? MutableList<Any?> ?: mutableListOf()
                                currentList.add(singleResult.value)
                                values[owner] = currentList
                            } else {
                                // For other types, use standard LinkedHashSet
                                val currentSet = values[owner] as? LinkedHashSet<Any?> ?: LinkedHashSet()
                                currentSet.add(singleResult.value)
                                values[owner] = currentSet
                            }
                        }
                    }

                    // Update value source
                    valueSources[owner] = singleResult.source

                    // Track callback with converted value
                    val callback = callbackRegistry[owner]
                    if (callback != null) {
                        callbackExecutions.add(owner to singleResult.value)
                    }
                }

                is SingleResult.Missing -> {
                    // No conversion needed for missing values
                }

                is SingleResult.Invalid -> // Invalid conversion - use existing fail() function
                    fail(
                        invalidValueFragments(
                            singleResult.original ?: rawValue,
                            spec.baseSwitch,
                            argvIndex,
                            spec.expectedDesc
                        )
                    )
            }
        }

        val cluster = clusterChar

        fun ownerForExact(token: String): String? = switchToOwner[token]
        fun tokenHasKnownPrefix(token: String): Pair<Boolean, String> {
            if (token == "--") return true to "--"
            val prefix = token.takeWhile { !it.isLetterOrDigit() && it != '_' }
            val known = prefix.isNotEmpty() && (prefix in knownPrefixes || prefix == defaultLongPrefix)
            return known to prefix
        }

        /**
         * Checks if a token represents a known option, handling attached values for short options.
         * This properly recognizes "-t3" as option "-t" with attached value "3".
         */
        fun isKnownOption(token: String): Boolean {
            // Check exact match first (for long options and short options without attached values)
            if (ownerForExact(token) != null) return true

            // Check for long options with attached values (--opt=value)
            val beforeSeparator = token.substringBeforeAny(valueSeparators)
            if (beforeSeparator != token && ownerForExact(beforeSeparator) != null) return true

            // Check for short options with attached values (-t3)
            val (hasKnownPrefix, prefix) = tokenHasKnownPrefix(token)
            if (hasKnownPrefix && prefix.length == 1) { // Only single-dash prefixes
                val rest = token.substring(prefix.length)
                if (rest.isNotEmpty()) {
                    val firstChar = rest[0]
                    val shortToken = "$prefix$firstChar"
                    if (ownerForExact(shortToken) != null) return true
                }
            }

            return false
        }

        /**
         * Checks if the next argument should be consumed as a value for an option with requiresValue(false).
         * Uses type-aware validation based on the option's expected type.
         */
        fun shouldConsumeAsOptionalValue(next: String?, spec: Spec<*>): Boolean {
            if (next == null) return false

            // Never consume "--"
            if (next == "--") {
                return false
            }

            // Options that ignore their input values (like count options) should never consume values
            // Test this by checking if the option returns the same result for different inputs
            val testEmpty = Raw(true, "", null)
            val testNext = Raw(true, next, null)
            val emptyResult = spec.single(testEmpty)
            val nextResult = spec.single(testNext)

            // If both results are the same value (e.g., both return 1 for count options),
            // then this option doesn't care about its input and should work in flag mode
            // Exception: boolean options should still consume valid boolean literals
            if (!spec.booleanFlag &&
                emptyResult is SingleResult.Value && nextResult is SingleResult.Value &&
                emptyResult.value == nextResult.value
            ) {
                return false
            }

            // For typed options (bool, int, double, enum, etc.), only check type compatibility
            // Don't worry if the value looks like a switch - if it parses as the expected type, consume it
            if (spec.booleanFlag) {
                // For boolean options, only consume valid boolean literals
                val parsed = parseBooleanLiteral(next)
                val result = parsed != null
                return result
            } else {
                // For all other types (int, double, enum, string), test if it can be parsed
                val result = nextResult

                // Special case for string options: avoid consuming known switches
                if (result is SingleResult.Value && result.value is String) {
                    return !isKnownOption(next)
                }

                return result !is SingleResult.Invalid
            }
        }

        fun suggestFor(prefix: String, name: String): String? {
            if (!didYouMean || unknownOptionsAsPositionals) return null
            val candidates =
                switchToOwner.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.distinct()
            if (candidates.isEmpty()) return null
            val lower = name.lowercase()
            var best: String? = null
            var bestDist = Int.MAX_VALUE
            for (cand in candidates) {
                val d = EditDistance.damerauLevenshtein(lower, cand.lowercase(), didYouMeanMax)
                if (d < bestDist) {
                    bestDist = d; best = cand
                }
            }
            val threshold = max(1, name.length / 3)
            return if (best != null && (bestDist <= didYouMeanMax || bestDist <= threshold)) "$prefix$best" else null
        }

        var i = 0
        var afterDoubleDash = false
        val positionalTokens = mutableListOf<Pair<String, Int>>()
        var eagerTriggered = false
        val consumedIndices = mutableSetOf<Int>()

        fun handleBare(tok: String, idx: Int) {
            // Skip tokens that were already consumed by options
            if (idx in consumedIndices) {
                return
            }

            val (looksLikeOption, _) = tokenHasKnownPrefix(tok)
            if (!looksLikeOption && domainIds.isNotEmpty() && selectedDomain == null) {
                val canon = when (tok) {
                    in domainIds -> tok
                    in aliasToId -> aliasToId[tok]
                    else -> null
                }
                if (canon != null) {
                    selectedDomain = canon
                    // Update domain value sources
                    updateDomainValueSources()
                    return
                }
                fail(
                    translate("Unknown domain '{domain}' at position {position}. Expected: {expected}")
                        .replace("{domain}", tok.asDomain())
                        .replace("{position}", idx.asPosition())
                        .replace("{expected}", domainIds.asDomainList())
                )
                return
            }
            positionalTokens += tok to idx
        }

        while (i < processedArgs.size) {
            val tok = processedArgs[i]
            val idx = i

            if (afterDoubleDash) {
                handleBare(tok, idx); i++; continue
            }
            if (tok == "--") {
                afterDoubleDash = true; i++; continue
            }

            if (tok.isNegativeNumberToken()) {
                val shortCandidate = "-${tok[1]}"
                if (ownerForExact(shortCandidate) == null) {
                    handleBare(tok, idx); i++; continue
                }
            }

            val (hasKnownPrefix, prefix) = tokenHasKnownPrefix(tok)
            if (!hasKnownPrefix) {
                handleBare(tok, idx); i++; continue
            }

            ownerForExact(tok)?.let { owner ->
                val spec = specs[owner]!!
                var value: String? = null

                // Handle arity > 1 options (multi-value consumption)
                if (spec.requiresValue && spec.arity > 1) {
                    val requiredValues = spec.arity
                    val raws = mutableListOf<Raw>()

                    for (arityIndex in 0 until requiredValues) {
                        val next = processedArgs.getOrNull(i + 1 + arityIndex)
                        val nextIsOption = next?.let { isKnownOption(it) } == true

                        if (next != null && !nextIsOption) {
                            raws.add(Raw(true, next, i + 1 + arityIndex))
                        } else {
                            fail(
                                translate("Missing value {index} of {total} for {option} at position {position}")
                                    .replace("{index}", (arityIndex + 1).asPosition())
                                    .replace("{total}", requiredValues.asPosition())
                                    .replace("{option}", tok.asOption())
                                    .replace("{position}", idx.asPosition()),
                            )
                        }
                    }

                    // Advance index and mark consumed
                    repeat(requiredValues) {
                        i++
                        consumedIndices.add(i)
                    }

                    // Store Raw objects for validation
                    rawsByOwner.getOrPut(owner) { mutableListOf() }.addAll(raws)

                    // Convert and store immediately using parseMany
                    when (val outcome = spec.parseMany(raws)) {
                        is ParseOutcome.Success -> {
                            // For collection arity options (LIST/SET), accumulate results instead of overwriting
                            if (spec.valueKind == ValueKind.LIST && spec.arity > 1) {
                                if (outcome.value != null) {
                                    @Suppress("UNCHECKED_CAST")
                                    val newSublist = outcome.value as List<Any?>

                                    @Suppress("UNCHECKED_CAST")
                                    val currentList = values[owner] as? MutableList<Any?> ?: mutableListOf()
                                    currentList.addAll(newSublist)
                                    values[owner] = currentList
                                }
                                // If outcome.value is null, keep existing value (which may be null for nullable options)
                            } else if (spec.valueKind == ValueKind.SET && spec.arity > 1) {
                                if (outcome.value != null) {
                                    @Suppress("UNCHECKED_CAST")
                                    val newSubset = outcome.value as Set<Any?>
                                    @Suppress("UNCHECKED_CAST")
                                    if (newSubset.isNotEmpty() && newSubset.first() is KeyValue) {
                                        // For KeyValue, collect in a list for later conversion to KeyValueSet
                                        val currentList = values[owner] as? MutableList<Any?> ?: mutableListOf()
                                        currentList.addAll(newSubset)
                                        values[owner] = currentList
                                    } else {
                                        // For other types, use standard LinkedHashSet
                                        val currentSet = values[owner] as? LinkedHashSet<Any?> ?: LinkedHashSet()
                                        currentSet.addAll(newSubset)
                                        values[owner] = currentSet
                                    }
                                }
                                // If outcome.value is null, keep existing value (which may be null for nullable options)
                            } else {
                                // For scalar arity options, overwrite (existing behavior)
                                values[owner] = outcome.value
                            }

                            if (outcome.value != null)
                                callbackExecutions.add(owner to outcome.value)
                        }

                        is ParseOutcome.Error -> fail(outcome.error)
                    }

                    // Handle eager evaluation for arity > 1 (though rare for multi-value options)
                    if (spec.eager && spec.booleanFlag) {
                        // For arity > 1 boolean flags, check if the first value is true
                        val firstValue = raws.firstOrNull()?.value
                        val parsedTrue = parseBooleanLiteral(firstValue) ?: true
                        if (parsedTrue) {
                            eagerTriggered = true; i = processedArgs.size; continue
                        }
                    }

                    i++; continue
                }

                // Detect if this is a negated option (common for both requiresValue and optional cases)
                val isNegated = if (spec.booleanFlag && spec.negationPrefix != null) {
                    val base = tok.drop(prefix.length)
                    val actualNegPrefix = spec.negationPrefix ?: this.defaultNegationPrefix
                    base.startsWith(actualNegPrefix)
                } else false

                // Handle regular options (arity = 1) - supports --param=value, -pvalue, etc.
                if (spec.requiresValue) {
                    val next = processedArgs.getOrNull(i + 1)
                    val nextIsOption = next?.let { isKnownOption(it) } == true
                    if (next != null && !nextIsOption) {
                        value = next; i++; consumedIndices.add(i)
                        storeConvertedValue(owner, true, value, i, isNegated)
                    } else fail(
                        translate("Missing value for {option} at position {position}")
                            .replace("{option}", tok.asOption())
                            .replace("{position}", idx.asPosition())
                    )
                } else {
                    // Optional value: use type-aware lookahead to decide whether to consume next arg
                    val next = processedArgs.getOrNull(i + 1)

                    val shouldConsume = shouldConsumeAsOptionalValue(next, spec)

                    value = if (shouldConsume) {
                        i++; consumedIndices.add(i); next  // Consume as value
                    } else {
                        // Flag mode: determine value based on option type and negation
                        if (spec.booleanFlag && spec.negationPrefix != null) {
                            // For negatable boolean options, check if this is a negated switch
                            if (isNegated) "false" else null
                        } else {
                            // For other options: use empty string for string options, null for typed options
                            val testRaw = Raw(true, "", null)
                            val emptyResult = spec.single(testRaw)
                            if (emptyResult is SingleResult.Invalid) null else ""
                        }
                    }

                    // Only pass negated=true if an explicit value was provided (not flag mode)
                    val passNegated = isNegated && shouldConsume
                    storeConvertedValue(owner, true, value, if (value == null) idx else i, passNegated)
                }

                if (spec.eager && spec.booleanFlag) {
                    val base = tok.drop(prefix.length)
                    val actualNegPrefix = spec.negationPrefix ?: this.defaultNegationPrefix
                    val parsedTrue =
                        parseBooleanLiteral(value) ?: !(spec.negationPrefix != null && base.startsWith(actualNegPrefix))
                    if (parsedTrue) {
                        eagerTriggered = true; i = processedArgs.size; continue
                    }
                }

                i++; continue
            }

            val rest = tok.drop(prefix.length)
            val sepIndex = rest.indexOfFirst { it in valueSeparators }
            if (sepIndex >= 0) {
                val name = rest.take(sepIndex)
                val value = rest.substring(sepIndex + 1)
                val full = prefix + name
                val owner = ownerForExact(full)
                if (owner == null) {
                    if (unknownOptionsAsPositionals) {
                        handleBare(tok, idx); i++; continue
                    }
                    val hint = suggestFor(prefix, name)
                    if (hint != null) fail(
                        translate("Unknown option {option} at position {position}. Did you mean {hint}?")
                            .replace("{option}", full.asOption())
                            .replace("{position}", idx.asPosition())
                            .replace("{hint}", hint.asOption())
                    ) else fail(
                        translate("Unknown option {option} at position {position}")
                            .replace("{option}", full.asOption())
                            .replace("{position}", idx.asPosition())
                    )
                    i++; continue
                }
                val spec = specs[owner]!!
                storeConvertedValue(owner, true, value, idx)

                if (spec.eager && spec.booleanFlag) {
                    val parsedTrue = parseBooleanLiteral(value) ?: true
                    if (parsedTrue) {
                        eagerTriggered = true; i = processedArgs.size; continue
                    }
                }

                i++; continue
            }

            if (prefix.length == 1 && rest.isNotEmpty()) {
                val firstCh = rest[0]
                val shortToken = "$prefix$firstCh"
                val owner = ownerForExact(shortToken)
                if (owner != null) {
                    val spec = specs[owner]!!
                    if (spec.requiresValue) {
                        var attached = rest.substring(1)
                        if (attached.isNotEmpty() && attached[0] in valueSeparators) attached = attached.substring(1)
                        if (attached.isNotEmpty()) {
                            storeConvertedValue(owner, true, attached, idx)
                        } else {
                            val next = processedArgs.getOrNull(i + 1)
                            val nextIsOption = next?.let { isKnownOption(it) } == true
                            if (next != null && !nextIsOption) {
                                storeConvertedValue(owner, true, next, i + 1); i++; consumedIndices.add(i)
                            } else fail(
                                translate("Missing value for {option} at position {position}")
                                    .replace("{option}", shortToken.asOption())
                                    .replace("{position}", idx.asPosition())
                            )
                        }
                        i++; continue
                    }
                }
            }

            if (cluster != null && prefix.length == 1 && prefix[0] == cluster) {
                if (rest.length > 1) {
                    var unknownFound = false
                    var needsValueNotLast = false
                    var posiScan = 0
                    while (posiScan < rest.length) {
                        val ch = rest[posiScan]
                        val shortToken = "$prefix$ch"
                        val owner = ownerForExact(shortToken)
                        if (owner == null) {
                            unknownFound = true; break
                        }
                        val spec = specs[owner]!!
                        if (spec.requiresValue && posiScan != rest.lastIndex) {
                            needsValueNotLast = true; break
                        }
                        posiScan++
                    }
                    if (unknownFound) {
                        if (unknownOptionsAsPositionals) {
                            handleBare(tok, idx); i++; continue
                        } else {
                            val ch = rest[posiScan]
                            val shortToken = "$prefix$ch"
                            fail(
                                translate("Unknown option {option} in '{cluster}' at position {position}")
                                    .replace("{option}", shortToken.asOption())
                                    .replace("{cluster}", tok.asOption())
                                    .replace("{position}", idx.asPosition())
                            )
                            i++; continue  // Skip the processing phase
                        }
                    }
                    if (needsValueNotLast) {
                        val ch = rest[posiScan]
                        val shortToken = "$prefix$ch"
                        fail(
                            translate("Option {option} requires a value and must be last in '{cluster}' at position {position}")
                                .replace("{option}", shortToken.asOption())
                                .replace("{cluster}", tok.asOption())
                                .replace("{position}", idx.asPosition())
                        )
                        i++; continue
                    }

                    var posi = 0
                    while (posi < rest.length) {
                        val ch = rest[posi]
                        val shortToken = "$prefix$ch"
                        val owner = ownerForExact(shortToken)!!
                        val spec = specs[owner]!!
                        if (!spec.requiresValue) {
                            storeConvertedValue(owner, true, null, idx)
                            if (spec.eager && spec.booleanFlag) {
                                eagerTriggered = true; i = processedArgs.size; break
                            }
                            posi++; continue
                        }
                        val tail = rest.substring(posi + 1)
                        val attached = if (tail.startsWith("=")) tail.drop(1) else tail
                        if (attached.isNotEmpty()) {
                            storeConvertedValue(owner, true, attached, idx)
                        } else {
                            val next = processedArgs.getOrNull(i + 1)
                            val nextIsOption = next?.let { isKnownOption(it) } == true
                            if (next != null && !nextIsOption && next != "--") {
                                storeConvertedValue(owner, true, next, i + 1); i++; consumedIndices.add(i)
                            } else {
                                fail(
                                    translate("Missing value for {option} at position {position}")
                                        .replace("{option}", shortToken.asOption())
                                        .replace("{position}", idx.asPosition())
                                )
                            }
                        }
                        posi = rest.length
                    }
                    i++; continue
                } else {
                    // Handle single character options in cluster mode
                    // Check if the single character is a known option
                    val ch = rest[0]
                    val shortToken = "$prefix$ch"
                    val owner = ownerForExact(shortToken)
                    if (owner == null) {
                        if (unknownOptionsAsPositionals) {
                            handleBare(tok, idx); i++; continue
                        } else {
                            fail(
                                translate("Unknown option {option} at position {position}")
                                    .replace("{option}", shortToken.asOption())
                                    .replace("{position}", idx.asPosition())
                            )
                        }
                    }
                }
            }

            val owner = ownerForExact(prefix + rest)
            if (owner == null) {
                if (unknownOptionsAsPositionals) {
                    handleBare(tok, idx); i++; continue
                }
                val hint = suggestFor(prefix, rest.substringBeforeAny(valueSeparators))
                if (hint != null) {
                    fail(
                        translate("Unknown option {option} at position {position}. Did you mean {hint}?")
                            .replace("{option}", (prefix + rest).asOption())
                            .replace("{position}", idx.asPosition())
                            .replace("{hint}", hint.asOption())
                    )
                } else {
                    fail(
                        translate("Unknown option {option} at position {position}")
                            .replace("{option}", (prefix + rest).asOption())
                            .replace("{position}", idx.asPosition())
                    )
                }
                i++; continue
            }
            val spec = specs[owner]!!
            var value: String? = null

            if (spec.requiresValue) {
                val next = processedArgs.getOrNull(i + 1)
                val nextIsOption = next?.let { isKnownOption(it) } == true
                if (next != null && !nextIsOption) {
                    value = next; i++; consumedIndices.add(i)
                } else fail(
                    translate("Missing value for {option} at position {position}")
                        .replace("{option}", (prefix + rest).asOption())
                        .replace("position", idx.asPosition())
                )
            } else if (spec.booleanFlag) {
                val next = processedArgs.getOrNull(i + 1)
                val parsed = parseBooleanLiteral(next)
                value = if (parsed != null) {
                    i++; consumedIndices.add(i); next
                } else {
                    // Check if this is a negated switch to determine default flag value
                    if (spec.negationPrefix != null) {
                        val base = tok.drop(prefix.length)
                        val actualNegPrefix = spec.negationPrefix ?: this.defaultNegationPrefix
                        if (base.startsWith(actualNegPrefix)) "false" else null
                    } else null
                }
            }
            storeConvertedValue(owner, true, value, if (value == null) idx else i)

            if (spec.eager && spec.booleanFlag) {
                val parsedTrue = parseBooleanLiteral(value) ?: true
                if (parsedTrue) {
                    eagerTriggered = true; i = processedArgs.size; continue
                }
            }

            i++
        }

        // Eager - values already converted during parsing
        if (eagerTriggered) {
            if (errorLines.isNotEmpty()) {
                val msg = errorLines.joinToString(OsBound.termNewLine) { line -> line.stripStyles() }
                throw ParseError(msg)
            }
            // Initialize collections and apply defaults even during eager exit
            for ((owner, spec) in specs) {
                if (values[owner] == null) {
                    when (spec.valueKind) {
                        ValueKind.SINGLE -> {
                            // Try calling single() with Missing to see if there's a default
                            val missingResult = spec.single(Raw(false, null, null))
                            if (missingResult is SingleResult.Value<*>) {
                                values[owner] = missingResult.value
                                valueSources[owner] = missingResult.source
                            }
                        }

                        ValueKind.LIST -> {
                            // For regular list options, initialize empty list
                            // Arity options (arity > 1) keep null for nullable semantics
                            if (spec.arity == 1) {
                                values[owner] = mutableListOf<Any?>()
                            }
                        }

                        ValueKind.SET -> {
                            // For regular set options, initialize empty set
                            // Arity options (arity > 1) keep null for nullable semantics
                            if (spec.arity == 1) {
                                values[owner] = LinkedHashSet<Any?>()
                            }
                        }

                    }
                }
            }
            // Execute callbacks after successful parsing
            executeCallbacks(callbackExecutions)
            return this
        }

        if (domainIds.isNotEmpty() && selectedDomain == null)
            fail(
                translate("A domain is required. Choose one of: {domains}")
                    .replace("{domains}", domainIds.asDomainList())
            )

        val activePos = specs.values
            .filter { it.positionalKind != null && (it.declaredDomains?.let { sd -> selectedDomain in sd } ?: true) }
            .sortedBy { it.positionalSeq }

        // Filter out tokens that were consumed by options
        val availablePositionalTokens = positionalTokens.filter { (_, at) -> at !in consumedIndices }

        var idxTok = 0
        for (s in activePos) {
            if (s.positionalKind == PositionalKind.SINGLE) {
                if (idxTok < availablePositionalTokens.size) {
                    val (tok, at) = availablePositionalTokens[idxTok]
                    storeConvertedValue(s.ownerName, true, tok, at); idxTok++
                }
            }
        }
        val tail =
            activePos.lastOrNull { it.positionalKind == PositionalKind.LIST || it.positionalKind == PositionalKind.SET }
        if (tail != null) {
            while (idxTok < availablePositionalTokens.size) {
                val (tok, at) = availablePositionalTokens[idxTok]
                storeConvertedValue(tail.ownerName, true, tok, at); idxTok++
            }
        } else {
            if (idxTok < availablePositionalTokens.size) {
                // Collect all unexpected positional arguments
                val unexpectedArgs = availablePositionalTokens.drop(idxTok)
                if (unexpectedArgs.size == 1) {
                    val (tok, at) = unexpectedArgs[0]
                    fail(
                        translate("Unexpected positional argument '{argument}' at position {position}")
                            .replace("{argument}", tok.asValue())
                            .replace("{position}", at.asPosition())
                    )
                } else fail(
                    translate("Unexpected positional arguments: {arguments}")
                        .replace("{arguments}", unexpectedArgs.map { it.first }.asValueListUnexpected())
                )
            }
        }

        // Env fallback
        for ((owner, spec) in specs) {
            if (spec.valueKind == ValueKind.SINGLE && values[owner] == null && spec.envVar != null) {
                val envVal = OsBound.getenv(spec.envVar)
                if (envVal != null) {
                    val valueForRaw = if (spec.booleanFlag && envVal.isEmpty()) null else envVal
                    storeConvertedValue(owner, true, valueForRaw, null)
                    // Mark this property as having value from environment
                    valueSources[owner] = ValueSource.ENVIRONMENT
                }
            }
        }

        // Per-option domain restriction + duplicates
        for ((owner, spec) in specs) {
            spec.declaredDomains?.let { allowed ->
                val sd = selectedDomain
                val used = values[owner] != null
                if (used && (sd == null || sd !in allowed)) {
                    if (sd == null) {
                        fail(
                            translate("Option {option} allowed only in: {domains}")
                                .replace("{option}", spec.baseSwitch.asOption())
                                .replace("{domains}", allowed.asDomainList())
                        )
                    } else {
                        fail(
                            translate("Option {option} allowed only in: {domains}, currently selected: {selected}")
                                .replace("{option}", spec.baseSwitch.asOption())
                                .replace("{domains}", allowed.asDomainList())
                                .replace("{selected_domain}", sd.asDomain())
                        )
                    }
                }
            }
            // Check for non-repeatable option duplicates
            if (!spec.repeatable && (optionOccurrences[owner] ?: 0) > 1 && spec.positionalKind == null) {
                fail(
                    translate("Option {option} provided multiple times")
                        .replace("{option}", spec.baseSwitch.asOption())
                )
            }
        }

        // Apply defaults for missing values
        for ((owner, spec) in specs) {
            if (values[owner] == null) {
                // Check if this option has a default value by calling single() with Missing
                when (spec.valueKind) {
                    ValueKind.SINGLE -> {
                        // Try calling single() with Missing to see if there's a default
                        when (val missingResult = spec.single(Raw(false, null, null))) {
                            is SingleResult.Value<*> -> {
                                values[owner] = missingResult.value
                                valueSources[owner] = missingResult.source
                            }

                            is SingleResult.Missing -> {
                                // No default value available
                            }

                            is SingleResult.Invalid -> {
                                // Should not happen for Missing input
                            }
                        }
                    }

                    ValueKind.LIST -> {
                        // Initialize empty list for regular list options
                        // Arity options (arity > 1) keep null for nullable semantics
                        if (spec.arity == 1) {
                            values[owner] = mutableListOf<Any?>()
                        }
                    }

                    ValueKind.SET -> {
                        // Initialize empty set for regular set options
                        // Arity options (arity > 1) keep null for nullable semantics
                        if (spec.arity == 1) {
                            values[owner] = LinkedHashSet<Any?>()
                        }
                    }

                }
            }
        }


        // Run post-conversion validation on typed values
        for ((owner, spec) in specs) {
            val value = values[owner]
            if (value != null) {
                // Helper function to create error fragments
                fun createUserError(
                    messageTemplate: String,
                    // If we want , when this is a list to properly format each enty, provide the collection itself
                    // Still, I believe, since this is user provided data, should be styled as a whole
                    value: String? = null,
                ): String {
                    return translate(messageTemplate)
                        .replace("{option}", spec.baseSwitch.asOption())
                        .replace("{value}", value.asValue())
                }

                when (spec.valueKind) {
                    ValueKind.SINGLE ->
                        // Check if this is an arity option (value is a List)
                        if (value is List<*>) {
                            // Run element validators on each list element (for arity options)
                            for ((messageTemplate, predicate) in spec.validators)
                                for (element in value)
                                    if (!predicate(element))
                                        fail(createUserError(messageTemplate, formatValue(element, argumentSeparator)))
                            // Run collection validators on the entire list (for arity options)
                            for ((messageTemplate, predicate) in spec.collectionValidators)
                                if (!predicate(value))
                                    fail(createUserError(messageTemplate, value.size.toString()))
                        } else
                        // Run element validators for single values
                            for ((messageTemplate, predicate) in spec.validators)
                                if (!predicate(value))
                                    fail(createUserError(messageTemplate, formatValue(value, argumentSeparator)))

                    ValueKind.LIST -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = value as List<Any?>
                        if (spec.arity > 1) {
                            // UNIFIED: For arity options (arity > 1), list is List<List<T>>, flatten for validation
                            @Suppress("UNCHECKED_CAST")
                            val nestedList = list as List<List<Any?>>
                            val flatList = nestedList.flatten()

                            // Run element validators on each flattened element
                            for ((messageTemplate, predicate) in spec.validators) {
                                for (element in flatList) {
                                    if (!predicate(element)) {
                                        fail(createUserError(messageTemplate, formatValue(element, argumentSeparator)))
                                    }
                                }
                            }
                            // Run collection validators on the flattened list
                            for ((messageTemplate, predicate) in spec.collectionValidators) {
                                if (!predicate(flatList)) {
                                    fail(translate(messageTemplate)
                                        .replace("{option}", spec.baseSwitch.asOption())
                                        .replace("{value}", flatList.size.toString())
                                        .replace("{count}", flatList.size.toString()))
                                }
                            }
                        } else {
                            // Regular list options - validate normally
                            // Run element validators on each list element
                            for ((messageTemplate, predicate) in spec.validators)
                                for (element in list)
                                    if (!predicate(element))
                                        fail(createUserError(messageTemplate, formatValue(element, argumentSeparator)))
                            // Run collection validators on the entire list
                            for ((messageTemplate, predicate) in spec.collectionValidators)
                                if (!predicate(list))
                                    fail(translate(messageTemplate)
                                        .replace("{option}", spec.baseSwitch.asOption())
                                        .replace("{value}", list.size.toString())
                                        .replace("{count}", list.size.toString()))
                        }
                    }

                    ValueKind.SET -> {
                        // Convert KeyValue lists to KeyValueSet if needed
                        val actualSet = if (value is MutableList<*> &&
                            value.isNotEmpty() && value.first() is KeyValue
                        ) {
                            @Suppress("UNCHECKED_CAST")
                            val keyValueSet = KeyValueSet(value as List<KeyValue>)
                            // Update the stored value to the KeyValueSet
                            values[owner] = keyValueSet
                            keyValueSet
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            value as Set<Any?>
                        }

                        // Run element validators on each set element
                        for ((messageTemplate, predicate) in spec.validators)
                            for (element in actualSet)
                                if (!predicate(element))
                                    fail(createUserError(messageTemplate, formatValue(element, argumentSeparator)))
                        // Run collection validators on the entire set
                        for ((messageTemplate, predicate) in spec.collectionValidators)
                            if (!predicate(actualSet))
                                fail(translate(messageTemplate)
                                    .replace("{option}", spec.baseSwitch.asOption())
                                    .replace("{value}", actualSet.size.toString())
                                    .replace("{count}", actualSet.size.toString()))
                    }

                }
            }
        }

        for (owner in specs.keys) {
            val spec = specs[owner]!!
            val effMin = requiredMins
                .filter { it.owner == owner && (it.limitToDomains == null || selectedDomain in it.limitToDomains) }
                .maxOfOrNull { it.min } ?: 0
            if (effMin > 0) {
                val count = when (spec.valueKind) {
                    ValueKind.SINGLE -> {
                        if (spec.arity > 1) {
                            // For arity scalar options, value is List<T>? - count number of invocations (same as arity collections)
                            val actualValue = values[owner]
                            (actualValue as? List<*>)?.size ?: 0
                        } else {
                            // For regular scalar options, count as 1 if non-null
                            if (values[owner] != null) 1 else 0
                        }
                    }

                    ValueKind.LIST -> {
                        val actualValue = values[owner]
                        if (spec.arity > 1) {
                            // For arity collection options, value is List<List<T>>? - count number of sublists
                            val size = (actualValue as? List<*>)?.size ?: 0
                            size
                        } else {
                            // For regular list options, count total elements
                            (actualValue as? List<*>)?.size ?: 0
                        }
                    }

                    ValueKind.SET -> {
                        val actualValue = values[owner]
                        if (spec.arity > 1) {
                            // For arity set options, value is Set<List<T>>? - count number of sublists
                            val size = (actualValue as? Set<*>)?.size ?: 0
                            // DEBUG: Print debug info for arity set constraint validation
                            size
                        } else {
                            // For regular set options, count total elements
                            (actualValue as? Set<*>)?.size ?: 0
                        }
                    }

                }
                if (count < effMin) {
                    // Special handling for interactive input options - prompt if missing and required
                    if (spec.expectedDesc != null && spec.expectedDesc.size == 1 && spec.expectedDesc[0] == "input" && effMin == 1 && count == 0) {
                        val inputConfig = inputConfigs[owner]
                        if (inputConfig != null) {
                            val password = promptForInput(
                                inputConfig.prompt,
                                inputConfig.hidden,
                                inputConfig.confirmPrompt,
                                inputConfig.requireConfirmation,
                                inputConfig.mismatchPrompt,
                                inputConfig.maxRetries
                            )
                            if (password != null) {
                                values[owner] = password
                                continue // Skip the error since we got the input
                            }
                        }
                    }

                    if (effMin == 1) fail(
                        translate("Option {option} is required")
                            .replace("{option}", spec.baseSwitch.asOption())
                    )
                    else fail(
                        translate("Option {option} requires at least {min} occurrences (got {count})")
                            .replace("{option}", spec.baseSwitch.asOption())
                            .replace("{min}", effMin.toString())
                            .replace("{count}", count.toString())
                    )
                }
            }
        }

        // Conditionals
        fun present(owner: String) = userProvidedOptions.contains(owner)
        for (req in requirements) {
            val targetSpec = specs[req.targetOwner]!!
            val inScope = req.limitToDomains?.let { selectedDomain in it }
                ?: targetSpec.declaredDomains?.let { selectedDomain in it } ?: true
            if (!inScope) continue
            val needed = when (req.kind) {
                CondKind.ANY_PRESENT -> req.refs.any { present(it) }
                CondKind.ALL_PRESENT -> req.refs.all { present(it) }
                CondKind.ANY_ABSENT -> req.refs.any { !present(it) }
                CondKind.ALL_ABSENT -> req.refs.all { !present(it) }
                CondKind.VALUE_PREDICATE -> {
                    val v = values[req.predicateRef!!]; req.predicate!!.invoke(v)
                }

                CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE -> {
                    // For "allow only if" - error when option is present BUT condition is false
                    if (present(req.targetOwner)) {
                        val v = values[req.predicateRef!!]
                        !req.predicate!!.invoke(v) // Condition must be false for this to be an error
                    } else false // If option not present, no error
                }
            }
            if (req.kind == CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE) {
                if (needed) {
                    fail(
                        translate("Option {option} is not allowed because {reference} has wrong value")
                            .replace("{option}", targetSpec.baseSwitch)
                            .replace("{reference}", specs[req.predicateRef]?.baseSwitch.asOption())
                    )
                }
            } else if (needed && !present(req.targetOwner))
                fail(
                    when (req.kind) {
                        CondKind.ANY_PRESENT -> translate("Option {option} is required because {references} are present")
                        CondKind.ALL_PRESENT -> translate("Option {option} is required because {references} are all present")
                        CondKind.ANY_ABSENT -> translate("Option {option} is required because {references} are absent")
                        CondKind.ALL_ABSENT -> translate("Option {option} is required because {references} are all absent")
                        CondKind.VALUE_PREDICATE -> translate("Option {option} is required because {reference} has wrong value")
                        else -> throw ConfigException("Unsupported conditional kind: ${req.kind}")
                    }
                        .replace("{option}", targetSpec.baseSwitch)
                        .replace("{references}", req.refs.mapNotNull { specs[it]?.baseSwitch }.asOptionList())
                        .replace("{reference}", specs[req.predicateRef]?.baseSwitch.asOption())
                )
        }

        // Conflicts
        for (c in conflicts) {
            val inScope = c.limitToDomains?.let { selectedDomain in it } ?: true
            if (!inScope) continue
            val offenders = c.owners.mapNotNull { specs[it] }.filter { present(it.ownerName) }
                .map { it.baseSwitch }
            if (offenders.size > 1) fail(
                translate("Conflicting options: {options}")
                    .replace("{options}", offenders.asOptionList())
            )
        }

        // Groups
        for (g in groups) {
            val inScope = g.limitToDomains?.let { selectedDomain in it } ?: true
            if (!inScope) continue

            val ownersSpecs = g.owners.mapNotNull { specs[it] }
            val count = ownersSpecs.count { present(it.ownerName) }
            val ownerNames = g.owners.mapNotNull { specs[it] }.map { it.baseSwitch }

            when (g) {
                is GroupRule.ExactlyOne -> if (count != 1)
                    fail(
                        translate("Exactly one of ({options}) must be provided (got {count})")
                            .replace("{options}", ownerNames.asOptionList())
                            .replace("{count}", count.toString())
                    )

                is GroupRule.AtMostOne -> if (count > 1)
                    fail(
                        translate("At most one of ({options}) may be provided (got {count})")
                            .replace("{options}", ownerNames.asOptionList())
                            .replace("{count}", count.toString())

                    )

                is GroupRule.AtLeastOne -> if (count < 1)
                    fail(
                        translate("At least one of ({options}) is required")
                            .replace("{options}", ownerNames.asOptionList())
                    )
            }
        }

        if (errorLines.isNotEmpty()) {
            val capped =
                if (errorLines.size > maxAggregatedErrors)
                    errorLines.take(maxAggregatedErrors) + listOf(
                        translate("... (+{errors} more)")
                            .replace("{errors}", (errorLines.size - maxAggregatedErrors).toString())
                    )
                else errorLines
            val msg = capped.joinToString(OsBound.termNewLine) { line -> line.stripStyles() }
            throw ParseError(msg)
        }

        // Execute callbacks after successful parsing and validation
        executeCallbacks(callbackExecutions)

        return this
    }

    /**
     * Execute callbacks in command-line order with pre-converted values
     */
    private fun executeCallbacks(callbackExecutions: List<Pair<String, Any?>>) {
        // Execute callbacks in the order they appeared on command line
        callbackExecutions.forEach { (owner, convertedValue) ->
            val callback = callbackRegistry[owner]
            if (callback != null && convertedValue != null) {
                try {
                    callback(convertedValue)
                } catch (_: Exception) {
                    // Callback exceptions shouldn't break parsing
                    // Could add error handling/logging here
                }
            }
        }
    }
}

/**
 * Extension function for parsing arguments with error handling.
 * @param args The command-line arguments to parse
 * @param onSuccess Callback executed on successful parsing
 * @param onError Callback executed on error
 * @param exitCode The exit code to use when parsing fails (default: the application will continue running and
 * return null. Provide a non-null value to exit the application with that code on error)
 */
fun <T : Arguments> T.parse(
    args: Array<String>,
    onSuccess: (T) -> Unit = {},
    onError: (ParseError, T) -> Unit = { _, t -> Help.printError(t) },
    exitCode: Int? = null
): T? {
    return try {
        parseWithException(args)
        onSuccess(this)
        this
    } catch (e: ParseError) {
        onError(e, this)
        if (exitCode != null) OsBound.exit(exitCode)
        else null
    }
}

/**
 * Parses command-line arguments and exits the application on error.
 *
 * This function is similar to [parse] but automatically exits the application
 * with the specified exit code when parsing fails, rather than returning null.
 *
 * @param args The command-line arguments to parse
 * @param exitCode The exit code to use when parsing fails (default: 1)
 * @param onSuccess Callback executed on successful parsing
 * @param onError Callback executed before exiting on error (default: prints error)
 * @return This Arguments instance on successful parsing (never returns on error)
 */
@Suppress("unused")
fun <T : Arguments> T.parseOrExit(
    args: Array<String>,
    exitCode: Int = 1,
    onSuccess: (T) -> Unit = {},
    onError: (ParseError, T) -> Unit = { _, t -> Help.printError(t) }
): T {
    return try {
        parseWithException(args)
        onSuccess(this)
        this
    } catch (e: ParseError) {
        onError(e, this)
        OsBound.exit(exitCode)
    }
}
