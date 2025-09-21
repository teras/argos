/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

// Help.kt
package onl.ycode.argos

import onl.ycode.argos.ArgosI18n.translate
import onl.ycode.argos.Help.printUsage
import onl.ycode.argos.snapshot.PositionalKind
import onl.ycode.argos.snapshot.Snapshot
import onl.ycode.argos.snapshot.SpecInfo
import onl.ycode.argos.terminal.ContentStyle
import onl.ycode.argos.terminal.Terminal
import onl.ycode.argos.terminal.TerminalMetrics
import kotlin.math.max
import kotlin.math.min

// ======== Print help ========

/**
 * Utilities for generating formatted help text and error messages.
 *
 * **Note**: Most users should use the extension functions [Arguments.printUsage] and
 * [Arguments.printUsageExtended] instead of calling methods on this object directly.
 * The extension functions provide a more convenient API and are the recommended entry points.
 *
 * This object contains the implementation details for help text generation,
 * including formatting, layout, and styling logic.
 */
object Help {


    // ----------------- PUBLIC API -----------------

    /**
     * Prints error messages followed by basic usage information.
     *
     * This method is typically called when argument parsing fails. It outputs
     * any accumulated error messages, followed by the basic usage line and
     * options summary to help users understand the correct syntax.
     *
     * @param args The Arguments instance containing error state and configuration
     * @param term The Terminal instance to use for output (optional)
     */
    fun printError(args: Arguments, term: Terminal? = null) {
        withTerminalEmission(args, term) { buffer, ss, metrics ->
            pushErrorList(buffer, ss)
            buffer.nl()
            pushUsageSection(buffer, ss, metrics)
        }
    }

    /**
     * Prints comprehensive usage information for the application.
     *
     * Outputs a complete help message including application description (if set),
     * usage syntax, available options, and positional arguments. This is the
     * standard help output shown when users request help with --help.
     *
     * @param args The Arguments instance to generate help for
     * @param term The Terminal instance to use for output (optional)
     */
    fun printUsage(args: Arguments, term: Terminal? = null) {
        withTerminalEmission(args, term) { buffer, ss, metrics ->
            pushHeader(buffer, ss, metrics)
            buffer.nl()
            pushUsageSection(buffer, ss, metrics)
            buffer.nl()
            pushOptions(buffer, ss, metrics)
        }
    }

    /**
     * Prints extended usage information including constraint details.
     *
     * Similar to [printUsage] but additionally includes detailed information about
     * option constraints, validation rules, group requirements, conflicts, and
     * conditional dependencies. This provides users with complete information
     * about argument validation rules.
     *
     * @param args The Arguments instance to generate extended help for
     * @param term The Terminal instance to use for output (optional)
     */
    fun printUsageWithConstraints(args: Arguments, term: Terminal? = null) {
        withTerminalEmission(args, term) { buffer, ss, metrics ->
            pushHeader(buffer, ss, metrics)
            buffer.nl()
            buffer.nl()
            pushUsageSection(buffer, ss, metrics)
            buffer.nl()
            pushOptions(buffer, ss, metrics)
            buffer.nl()
            pushConstraints(buffer, ss)
        }
    }

    // ---- Helper methods for reducing duplication ----

    /**
     * Executes the given printing action with standard boilerplate setup/teardown.
     * This reduces duplication across printError, printUsage, and printUsageWithConstraints.
     */
    private inline fun withTerminalEmission(
        args: Arguments,
        term: Terminal? = null,
        action: (StringBuilder, Snapshot, TerminalMetrics) -> Unit
    ) {
        val ss = args.snapshot()
        val resolvedTerm = term ?: args.terminal()
        val buffer = StringBuilder()
        resolvedTerm.startEmit()
        action(buffer, ss, resolvedTerm)
        resolvedTerm.emitTaggedContent(buffer.toString())
        resolvedTerm.endEmit()
    }

    // ----------------- ACTUAL IMPLEMENTATIONS -----------------

    internal fun pushHeader(buffer: StringBuilder, ss: Snapshot, metrics: TerminalMetrics) {
        val description = ss.settings.appDescription
        if (description != null) {
            val w = max(40, metrics.width)
            val separator = " — "
            val startCol = ss.settings.appName.length + separator.length
            buffer += ss.settings.appName.asAppName()
            buffer += separator
            val description = translate(description.trim())
            pushWrappedAfter(buffer, description, currentCol = startCol, wrapIndent = startCol, width = w)
            buffer.nl()
        }
    }

    internal fun pushUsageSection(buffer: StringBuilder, ss: Snapshot, metrics: TerminalMetrics) {
        val w = max(40, metrics.width)
        buffer += translate("Usage:").asSection()
        buffer.nl()
        if (ss.domains.isEmpty()) {
            val usage = constructUsageLine(ss.settings.appName, null, ss)
            pushWrappedUniform(buffer, usage, indent = 2, width = w)
            buffer.nl()
        } else {
            val entries: List<Triple<String, String, String?>> =
                ss.domains.map { d -> Triple(d.label ?: d.id, d.id, d.description) }
            pushDomainUsageBlocks(buffer, ss.settings.appName, entries, ss, width = w)
        }
    }

    internal fun pushOptions(buffer: StringBuilder, ss: Snapshot, metrics: TerminalMetrics) {
        if (ss.options.none { !it.hidden } && ss.positionals.isEmpty()) return

        val w = max(40, metrics.width)
        val x = min(metrics.infoColumn, max(10, w / 2))

        fun List<String>?.describeValueType(): String = if (this == null || this.size > 1) "string" else this[0]

        // Detailed positionals (Arguments)
        val posDetailed = ss.positionals
            .sortedBy { it.seq }
            .map { p ->
                val typeName = p.expectedDesc.describeValueType()
                val show = !p.help.isNullOrBlank() || typeName != "string"
                Triple(p, typeName, show)
            }
            .filter { it.third }

        if (posDetailed.isNotEmpty()) {
            buffer += translate("Arguments:").asSection()
            buffer.nl()

            val maxLeftLen = posDetailed.maxOf { ("<${it.first.name}>").length }
            val tabColArgs = min(x, maxLeftLen + 2)

            for ((p, tname, _) in posDetailed) {
                val leftName = "<${p.name}>"
                buffer += leftName.asValue()
                val leftLen = leftName.length

                val parts = StringBuilder()
                p.help?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
                if (tname != "string") {
                    if (parts.isNotEmpty()) parts += " - "
                    parts += (if (p.help.isNullOrBlank()) translate("Type {type}")
                    else translate("type {type}"))
                        .replace("{type}", tname)
                }
                pushTwoColumnRight(buffer, parts.toString(), leftLen, tabCol = tabColArgs, width = w)
            }
        }

        val publicOptions = ss.options.filter { !it.hidden }
        if (publicOptions.isNotEmpty()) {
            if (posDetailed.isNotEmpty())
                buffer.nl()
            buffer += translate("Options:").asSection()
            buffer.nl()

            fun SpecInfo.typeToken() =
                if (!booleanFlag && requiresValue) "<${expectedDesc.describeValueType()}>" else null

            // compute tab column = min(infoColumn, maxLeftLen + 2)
            val maxLeftLen = publicOptions.maxOf { opt ->
                val leftSwitches = calculateSwitchesLength(ss, opt)
                val typeToken = opt.typeToken()
                var len = leftSwitches
                if (typeToken != null) len += 1 + typeToken.length
                len
            }
            val tabCol = min(x, maxLeftLen + 2)

            for (opt in publicOptions) {
                // Left: aliases with custom styling: '|' and parentheses plain; switches & 'no-' styled
                emitLeftSwitches(buffer, ss, opt)

                var leftLen = calculateSwitchesLength(ss, opt)

                // Type once, after aliases, for valued (non-boolean) options
                val typeToken = opt.typeToken()
                if (typeToken != null) {
                    buffer += " "
                    buffer += typeToken.asValue()
                    leftLen += 1 + typeToken.length
                }

                // Right: description + meta + choices + domains
                val helpText = opt.help?.takeIf { it.isNotBlank() }?.let { translate(it.trim()) }
                    ?: translate(fallbackSwitchDescription(opt))
                val meta = mutableListOf<String>()

                if (opt.defaultValue != null)
                    meta += translate("default: {value}")
                        .replace("{value}", opt.defaultValue.asValue())
                if (opt.envVar != null)
                    meta += translate("env: {value}")
                        .replace("{value}", opt.envVar)
                if (opt.minRequiredGlobal >= 1)
                    meta += translate("required")
                if (opt.declaredDomains != null && opt.declaredDomains.isNotEmpty())
                    meta += translate("domains: {domains}")
                        .replace("{domains}", opt.declaredDomains.asDomainList())
                if (opt.expectedDesc != null && opt.expectedDesc.isNotEmpty())
                    meta += translate("choices: {choices}")
                        .replace("{choices}", opt.expectedDesc.asValueListExpected())


                val rightFragments = StringBuilder()
                if (helpText.isNotBlank()) {
                    rightFragments += helpText
                }
                if (meta.isNotEmpty()) {
                    if (rightFragments.isNotEmpty()) rightFragments += " "
                    rightFragments += "("
                    rightFragments += meta.joinToString(", ")
                    rightFragments += ")"
                }

                pushTwoColumnRight(buffer, rightFragments.toString(), leftLen, tabCol = tabCol, width = w)
            }
        }
    }

    internal fun pushConstraints(buffer: StringBuilder, ss: Snapshot) {
        if (ss.groups.isEmpty() && ss.conflicts.isEmpty() && ss.conditionals.isEmpty() &&
            ss.options.filter { !it.hidden }
                .none { it.minRequiredGlobal > 1 } && ss.positionals.none { it.minRequiredGlobal > 1 }
        ) return

        for (g in ss.groups) {
            buffer += when (g.kind) {
                "exactly-one" -> translate("Exactly one of {options}")
                "at-most-one" -> translate("At most one of {options}")
                "at-least-one" -> translate("At least one of {options}")
                else -> ""
            }.replace("{options}", g.owners.asOptionList())
            buffer.nl()
        }

        for (c in ss.conflicts) {
            buffer += translate("Only one of {options}").replace("{options}", c.asOptionList())
            buffer.nl()
        }

        for (c in ss.conditionals) {
            buffer += when (c.kind) {
                "if-any-present" -> translate("Required {option} if exists any of: {references}")
                "if-all-present" -> translate("Required {option} if exists all of: {references}")
                "if-value" -> translate("Required {option} if {reference} has specific value")
                else -> ""
            }
                .replace("{option}", c.targetOwner.asOption())
                .replace("{references}", c.refs.asOptionList())
                .replace("{reference}", c.predicateRef?.asOption() ?: "<?>")
            buffer.nl()
        }

        val switchOwners = ss.options.filter { !it.hidden && it.minRequiredGlobal > 1 }
            .map { longestSwitch(it) to it.minRequiredGlobal }
        val positionalOwners = ss.positionals.filter { it.minRequiredGlobal > 1 }
            .map { "<${it.name}>" to it.minRequiredGlobal }

        listOf(positionalOwners, switchOwners).forEach { group ->
            group.forEach { (label, count) ->
                buffer += translate("Required {argument} at least {count} occurrences")
                    .replace("{argument}", label.asOption())
                    .replace("{count}", count.toString())
                buffer.nl()
            }
        }
    }

    internal fun pushErrorList(buffer: StringBuilder, ss: Snapshot) {
        val errs = ss.errors
        if (errs.isEmpty()) return
        for (line in errs) {
            with(buffer) {
                append(translate("Error:").asError())
                append(" ")
                append(line)
                append('\n')
            }
        }
    }

    // ----------------- INTERNAL HELPERS -----------------

    /**
     * Emit domain blocks:
     *   Label: <desc...>    (wrap with 4-space indent)
     *   <2-space indent> <usage...> (wrap with 2-space indent)
     */
    private fun pushDomainUsageBlocks(
        buffer: StringBuilder,
        appName: String,
        entries: List<Triple<String, String, String?>>, // (label, id, description)
        ss: Snapshot,
        width: Int
    ) {
        for ((label, id, desc) in entries) {
            // Label + description
            buffer += label.asDomainInfo()
            buffer += ":"
            if (!desc.isNullOrBlank()) {
                buffer += " "
                val currentCol = label.length + 2 // label + ":" + " "
                pushWrappedAfter(buffer, desc.trim(), currentCol = currentCol, wrapIndent = 4, width = width)
            }
            buffer.nl()

            // Usage (2-space indent)
            val usage = constructUsageLine(appName, id, ss)

            // break it into: "appName domain rest..."
            val rest = usage.removePrefix("$appName $id").trimStart()

            buffer += "  "
            buffer += appName
            buffer += " "
            buffer += id.asDomain()
            if (rest.isNotEmpty()) {
                buffer += " "
                pushWrappedAfter(buffer, rest, 2 + appName.length + 1 + id.length + 1, 2, width)
            }
            buffer.nl()
        }
    }

    /**
     * Usage text builder:
     *  - app (+ domain)
     *  - required options (raw alias group)
     *  - optional options ([raw alias group])
     *  - positionals by name, optional ones wrapped in []
     */
    private fun constructUsageLine(appName: String, domainId: String?, ss: Snapshot): String {
        val sb = StringBuilder()
        sb.append(appName)
        if (domainId != null) sb.append(' ').append(domainId)

        val optsForDomain = ss.options.filter { opt ->
            !opt.hidden && (opt.declaredDomains == null || (domainId != null && domainId in opt.declaredDomains))
        }
        val reqOpts = optsForDomain.filter { it.minRequiredGlobal >= 1 }
        val optOpts = optsForDomain.filter { it.minRequiredGlobal == 0 }

        reqOpts.forEach { o -> sb.append(' ').append(rawOptionToken(ss, o)) }
        optOpts.forEach { o -> sb.append(' ').append('[').append(rawOptionToken(ss, o)).append(']') }

        val activePos = ss.positionals
            .filter { domainId == null || it.declaredDomains == null || domainId in it.declaredDomains }
            .sortedBy { it.seq }

        val singles = activePos.filter { it.kind == PositionalKind.SINGLE }
        val tail = activePos.lastOrNull { it.kind != PositionalKind.SINGLE }

        for (p in singles) {
            val token = "<${p.name}>"
            sb.append(' ')
            if (p.minRequiredGlobal >= 1) sb.append(token) else sb.append('[').append(token).append(']')
        }
        tail?.let {
            val token = "<${it.name}>..."
            sb.append(' ')
            if (it.minRequiredGlobal >= 1) sb.append(token) else sb.append('[').append(token).append(']')
        }

        return sb.toString()
    }

    /**
     * Common word-wrapping logic shared between different emission strategies.
     */
    private fun pushWrappedWords(
        buffer: StringBuilder,
        words: List<String>,
        startCol: Int,
        wrapIndent: Int,
        width: Int
    ) {
        if (words.isEmpty()) return

        var col = startCol
        var atLineStart = true

        for (w in words) {
            val need = if (atLineStart) w.length else 1 + w.length
            if (col + need > width) {
                buffer.nl()
                buffer += " ".repeat(wrapIndent)
                buffer += w
                col = wrapIndent + w.length
                atLineStart = false
            } else {
                if (!atLineStart) {
                    buffer += " "
                    col++
                }
                buffer += w
                col += w.length
                atLineStart = false
            }
        }
    }

    /** Uniform wrapper: every line starts at the same indent (prints leading spaces for the first line). */
    private fun pushWrappedUniform(
        buffer: StringBuilder,
        text: String,
        @Suppress("SameParameterValue") indent: Int,
        width: Int
    ) {
        buffer += " ".repeat(indent)
        pushWrappedAfter(buffer, text, indent, indent, width)
    }

    /**
     * Wrapper that assumes we are already at `currentCol` on the first line (prints no initial spaces),
     * but on wrap moves to a new line and indents to `wrapIndent`.
     */
    private fun pushWrappedAfter(buffer: StringBuilder, text: String, currentCol: Int, wrapIndent: Int, width: Int) {
        val words = text.splitWhitespace()
        pushWrappedWords(buffer, words, currentCol, wrapIndent, width)
    }

    /**
     * Two-column writer for Options/Arguments, with dynamic tab column:
     *  tabCol = min(infoColumn, maxLeftLen + 2) (precomputed by caller),
     *  - If the left fits before tabCol: pad to tabCol and start right there.
     *  - Else: one space after left, start right, wrap to tabCol.
     */
    private fun pushTwoColumnRight(
        buffer: StringBuilder,
        text: String,
        leftLen: Int,
        tabCol: Int,
        width: Int
    ) {
        val words = parseStyledWords(text)
        if (words.isEmpty()) {
            buffer.nl()
            return
        }

        var col: Int
        if (leftLen + 1 <= tabCol) {
            buffer += " ".repeat(tabCol - leftLen)
            col = tabCol
        } else {
            buffer += " "
            col = leftLen + 1
        }

        var atLineStart = (col == tabCol)

        for (word in words) {
            val prefixSpace = if (atLineStart) 0 else if (word.leadingSpace) 1 else 0
            val need = prefixSpace + word.text.length
            if (col + need > width) {
                buffer.nl()
                buffer += " ".repeat(tabCol)
                buffer += word.style.asTag(word.text)
                col = tabCol + word.text.length
                atLineStart = false
            } else {
                if (!atLineStart && word.leadingSpace) {
                    buffer += " "
                    col++
                }
                buffer += word.style.asTag(word.text)
                col += word.text.length
                atLineStart = false
            }
        }
        buffer.nl()
    }

    private data class StyledWord(val text: String, val style: ContentStyle, val leadingSpace: Boolean)

    private fun parseStyledWords(input: String): List<StyledWord> {
        if (input.isEmpty()) return emptyList()
        val words = mutableListOf<StyledWord>()
        var style = ContentStyle.PLAIN
        var i = 0
        var pendingSpace = false
        fun matchTag(pos: Int) = ContentStyle.tagToStyle.entries.firstOrNull { input.startsWith(it.key, pos) }?.toPair()
        while (i < input.length) {
            if (input[i].isWhitespace()) {
                pendingSpace = pendingSpace || words.isNotEmpty()
                while (i < input.length && input[i].isWhitespace()) i++
                continue
            }
            matchTag(i)?.let { (tag, s) ->
                style = s; i += tag.length; continue
            }
            val start = i
            while (i < input.length && !input[i].isWhitespace() && matchTag(i) == null) i++
            if (i > start) {
                val word = input.substring(start, i)
                words += StyledWord(word, style, pendingSpace && words.isNotEmpty())
                pendingSpace = false
            }
        }
        return words
    }


    // ---------- Switch rendering helpers ----------

    /** Text form used for width calc & Usage (no styling). Includes "--(no-)param" for negatable booleans. */
    private fun rawOptionToken(ss: Snapshot, opt: SpecInfo): String {
        val pref = ss.settings.defaultLongPrefix
        val forms = opt.switches.ifEmpty { listOf(pref + opt.ownerName) }

        // Use only the longest option name instead of all aliases
        val longestForm = forms.maxByOrNull { it.length } ?: forms.first()

        return if (opt.booleanFlag && opt.negationPrefix != null &&
            longestForm.startsWith(pref)
        ) {
            val base = longestForm.removePrefix(pref)
            val negPrefix = opt.negationPrefix
            if (!base.startsWith(negPrefix)) {
                "${pref}($negPrefix)$base"
            } else longestForm
        } else longestForm
    }

    /** Calculate the actual rendered length of all switches (including separators) for alignment calculation. */
    private fun calculateSwitchesLength(ss: Snapshot, opt: SpecInfo): Int {
        val pref = ss.settings.defaultLongPrefix
        val forms = opt.switches.ifEmpty { listOf(pref + opt.ownerName) }
        val separatorLen = ss.settings.argumentSeparator.length

        var totalLen = 0
        forms.forEachIndexed { idx, sw ->
            if (opt.booleanFlag && opt.negationPrefix != null &&
                sw.startsWith(pref)
            ) {
                val base = sw.removePrefix(pref)
                val negPrefix = opt.negationPrefix
                if (!base.startsWith(negPrefix)) {
                    totalLen += pref.length + 1 + negPrefix.length + 1 + base.length // "--" + "(" + "no" + ")" + "param"
                } else {
                    totalLen += sw.length
                }
            } else {
                totalLen += sw.length
            }
            if (idx < forms.lastIndex) totalLen += separatorLen
        }
        return totalLen
    }

    /**
     * Styled emission of left aliases: '|' and parentheses are plain;
     * switches and the "no-" part are styled as PARAM.
     */
    private fun emitLeftSwitches(buffer: StringBuilder, ss: Snapshot, opt: SpecInfo) {
        val pref = ss.settings.defaultLongPrefix
        val forms = opt.switches.ifEmpty { listOf(pref + opt.ownerName) }
        forms.forEachIndexed { idx, sw ->
            if (opt.booleanFlag && opt.negationPrefix != null &&
                sw.startsWith(pref)
            ) {
                val base = sw.removePrefix(pref)
                val negPrefix = opt.negationPrefix
                if (!base.startsWith(negPrefix)) {
                    buffer += pref.asOption() // e.g. "--"
                    buffer += "("             // "(" plain
                    buffer += negPrefix.asOption() // style custom prefix as PARAM
                    buffer += ")"             // ")" plain
                    buffer += base.asOption() // "param"
                } else {
                    buffer += sw.asOption()
                }
            } else {
                buffer += sw.asOption()
            }
            if (idx < forms.lastIndex)
                buffer += ss.settings.argumentSeparator // separator plain
        }
    }

    /** Fallback description for switches: Capitalized best switch name (only for options). */
    private fun fallbackSwitchDescription(opt: SpecInfo): String {
        val best = opt.switches.maxByOrNull { if (it.startsWith("--")) Int.MAX_VALUE else it.length }.orEmpty()
        val name = best.dropWhile { !it.isLetterOrDigit() && it != '_' }
        val words = name.replace('_', ' ').replace('-', ' ').trim().splitWhitespace()
        if (words.isEmpty()) return name.replaceFirstChar { it.uppercase() }
        return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    /** Longest switch helper for constraints. */
    private fun longestSwitch(opt: SpecInfo): String {
        val forms = opt.switches
        return forms.maxByOrNull { it.length } ?: forms.first()
    }

    private operator fun StringBuilder.plusAssign(s: String) {
        append(s)
    }

    private fun StringBuilder.nl() {
        append('\n')
    }
}
