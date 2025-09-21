/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

// Arguments.kt
package onl.ycode.argos

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

internal class ConfigError(msg: String) : IllegalStateException(msg)

// ============================ Core data & helpers ============================

internal data class Raw(val present: Boolean, val value: String?, val argvIndex: Int?)

internal enum class ValueKind { SINGLE, LIST, SET, COUNT }

internal enum class CondKind { ANY_PRESENT, ALL_PRESENT, ANY_ABSENT, ALL_ABSENT, VALUE_PREDICATE, ALLOW_ONLY_IF_VALUE_PREDICATE }

internal enum class PositionalKind { SINGLE, LIST, SET }

internal sealed class SingleResult<out T> {
    data object Missing : SingleResult<Nothing>()
    data class Value<T>(val value: T, val source: ValueSource = ValueSource.USER) : SingleResult<T>()
    data class Invalid(val original: String?) : SingleResult<Nothing>()
}

internal sealed class ParseOutcome<out T> {
    data class Success<T>(val value: T) : ParseOutcome<T>()
    data class Error(val frags: List<TextFragment>) : ParseOutcome<Nothing>()
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

private fun String.isNegativeNumberToken(): Boolean =
    length >= 2 && this[0] == '-' && this[1].isDigit()

internal fun String.toKebab(): String = buildString(length + 4) {
    var first = true
    for (c in this@toKebab) {
        if (c.isUpperCase()) {
            if (!first) append('-')
            append(c.lowercaseChar())
        } else append(c)
        first = false
    }
}.trimStart('-')

private fun String.substringBeforeAny(seps: Set<Char>): String {
    val idx = indexOfFirst { it in seps }
    return if (idx >= 0) substring(0, idx) else this
}

internal fun pos(idx: Int?): String = idx?.let { " at position #${it + 1}" } ?: " from env"

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
    if (n <= 0) throw ConfigError("atLeast requires n >= 1 (got $n)")
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
        if (owner in names) throw ConfigError("Self is not allowed in constraint refs for '$owner'")
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
        reg.addConditional(owner, CondKind.ALLOW_ONLY_IF_VALUE_PREDICATE, emptySet(), predicateRef, predicate, scope = null)
    }
}

internal fun addGroupConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    groupType: String,
    refs: Array<out KProperty<*>>
) {
    constraintHooks += { reg, owner ->
        val others = refs.map { it.name }.toSet()
        if (owner in others) throw ConfigError("Owner '$owner' must not be passed to $groupType(...)")
        val owners = buildSet { add(owner); addAll(others) }
        if (owners.size < 2) throw ConfigError("$groupType requires at least 1 other ref")
        reg.addGroup(groupType, owners, scope = null)
    }
}

internal fun addConflictsConstraint(
    constraintHooks: MutableList<(Arguments, String) -> Unit>,
    refs: Array<out KProperty<*>>
) {
    constraintHooks += { reg, owner ->
        val others = refs.map { it.name }.toSet()
        if (owner in others) throw ConfigError("Owner '$owner' must not be passed to conflictsWith(...)")
        val owners = buildSet { add(owner); addAll(others) }
        if (owners.size < 2) throw ConfigError("conflictsWith requires at least 1 other ref")
        reg.addConflicts(owners, scope = null)
    }
}


/**
 * Extension function for parsing arguments with error handling.
 */
fun <T : Arguments> T.parse(
    args: Array<String>,
    onSuccess: (T) -> Unit = {},
    onError: (ParseError, T) -> Unit = { _, t -> Help.printError(t) }
): T? {
    return try {
        parseWithException(args)
        onSuccess(this)
        this
    } catch (e: ParseError) {
        onError(e, this)
        null
    }
}

// ============================ Arguments ============================


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
        else throw ConfigError("Invalid defaultLongPrefix: $defaultLongPrefix")

    private val clusterChar = if (clusterChar == null || clusterChar.isSymbol()) clusterChar
    else throw ConfigError("Invalid clusterChar: $clusterChar")

    private val valueSeparators = if (valueSeparators.all { it.isSymbol() }) valueSeparators
    else throw ConfigError("Invalid valueSeparators: $valueSeparators")

    private val argumentSeparator =
        argumentSeparator.ifEmpty { throw ConfigError("Invalid argumentSeparator: cannot be empty") }

    private val didYouMeanMax: Int =
        didYouMeanMax.takeIf { it >= 0 } ?: throw ConfigError("Invalid didYouMeanMax: $didYouMeanMax")

    private val maxAggregatedErrors: Int =
        maxAggregatedErrors.takeIf { it >= 0 } ?: throw ConfigError("Invalid maxAggregatedErrors: $maxAggregatedErrors")

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
    internal val passwordConfigs = mutableMapOf<String, PasswordConfig>()

    private val domainDefs = mutableListOf<Domain>()
    private val domainIds = mutableSetOf<String>()
    private val aliasToId = mutableMapOf<String, String>()
    private val domainPropNameToId = mutableMapOf<String, String>()

    // Domain builder information for late resolution
    private val domainBuilders = mutableMapOf<String, DomainBuilderInfo>()
    private val fragmentToInheritors = mutableMapOf<String, MutableSet<String>>()
    private val expandedDomainProps = mutableMapOf<String, Set<String>>()

    private var positionalSeqCounter = 0
    private val errorLines = mutableListOf<List<TextFragment>>()

    private val terminal: Terminal = terminal ?: if (useANSITerminal) ANSITerminal() else PlainTerminal()
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
        if (id.isBlank()) throw ConfigError("Domain id defined in property $propName cannot be blank")
        if (!id[0].isLetterOrDigit()) throw ConfigError("Domain id '$id' defined in property $propName must begin with a letter or digit")
        if (domainIds.contains(id)) throw ConfigError("Duplicate domain id '$id' defined in property $propName")
        if (aliasToId.containsKey(id)) throw ConfigError("Domain id '$id' defined in property $propName conflicts with an already existing domain alias")
        domainDefs += Domain(id, label, description, aliases)
        domainIds += id
        aliases.forEach { al ->
            if (al.isBlank()) throw ConfigError("Alias defined in domain id '$id', property $propName, cannot be blank")
            if (!al[0].isLetterOrDigit()) throw ConfigError("Alias $al defined in domain id '$id', property $propName, must begin with a letter or digit")
            if (domainIds.contains(al)) throw ConfigError("Alias $al defined in domain id '$id', property $propName, conflicts with an already existing domain id")
            if (aliasToId.containsKey(al)) throw ConfigError("Alias $al defined in domain id '$id', property $propName, conflicts with an already existing domain alias")
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
        booleanNegatable: Boolean,
        negationPrefix: String?,
        positionalKind: PositionalKind?,
        positionalSeq: Int?,
        envVar: String?,
        expectedDesc: String?,
        eager: Boolean,
        hidden: Boolean = false,
        defaultValue: String? = null,
        parseMany: (List<Raw>, labelFrags: List<TextFragment>, expectedDesc: String?) -> ParseOutcome<Any?>,
        single: (Raw) -> SingleResult<Any?>,
        validators: List<Pair<String, (Any?) -> Boolean>> = emptyList(),
        collectionValidators: List<Pair<String, (Any?) -> Boolean>> = emptyList()
    ) {
        ensureConfigMutable()

        for (sw in switches) {
            if (sw == "--") throw ConfigError("Switch '$sw' is reserved.")
            val prefix = sw.takeWhile { !it.isLetterOrDigit() && it != '_' }
            if (prefix.isEmpty()) throw ConfigError("Switch '$sw' must begin with a non-alphanumeric prefix.")
            if (prefix.length !in 1..2) throw ConfigError("Unsupported switch prefix length for '$sw' (${prefix.length}).")
            knownPrefixes += prefix

            val prev = switchToOwner[sw]
            if (prev != null && prev != ownerName) throw ConfigError("Switch '$sw' already bound to '$prev'.")

            switchToOwner[sw] = ownerName

            if (booleanFlag && booleanNegatable) {
                if (prefix == this.defaultLongPrefix) {
                    val base = sw.drop(prefix.length)
                    val actualNegPrefix = negationPrefix ?: this.defaultNegationPrefix
                    if (!base.startsWith(actualNegPrefix)) {
                        val neg = prefix + actualNegPrefix + base
                        val prev2 = switchToOwner[neg]
                        if (prev2 != null && prev2 != ownerName) throw ConfigError("Auto-negation '$neg' conflicts with '$prev2'.")
                        switchToOwner[neg] = ownerName
                    }
                }
            }
        }

        val displayLabel: List<TextFragment> =
            if (switches.isNotEmpty()) {
                switches.flatMapIndexed { idx, sw ->
                    if (idx < switches.lastIndex) listOf(TextFragment(ContentStyle.PARAM, sw), TextFragment(ContentStyle.PLAIN, argumentSeparator))
                    else listOf(TextFragment(ContentStyle.PARAM, sw))
                }
            } else {
                listOf(TextFragment(ContentStyle.PLAIN, "positional "), TextFragment(ContentStyle.PARAM, "<${ownerName}>"))
            }

        specs[ownerName] = Spec(
            ownerName,
            switches,
            displayLabel,
            repeatable,
            help,
            domainProps,
            null,
            requiresValue,
            valueKind,
            arity,
            booleanFlag,
            booleanNegatable,
            negationPrefix,
            positionalKind,
            positionalSeq,
            envVar,
            expectedDesc,
            eager,
            hidden,
            defaultValue,
            parseMany = { raws ->
                parseMany(raws, displayLabel, expectedDesc)
            },
            single = single,
            validators = validators,
            collectionValidators = collectionValidators
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
     * Returns the value source for the specified property.
     *
     * Usage: `args.valueSourceOf(args::property)`
     *
     * @param property The property reference to get the value source for
     * @return The ValueSource indicating how the property's value was obtained
     * @throws IllegalArgumentException if the property is not managed by Argos
     */
    fun <T> valueSourceOf(property: KProperty0<T>): ValueSource {
        return valueSources[property.name]
            ?: throw IllegalArgumentException("Property '${property.name}' is not managed by Argos or may be a domain property (domains are not supported for valueSource tracking).")
    }


    // ======== Rule registration helpers (called from hooks) ========

    internal fun addRequiredMin(owner: String, min: Int, scope: Set<String>?) {
        ensureConfigMutable()
        val key = RuleKey.RequiredMinKey(owner, min, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigError("Duplicate required/atLeast rule for '$owner' in scope $scope")
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
            throw ConfigError("Conditional refs cannot be empty for '$owner'")
        val key = RuleKey.ConditionalKey(owner, kind, canonNames(refs), predicateRef, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigError("Duplicate conditional for '$owner' in scope $scope")
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
        if (canon.size < 2) throw ConfigError("$kind requires at least 2 distinct owners")
        val key = RuleKey.GroupKey(kind, canon, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigError("Duplicate group rule for $canon in scope $scope")
        groups += when (kind) {
            "exactly-one" -> GroupRule.ExactlyOne(canon, scope)
            "at-most-one" -> GroupRule.AtMostOne(canon, scope)
            "at-least-one" -> GroupRule.AtLeastOne(canon, scope)
            else -> throw ConfigError("Unknown group kind '$kind'")
        }
    }

    internal fun addConflicts(owners: Set<String>, scope: Set<String>?) {
        ensureConfigMutable()
        val canon = canonNames(owners)
        if (canon.size < 2) throw ConfigError("conflicts requires at least 2 distinct owners")
        val key = RuleKey.ConflictsKey(canon, canonScope(scope))
        if (!ruleKeys.add(key)) throw ConfigError("Duplicate conflicts rule for $canon in scope $scope")
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
                    throw ConfigError("Domain '$domainId' inherits from unknown domain property '$inheritedDomainPropName'")
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
        if (configValidated) throw ConfigError("Configuration is frozen; modify DSL before first parse.")
    }

    private fun validateConfig() {
        if (configValidated) return

        // Resolve domain inheritance and fragments
        resolveDomainInheritance()

        // Validate that all refs point to existing specs
        run {
            val owners = specs.keys
            requiredMins.forEach {
                if (it.owner !in owners) throw ConfigError("Unknown option '${it.owner}' referenced in require/atLeast")
            }
            requirements.forEach { r ->
                if (r.targetOwner !in owners)
                    throw ConfigError("Unknown option '${r.targetOwner}' referenced in conditional requirement")
                r.refs.forEach { ref ->
                    if (ref !in owners) throw ConfigError("Unknown option '$ref' referenced in conditional requirement for '${r.targetOwner}'")
                }
                r.predicateRef?.let {
                    if (it !in owners) throw ConfigError("Unknown option '$it' used as predicate in conditional requirement for '${r.targetOwner}'")
                }
            }
            conflicts.forEach { c ->
                c.owners.forEach { if (it !in owners) throw ConfigError("Unknown option '$it' referenced in conflicts()") }
            }
            groups.forEach { g ->
                g.owners.forEach { if (it !in owners) throw ConfigError("Unknown option '$it' referenced in group rule") }
            }
        }

        // onlyInDomains(...) checks - use expanded domain props if available
        specs.values.filter { it.declaredDomainProps != null }.forEach { spec ->
            val propsToProcess = expandedDomainProps[spec.ownerName] ?: spec.declaredDomainProps!!
            spec.declaredDomains = propsToProcess.map {
                val id = domainPropNameToId[it]
                if (id == null) {
                    val label = if (spec.switches.isNotEmpty()) spec.switches.joinToString(", ") else "<positional>"
                    throw ConfigError("Item $label requested property '$it' which is not a domain property.")
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
            if (repeatables.size > 1) throw ConfigError("Multiple repeatable positionals in $label schema are not allowed")
            if (repeatables.size == 1) {
                val tailSeq = repeatables.first().positionalSeq!!
                val lastSeq = pos.maxOf { it.positionalSeq!! }
                if (tailSeq != lastSeq) throw ConfigError("Repeatable positional must be the last positional in $label schema")
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

    // ======== Label helpers ========

    private fun t(s: String) = TextFragment(ContentStyle.PLAIN, s)

    private fun labelFragsFor(spec: Spec<*>): List<TextFragment> = spec.label

    private fun joinOwnersFrags(specsList: List<Spec<*>>): List<TextFragment> {
        val out = mutableListOf<TextFragment>()
        specsList.forEachIndexed { i, sp ->
            if (i > 0) out += t(", ")
            out += labelFragsFor(sp)
        }
        return out
    }

    // ======== Snapshot (unchanged structures) ========

    internal fun snapshot(): Snapshot {
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
                booleanNegatable = it.booleanNegatable,
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

        val errorsSnap = errorLines.map { it.toList() }
        return Snapshot(settings, domainsSnap, optionsSnap, posSnap, groupsSnap, conflictsSnap, condSnap, errorsSnap)
    }

    // ============================ Internal helpers ============================

    internal fun getDefaultNegationPrefix(): String = defaultNegationPrefix

    // ============================ Public factories ============================

    protected fun option(vararg switches: String): OptionBuilder<String?> =
        OptionBuilder(
            switches = switches.toList(),
            repeatable = false,
            helpText = null,
            domainProps = null,
            requiresValue = true,
            single = { raw -> if (!raw.present) SingleResult.Missing else SingleResult.Value(raw.value) },
            booleanFlag = false,
            booleanNegatable = false,
            negationPrefix = null,
            positionalKind = null,
            positionalSeq = null,
            envVar = null,
            expectedDesc = null,
            eager = false
        )

    protected fun positional(): OptionBuilder<String?> =
        OptionBuilder(
            switches = emptyList(),
            repeatable = false,
            helpText = null,
            domainProps = null,
            requiresValue = true,
            single = { raw -> if (!raw.present) SingleResult.Missing else SingleResult.Value(raw.value) },
            booleanFlag = false,
            booleanNegatable = false,
            negationPrefix = null,
            positionalKind = PositionalKind.SINGLE,
            positionalSeq = null,
            envVar = null,
            expectedDesc = null,
            eager = false
        )

    protected fun domain(id: String? = null, fragment: Boolean = false): DomainBuilder = DomainBuilder(id, fragment)

    protected fun help(vararg switches: String = arrayOf("-h", "${defaultLongPrefix()}help"), exitOnTrigger: Boolean = true): NonNullableOptionBuilder<Boolean> =
        option(*switches).bool().eager().default(false).help("Show this help message").onValue {
            printUsage()
            if (exitOnTrigger) OsBound.exit(0)
        }

    protected fun version(version: String, vararg switches: String = arrayOf("${defaultLongPrefix()}version"), exitOnTrigger: Boolean = true): NonNullableOptionBuilder<Boolean> =
        option(*switches).bool().eager().default(false).help("Show version information").onValue {
            println(version)
            if (exitOnTrigger) OsBound.exit(0)
        }

    // ======== Spec model ========

    internal data class Spec<T>(
        val ownerName: String,
        val switches: List<String>,
        val label: List<TextFragment>,
        val repeatable: Boolean,
        val help: String?,
        val declaredDomainProps: Set<String>?,
        var declaredDomains: Set<String>?,
        val requiresValue: Boolean,
        val valueKind: ValueKind,
        val arity: Int = 1,
        val booleanFlag: Boolean,
        val booleanNegatable: Boolean,
        val negationPrefix: String?,
        val positionalKind: PositionalKind?,
        val positionalSeq: Int?,
        val envVar: String?,
        val expectedDesc: String?,
        val eager: Boolean,
        val hidden: Boolean = false,
        val defaultValue: String? = null,
        val parseMany: (List<Raw>) -> ParseOutcome<T>,
        val single: (Raw) -> SingleResult<T?>,
        val validators: List<Pair<String, (Any?) -> Boolean>> = emptyList(),
        val collectionValidators: List<Pair<String, (Any?) -> Boolean>> = emptyList()
    )

    // ======== Snapshot types ========

    internal data class Settings(
        val appName: String,
        val appDescription: String?,
        val defaultLongPrefix: String,
        val clusterChar: Char?,
        val valueSeparators: Set<Char>,
        val unknownOptionsAsPositionals: Boolean,
        val argumentSeparator: String
    )

    internal data class DomainInfo(
        val id: String,
        val label: String?,
        val description: String?,
        val aliases: Set<String>
    )

    internal data class SpecInfo(
        val ownerName: String,
        val switches: List<String>,
        val booleanFlag: Boolean,
        val booleanNegatable: Boolean,
        val negationPrefix: String?,
        val requiresValue: Boolean,
        val repeatable: Boolean,
        val declaredDomains: Set<String>?,
        val help: String?,
        val expectedDesc: String?,
        val minRequiredGlobal: Int,
        val hidden: Boolean = false,
        val defaultValue: String? = null,
        val envVar: String? = null
    )

    internal data class PositionalInfo(
        val name: String,
        val kind: PositionalKind,
        val repeatable: Boolean,
        val declaredDomains: Set<String>?,
        val help: String?,
        val expectedDesc: String?,
        val seq: Int,
        val minRequiredGlobal: Int
    )

    internal data class GroupRuleInfo(val kind: String, val owners: Set<String>)

    internal data class ConditionalInfo(
        val targetOwner: String,
        val kind: String,
        val refs: Set<String>,
        val predicateRef: String?,
        val limitToDomains: Set<String>?
    )

    internal data class Snapshot(
        val settings: Settings,
        val domains: List<DomainInfo>,
        val options: List<SpecInfo>,
        val positionals: List<PositionalInfo>,
        val groups: List<GroupRuleInfo>,
        val conflicts: List<Set<String>>,
        val conditionals: List<ConditionalInfo>,
        val errors: List<List<TextFragment>>
    )

    // ---- Public info ----

    /**
     * Returns the terminal instance used for formatted output.
     * This is either the user-provided terminal or auto-detected based on ANSI support.
     * @return The terminal instance used for output formatting
     */
    fun terminal() = terminal

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

        fun fail(frags: List<TextFragment>) {
            if (aggregateErrors) errorLines += frags
            else {
                errorLines += frags
                val msg = frags.joinToString("") { it.text }
                throw ParseError(msg)
            }
        }

        // Track option occurrences for duplicate validation
        val optionOccurrences = mutableMapOf<String, Int>()
        // Track which options were actually provided by user (not just initialized)
        val userProvidedOptions = mutableSetOf<String>()

        // Helper function to convert and accumulate values immediately
        fun storeConvertedValue(owner: String, present: Boolean, rawValue: String?, argvIndex: Int?) {
            if (!present) return

            val spec = specs[owner]!!

            // Track occurrences for duplicate checking
            optionOccurrences[owner] = (optionOccurrences[owner] ?: 0) + 1
            // Mark as user-provided
            userProvidedOptions.add(owner)

            val raw = Raw(present, rawValue, argvIndex)

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

                        ValueKind.COUNT -> {
                            // For count options, accumulate the count
                            val currentCount = values[owner] as? Int ?: 0
                            values[owner] = currentCount + (singleResult.value as? Int ?: 1)
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

                is SingleResult.Invalid -> {
                    // Invalid conversion - use existing fail() function
                    val more = spec.expectedDesc?.let { " (expected $it)" } ?: ""
                    val labelFrags = labelFragsFor(spec)
                    fail(
                        listOf(
                            TextFragment(ContentStyle.PLAIN, "Invalid value '"),
                            TextFragment(ContentStyle.PLAIN, rawValue ?: "null"),
                            TextFragment(ContentStyle.PLAIN, "' for ")
                        ) + labelFrags + listOf(
                            TextFragment(ContentStyle.PLAIN, pos(argvIndex) + more)
                        )
                    )
                }
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
                    messageRuns(
                        translate("Unknown domain '{domain}'. Expected: {expected}{position}"),
                        "domain" to tok,
                        "expected" to domainIds.joinToString(", "),
                        "position" to pos(idx)
                    )
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
                                messageRuns(
                                    translate("Missing value {index} of {total} for {option_token}{position}"),
                                    "index" to (arityIndex + 1),
                                    "total" to requiredValues,
                                    "option_token" to tok,
                                    "position" to pos(idx)
                                )
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

                            if (outcome.value != null) {
                                callbackExecutions.add(owner to outcome.value)
                            }
                        }
                        is ParseOutcome.Error -> fail(outcome.frags)
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

                // Handle regular options (arity = 1) - supports --param=value, -pvalue, etc.
                else if (spec.requiresValue) {
                    val next = processedArgs.getOrNull(i + 1)
                    val nextIsOption = next?.let { isKnownOption(it) } == true
                    if (next != null && !nextIsOption) {
                        value = next; i++; consumedIndices.add(i)
                    } else fail(messageRuns(translate("Missing value for {option_token}{position}"), "option_token" to tok, "position" to pos(idx)))
                } else {
                    // Optional value: use type-aware lookahead to decide whether to consume next arg
                    val next = processedArgs.getOrNull(i + 1)

                    val shouldConsume = shouldConsumeAsOptionalValue(next, spec)
                    value = if (shouldConsume) {
                        i++; consumedIndices.add(i); next  // Consume as value
                    } else {
                        // Flag mode: determine value based on option type and negation
                        if (spec.booleanFlag && spec.booleanNegatable) {
                            // For negatable boolean options, check if this is a negated switch
                            val base = tok.drop(prefix.length)
                            val actualNegPrefix = spec.negationPrefix ?: this.defaultNegationPrefix
                            if (base.startsWith(actualNegPrefix)) "false" else null
                        } else {
                            // For other options: use empty string for string options, null for typed options
                            val testRaw = Raw(true, "", null)
                            val emptyResult = spec.single(testRaw)
                            if (emptyResult is SingleResult.Invalid) null else ""
                        }
                    }
                }
                storeConvertedValue(owner, true, value, if (value == null) idx else i)

                if (spec.eager && spec.booleanFlag) {
                    val base = tok.drop(prefix.length)
                    val actualNegPrefix = spec.negationPrefix ?: this.defaultNegationPrefix
                    val parsedTrue =
                        parseBooleanLiteral(value) ?: !(spec.booleanNegatable && base.startsWith(actualNegPrefix))
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
                    if (hint != null) {
                        fail(
                            messageRuns(
                                translate("Unknown option {option_token}{position}. Did you mean {hint}?"),
                                "option_token" to full,
                                "position" to pos(idx),
                                "hint" to hint
                            )
                        )
                    } else {
                        fail(
                            messageRuns(
                                translate("Unknown option {option_token}{position}"),
                                "option_token" to full,
                                "position" to pos(idx)
                            )
                        )
                    }
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
                                messageRuns(
                                    translate("Missing value for {option_token}{position}"),
                                    "option_token" to shortToken,
                                    "position" to pos(idx)
                                )
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
                                messageRuns(
                                    translate("Unknown option {option_token} in '{cluster}'{position}"),
                                    "option_token" to shortToken,
                                    "cluster" to tok,
                                    "position" to pos(idx)
                                )
                            )
                            i++; continue  // Skip the processing phase
                        }
                    }
                    if (needsValueNotLast) {
                        val ch = rest[posiScan]
                        val shortToken = "$prefix$ch"
                        fail(
                            messageRuns(
                                translate("Option {option_token} requires a value and must be last in '{cluster}'{position}"),
                                "option_token" to shortToken,
                                "cluster" to tok,
                                "position" to pos(idx)
                            )
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
                                    messageRuns(
                                        translate("Missing value for {option_token}{position}"),
                                        "option_token" to shortToken,
                                        "position" to pos(idx)
                                    )
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
                                messageRuns(
                                    translate("Unknown option {option_token}{position}"),
                                    "option_token" to shortToken,
                                    "position" to pos(idx)
                                )
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
                        messageRuns(
                            translate("Unknown option {option_token}{position}. Did you mean {hint}?"),
                            "option_token" to (prefix + rest),
                            "position" to pos(idx),
                            "hint" to hint
                        )
                    )
                } else {
                    fail(
                        messageRuns(
                            translate("Unknown option {option_token}{position}"),
                            "option_token" to (prefix + rest),
                            "position" to pos(idx)
                        )
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
                    messageRuns(
                        "Missing value for {option_token}{position}",
                        "option_token" to (prefix + rest),
                        "position" to pos(idx)
                    )
                )
            } else if (spec.booleanFlag) {
                val next = processedArgs.getOrNull(i + 1)
                val parsed = parseBooleanLiteral(next)
                value = if (parsed != null) {
                    i++; consumedIndices.add(i); next
                } else {
                    // Check if this is a negated switch to determine default flag value
                    if (spec.booleanNegatable) {
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
                val msg = errorLines.joinToString(OsBound.termNewLine) { line -> line.joinToString("") { it.text } }
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

                        ValueKind.COUNT -> {
                            values[owner] = 0
                            valueSources[owner] = ValueSource.DEFAULT
                        }
                    }
                }
            }
            // Execute callbacks after successful parsing
            executeCallbacks(callbackExecutions)
            return this
        }

        if (domainIds.isNotEmpty() && selectedDomain == null) {
            val domList = domainIds.joinToString(", ")
            fail(messageRuns(translate("A domain is required. Choose one of: {domains}"), "domains" to domList))
        }

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
                        messageRuns(
                            translate("Unexpected positional argument '{argument}'{position}"),
                            "argument" to tok,
                            "position" to pos(at)
                        )
                    )
                } else {
                    val tokens = unexpectedArgs.joinToString(", ") { "'${it.first}'" }
                    fail(messageRuns(translate("Unexpected positional arguments: {arguments}"), "arguments" to tokens))
                }
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
                    val selectedInfo = sd?.let { " (selected: $it)" } ?: ""
                    fail(
                        messageRuns(
                            translate("{option} allowed only in: {domains}{selected_domain}"),
                            "option" to spec,
                            "domains" to allowed.joinToString(", "),
                            "selected_domain" to selectedInfo
                        )
                    )
                }
            }
            // Check for non-repeatable option duplicates
            if (!spec.repeatable && (optionOccurrences[owner] ?: 0) > 1 && spec.positionalKind == null) {
                fail(messageRuns(translate("Option {option} provided multiple times"), "option" to spec))
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

                    ValueKind.COUNT -> {
                        // Count options default to 0
                        values[owner] = 0
                        valueSources[owner] = ValueSource.DEFAULT
                    }
                }
            }
        }


        // Run post-conversion validation on typed values
        for ((owner, spec) in specs) {
            val value = values[owner]
            if (value != null) {
                // Helper function to create error fragments
                fun createErrorFragments(
                    messageTemplate: String,
                    value: String? = null,
                    count: String? = null
                ): List<TextFragment> {
                    val switches = spec.switches.joinToString(argumentSeparator)
                    return messageRuns(
                        translate(messageTemplate),
                        "name" to owner,
                        "value" to value,
                        "switches" to switches,
                        "count" to count
                    )
                }

                when (spec.valueKind) {
                    ValueKind.SINGLE -> {
                        // Check if this is an arity option (value is a List)
                        if (value is List<*>) {
                            @Suppress("UNCHECKED_CAST")
                            val list = value as List<Any?>
                            // Run element validators on each list element (for arity options)
                            for ((messageTemplate, predicate) in spec.validators) {
                                for (element in list) {
                                    if (!predicate(element)) {
                                        fail(createErrorFragments(messageTemplate, value = formatValue(element)))
                                    }
                                }
                            }
                            // Run collection validators on the entire list (for arity options)
                            for ((messageTemplate, predicate) in spec.collectionValidators) {
                                if (!predicate(list)) {
                                    fail(createErrorFragments(messageTemplate, count = list.size.toString()))
                                }
                            }
                        } else {
                            // Run element validators for single values
                            for ((messageTemplate, predicate) in spec.validators) {
                                if (!predicate(value)) {
                                    fail(createErrorFragments(messageTemplate, value = formatValue(value)))
                                }
                            }
                        }
                    }

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
                                        fail(createErrorFragments(messageTemplate, value = formatValue(element)))
                                    }
                                }
                            }
                            // Run collection validators on the flattened list
                            for ((messageTemplate, predicate) in spec.collectionValidators) {
                                if (!predicate(flatList)) {
                                    fail(createErrorFragments(messageTemplate, count = flatList.size.toString()))
                                }
                            }
                        } else {
                            // Regular list options - validate normally
                            // Run element validators on each list element
                            for ((messageTemplate, predicate) in spec.validators) {
                                for (element in list) {
                                    if (!predicate(element)) {
                                        fail(createErrorFragments(messageTemplate, value = formatValue(element)))
                                    }
                                }
                            }
                            // Run collection validators on the entire list
                            for ((messageTemplate, predicate) in spec.collectionValidators) {
                                if (!predicate(list)) {
                                    fail(createErrorFragments(messageTemplate, count = list.size.toString()))
                                }
                            }
                        }
                    }

                    ValueKind.SET -> {
                        // Convert KeyValue lists to KeyValueSet if needed
                        val actualSet = if (value is MutableList<*> &&
                                          value.isNotEmpty() && value.first() is KeyValue) {
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
                        for ((messageTemplate, predicate) in spec.validators) {
                            for (element in actualSet) {
                                if (!predicate(element)) {
                                    fail(createErrorFragments(messageTemplate, value = formatValue(element)))
                                }
                            }
                        }
                        // Run collection validators on the entire set
                        for ((messageTemplate, predicate) in spec.collectionValidators) {
                            if (!predicate(actualSet)) {
                                fail(createErrorFragments(messageTemplate, count = actualSet.size.toString()))
                            }
                        }
                    }

                    ValueKind.COUNT -> {
                        // Run validators for count values
                        for ((messageTemplate, predicate) in spec.validators) {
                            if (!predicate(value)) {
                                fail(createErrorFragments(messageTemplate, value = value.toString()))
                            }
                        }
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
                    ValueKind.COUNT -> values[owner] as Int
                }
                if (count < effMin) {
                    // Special handling for password options - prompt if missing and required
                    if (spec.expectedDesc == "password" && effMin == 1 && count == 0) {
                        val passwordConfig = passwordConfigs[owner]
                        if (passwordConfig != null) {
                            val password = promptForPassword(
                                passwordConfig.prompt,
                                passwordConfig.confirmPrompt,
                                passwordConfig.requireConfirmation,
                                passwordConfig.mismatchPrompt,
                                passwordConfig.maxRetries
                            )
                            if (password != null) {
                                values[owner] = password
                                continue // Skip the error since we got the password
                            }
                        }
                    }

                    if (effMin == 1) fail(messageRuns(translate("Option {option} is required"), "option" to spec))
                    else fail(
                        messageRuns(
                            "Option {option} requires at least {min} occurrences (got {count})",
                            "option" to spec,
                            "min" to effMin,
                            "count" to count
                        )
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
                    val refSpec = specs[req.predicateRef!!]!!
                    fail(
                        messageRuns(
                            translate("Option {option} is not allowed because {reference} has that value"),
                            "option" to targetSpec,
                            "reference" to refSpec
                        )
                    )
                }
            } else if (needed && !present(req.targetOwner)) {
                val pattern = when (req.kind) {
                    CondKind.ANY_PRESENT -> translate("Option {option} is required because {references} are present")
                    CondKind.ALL_PRESENT -> translate("Option {option} is required because {references} are all present")
                    CondKind.ANY_ABSENT -> translate("Option {option} is required because {references} are absent")
                    CondKind.ALL_ABSENT -> translate("Option {option} is required because {references} are all absent")
                    CondKind.VALUE_PREDICATE -> translate("Option {option} is required because {reference} has that value")
                    else -> error("Unsupported conditional kind: ${req.kind}")
                }

                val args = mutableListOf(
                    "option" to targetSpec as Any?
                )
                when (req.kind) {
                    CondKind.ANY_PRESENT, CondKind.ALL_PRESENT, CondKind.ANY_ABSENT, CondKind.ALL_ABSENT -> {
                        val refs = req.refs.map { specs[it]!! }
                        args += "references" to refs
                    }

                    CondKind.VALUE_PREDICATE -> {
                        val refSpec = specs[req.predicateRef!!]!!
                        args += "reference" to refSpec
                    }

                    else -> {}
                }
                fail(messageRuns(pattern, *args.toTypedArray()))
            }
        }

        // Conflicts
        for (c in conflicts) {
            val inScope = c.limitToDomains?.let { selectedDomain in it } ?: true
            if (!inScope) continue
            val offenders = c.owners.mapNotNull { specs[it] }.filter { present(it.ownerName) }
            if (offenders.size > 1) {
                fail(messageRuns(translate("Conflicting options: {options}"), "options" to offenders))
            }
        }

        // Groups
        for (g in groups) {
            val inScope = g.limitToDomains?.let { selectedDomain in it } ?: true
            if (!inScope) continue

            val ownersSpecs = g.owners.mapNotNull { specs[it] }
            val count = ownersSpecs.count { present(it.ownerName) }

            when (g) {
                is GroupRule.ExactlyOne -> if (count != 1)
                    fail(
                        messageRuns(
                            "Exactly one of ({options}) must be provided (got {count})",
                            "options" to ownersSpecs,
                            "count" to count
                        )
                    )

                is GroupRule.AtMostOne -> if (count > 1)
                    fail(
                        messageRuns(
                            "At most one of ({options}) may be provided (got {count})",
                            "options" to ownersSpecs,
                            "count" to count
                        )
                    )

                is GroupRule.AtLeastOne -> if (count < 1)
                    fail(messageRuns(translate("At least one of ({options}) is required"), "options" to ownersSpecs))
            }
        }

        if (errorLines.isNotEmpty()) {
            val capped =
                if (errorLines.size > maxAggregatedErrors)
                    errorLines.take(maxAggregatedErrors) + listOf(
                        listOf(
                            TextFragment(
                                ContentStyle.PLAIN,
                                "... (+${errorLines.size - maxAggregatedErrors} more)"
                            )
                        )
                    )
                else errorLines
            val msg = capped.joinToString(OsBound.termNewLine) { line -> line.joinToString("") { it.text } }
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
