/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

// Help.kt
package onl.ycode.argos

import onl.ycode.argos.Arguments.Snapshot
import onl.ycode.argos.Help.printUsage
import onl.ycode.argos.terminal.ContentStyle
import onl.ycode.argos.terminal.Terminal
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
        withTerminalEmission(args, term) { ss, resolvedTerm ->
            printErrorList(ss, resolvedTerm)
            resolvedTerm.emitNewLine()
            printUsageSection(ss, resolvedTerm)
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
        withTerminalEmission(args, term) { ss, resolvedTerm ->
            printHeader(ss, resolvedTerm)
            resolvedTerm.emitNewLine()
            printUsageSection(ss, resolvedTerm)
            resolvedTerm.emitNewLine()
            printOptions(ss, resolvedTerm)
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
        withTerminalEmission(args, term) { ss, resolvedTerm ->
            printHeader(ss, resolvedTerm)
            resolvedTerm.emitNewLine()
            printUsageSection(ss, resolvedTerm)
            resolvedTerm.emitNewLine()
            printOptions(ss, resolvedTerm)
            resolvedTerm.emitNewLine()
            printConstraints(ss, resolvedTerm)
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
        action: (Snapshot, Terminal) -> Unit
    ) {
        val ss = args.snapshot()
        val resolvedTerm = term ?: args.terminal()
        resolvedTerm.startEmit()
        action(ss, resolvedTerm)
        resolvedTerm.endEmit()
    }

    // ----------------- ACTUAL IMPLEMENTATIONS -----------------

    internal fun printHeader(ss: Snapshot, term: Terminal) {
        ss.settings.appDescription?.let { desc ->
            val w = max(40, term.width)
            val separator = messageText(translate(" — "))
            val startCol = ss.settings.appName.length + separator.length
            term.emitStrong(ss.settings.appName)
            term.emitPlain(separator)
            val description = translate(desc.trim())
            emitWrappedAfter(term, description, currentCol = startCol, wrapIndent = startCol, width = w)
            term.emitNewLine()
        }
    }

    internal fun printUsageSection(ss: Snapshot, term: Terminal) {
        val w = max(40, term.width)
        term.emitStrong(messageText(translate("Usage:")))
        term.emitNewLine()

        if (ss.domains.isEmpty()) {
            val usage = buildUsageLine(ss.settings.appName, null, ss)
            emitWrappedUniform(term, usage, indent = 2, width = w)
            term.emitNewLine()
        } else {
            val entries: List<Triple<String, String, String?>> =
                ss.domains.map { d -> Triple(d.label ?: d.id, d.id, d.description) }
            emitDomainUsageBlocks(term, ss.settings.appName, entries, ss, width = w)
        }
    }

    internal fun printOptions(ss: Snapshot, term: Terminal) {
        if (ss.options.none { !it.hidden } && ss.positionals.isEmpty()) return

        val w = max(40, term.width)
        val x = min(term.infoColumn, max(10, w / 2))

        // Detailed positionals (Arguments)
        val posDetailed = ss.positionals
            .sortedBy { it.seq }
            .map { p ->
                val typeName = posType(p.expectedDesc)
                val show = !p.help.isNullOrBlank() || typeName != "string"
                Triple(p, typeName, show)
            }
            .filter { it.third }

        if (posDetailed.isNotEmpty()) {
            term.emitStrong(messageText(translate("Arguments:")))
            term.emitNewLine()

            // --- NEW: compute tab column = min(infoColumn, maxLeftLen + 2)
            val maxLeftLen = posDetailed.maxOf { ("<${it.first.name}>").length }
            val tabColArgs = min(x, maxLeftLen + 2)

            for ((p, tname, _) in posDetailed) {
                val leftName = "<${p.name}>"
                term.emitParam(leftName)
                val leftLen = leftName.length

                val parts = mutableListOf<TextFragment>()
                p.help?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += TextFragment(ContentStyle.PLAIN, it) }
                if (tname != "string") {
                    val keyPattern = if (p.help.isNullOrBlank()) {
                        translate("Type {type}")
                    } else {
                        translate("type {type}")
                    }
                    parts += TextFragment(ContentStyle.PLAIN, messageText(keyPattern, "type" to tname))
                }
                val joiner = messageText(translate(" — "))
                val rightFragments = if (parts.isEmpty()) emptyList() else parts.joinWithSeparator(joiner)

                emitTwoColumnRight(term, rightFragments, leftLen, tabCol = tabColArgs, width = w)
            }
        }

        val publicOptions = ss.options.filter { !it.hidden }
        if (publicOptions.isNotEmpty()) {
            if (posDetailed.isNotEmpty())
                term.emitNewLine()
            term.emitStrong(messageText(translate("Options:")))
            term.emitNewLine()

            // compute tab column = min(infoColumn, maxLeftLen + 2)
            val maxLeftLen = publicOptions.maxOf { opt ->
                val leftSwitches = calculateSwitchesLength(ss, opt)
                val typeToken = if (!opt.booleanFlag && opt.requiresValue) strongTypeToken(opt) else null
                var len = leftSwitches
                if (typeToken != null) len += 1 + typeToken.length
                len
            }
            val tabCol = min(x, maxLeftLen + 2)

            for (opt in publicOptions) {
                // Left: aliases with custom styling: '|' and parentheses plain; switches & 'no-' styled
                emitLeftSwitches(term, ss, opt)

                var leftLen = calculateSwitchesLength(ss, opt)

                // Type once, after aliases, for valued (non-boolean) options
                val typeToken = if (!opt.booleanFlag && opt.requiresValue) strongTypeToken(opt) else null
                if (typeToken != null) {
                    term.emitPlain(" ")
                    term.emitStrong(typeToken)
                    leftLen += 1 + typeToken.length
                }

                // Right: description + meta + choices + domains
                val helpText = opt.help?.takeIf { it.isNotBlank() }?.let { translate(it.trim()) }
                    ?: translate(fallbackSwitchDescription(opt))
                val meta = mutableListOf<List<TextFragment>>()

                opt.defaultValue?.let {
                    meta += messageRuns(translate("default: {value}"), "value" to TextFragment(ContentStyle.STRONG, it))
                }
                opt.envVar?.let {
                    meta += messageRuns(translate("env: {value}"), "value" to TextFragment(ContentStyle.STRONG, it))
                }
                if (opt.minRequiredGlobal >= 1) meta += messageRuns(translate("required"))
                opt.declaredDomains?.takeIf { it.isNotEmpty() }?.let { ds ->
                    meta += messageRuns(translate("domains: {domains}"), "domains" to ds.joinToString(", "))
                }

                val choices = commaChoices(ss, opt.expectedDesc)
                if (!choices.isNullOrBlank()) {
                    buildChoiceFragments(choices, ss.settings.argumentSeparator)?.let { choiceFrags ->
                        meta += messageRuns(translate("choices: {choices}"), "choices" to choiceFrags)
                    }
                }

                val rightFragments = mutableListOf<TextFragment>()
                if (helpText.isNotBlank()) {
                    rightFragments += TextFragment(ContentStyle.PLAIN, helpText)
                }
                if (meta.isNotEmpty()) {
                    if (rightFragments.isNotEmpty()) {
                        rightFragments += TextFragment(ContentStyle.PLAIN, " (")
                    } else {
                        rightFragments += TextFragment(ContentStyle.PLAIN, "(")
                    }
                    meta.forEachIndexed { index, fragments ->
                        if (index > 0) rightFragments += TextFragment(ContentStyle.PLAIN, messageText(translate(", ")))
                        rightFragments += fragments
                    }
                    rightFragments += TextFragment(ContentStyle.PLAIN, ")")
                }

                emitTwoColumnRight(term, rightFragments, leftLen, tabCol = tabCol, width = w)
            }
        }
    }

    internal fun printConstraints(ss: Snapshot, term: Terminal) {
        if (ss.groups.isEmpty() && ss.conflicts.isEmpty() && ss.conditionals.isEmpty() &&
            ss.options.filter { !it.hidden }
                .none { it.minRequiredGlobal > 1 } && ss.positionals.none { it.minRequiredGlobal > 1 }
        ) return

        for (g in ss.groups) when (g.kind) {
            "exactly-one" -> {
                emitMessage(term, translate("Exactly one of {options}"), "options" to ownerFragments(ss, g.owners))
                term.emitNewLine()
            }

            "at-most-one" -> {
                emitMessage(term, translate("At most one of {options}"), "options" to ownerFragments(ss, g.owners))
                term.emitNewLine()
            }

            "at-least-one" -> {
                emitMessage(term, translate("At least one of {options}"), "options" to ownerFragments(ss, g.owners))
                term.emitNewLine()
            }
        }

        for (c in ss.conflicts) {
            emitMessage(term, translate("Only one of {options}"), "options" to ownerFragments(ss, c))
            term.emitNewLine()
        }

        for (c in ss.conditionals) {
            when (c.kind) {
                "if-any-present" -> emitMessage(
                    term,
                    translate("Required {option} if exists any of: {references}"),
                    "option" to ownerFragments(ss, setOf(c.targetOwner)),
                    "references" to ownerFragments(ss, c.refs)
                )

                "if-all-present" -> emitMessage(
                    term,
                    translate("Required {option} if exists all of: {references}"),
                    "option" to ownerFragments(ss, setOf(c.targetOwner)),
                    "references" to ownerFragments(ss, c.refs)
                )

                "if-value" -> {
                    val referenceFragments = c.predicateRef?.let { ownerFragments(ss, setOf(it)) }
                        ?: listOf(TextFragment(ContentStyle.PARAM, messageText(translate("specific option"))))
                    emitMessage(
                        term,
                        translate("Required {option} if {reference} has specific value"),
                        "option" to ownerFragments(ss, setOf(c.targetOwner)),
                        "reference" to referenceFragments
                    )
                }
            }
            term.emitNewLine()
        }

        val switchOwners = ss.options.filter { !it.hidden && it.minRequiredGlobal > 1 }
            .map { longestSwitch(it) to it.minRequiredGlobal }
        val positionalOwners = ss.positionals.filter { it.minRequiredGlobal > 1 }
            .map { "<${it.name}>" to it.minRequiredGlobal }

        listOf(positionalOwners, switchOwners).forEach { group ->
            group.forEach { (label, count) ->
                emitMessage(
                    term,
                    translate("Required {argument} at least {count} occurrences"),
                    "argument" to listOf(TextFragment(ContentStyle.PARAM, label)),
                    "count" to count
                )
                term.emitNewLine()
            }
        }
    }

    internal fun printErrorList(ss: Snapshot, term: Terminal) {
        val errs = ss.errors
        if (errs.isEmpty()) return
        for (line in errs) {
            term.emitError(messageText(translate("Error:")))
            term.emitPlain(" ")
            term.emitAll(line)
            term.emitNewLine()
        }
    }

    // ----------------- INTERNAL HELPERS -----------------

    /**
     * Emit domain blocks:
     *   Label: <desc...>    (wrap with 4-space indent)
     *   <2-space indent> <usage...> (wrap with 2-space indent)
     */
    private fun emitDomainUsageBlocks(
        term: Terminal,
        appName: String,
        entries: List<Triple<String, String, String?>>, // (label, id, description)
        ss: Snapshot,
        width: Int
    ) {
        for ((label, id, desc) in entries) {
            // Label + description
            term.emitStrong(label)
            term.emitStrong(":")
            if (!desc.isNullOrBlank()) {
                term.emitPlain(" ")
                val currentCol = label.length + 2 // label + ":" + " "
                emitWrappedAfter(term, desc.trim(), currentCol = currentCol, wrapIndent = 4, width = width)
            }
            term.emitNewLine()

            // Usage (2-space indent)
            val usage = buildUsageLine(appName, id, ss)

            // break it into: "appName domain rest..."
            val rest = usage.removePrefix("$appName $id").trimStart()

            term.emitPlain("  ")
            term.emitPlain(appName)
            term.emitPlain(" ")
            term.emitParam(id) // bold domain
            if (rest.isNotEmpty()) {
                term.emitPlain(" ")
                emitWrappedAfter(term, rest, 2 + appName.length + 1 + id.length + 1, 2, width)
            }
            term.emitNewLine()
        }
    }

    /**
     * Usage text builder:
     *  - app (+ domain)
     *  - required options (raw alias group)
     *  - optional options ([raw alias group])
     *  - positionals by name, optional ones wrapped in []
     */
    private fun buildUsageLine(appName: String, domainId: String?, ss: Snapshot): String {
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
    private fun emitWrappedWords(
        term: Terminal,
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
                term.emitNewLine()
                term.emitPlain(" ".repeat(wrapIndent))
                term.emitPlain(w)
                col = wrapIndent + w.length
                atLineStart = false
            } else {
                if (!atLineStart) {
                    term.emitPlain(" "); col++
                }
                term.emitPlain(w)
                col += w.length
                atLineStart = false
            }
        }
    }

    /** Uniform wrapper: every line starts at the same indent (prints leading spaces for the first line). */
    private fun emitWrappedUniform(
        term: Terminal,
        text: String,
        @Suppress("SameParameterValue") indent: Int,
        width: Int
    ) {
        val words = StringUtils.splitWhitespace(text)
        if (words.isEmpty()) {
            term.emitPlain(" ".repeat(indent))
            return
        }

        // Emit initial indent
        term.emitPlain(" ".repeat(indent))

        // Use shared word-wrapping logic
        emitWrappedWords(term, words, indent, indent, width)
    }

    /**
     * Wrapper that assumes we are already at `currentCol` on the first line (prints no initial spaces),
     * but on wrap moves to a new line and indents to `wrapIndent`.
     */
    private fun emitWrappedAfter(term: Terminal, text: String, currentCol: Int, wrapIndent: Int, width: Int) {
        val words = StringUtils.splitWhitespace(text)

        // Use shared word-wrapping logic
        emitWrappedWords(term, words, currentCol, wrapIndent, width)
    }

    /**
     * Two-column writer for Options/Arguments, with dynamic tab column:
     *  tabCol = min(infoColumn, maxLeftLen + 2) (precomputed by caller),
     *  - If the left fits before tabCol: pad to tabCol and start right there.
     *  - Else: one space after left, start right, wrap to tabCol.
     */
    private fun emitTwoColumnRight(term: Terminal, rightFragments: List<TextFragment>, leftLen: Int, tabCol: Int, width: Int) {
        val words = fragmentsToWords(rightFragments)
        if (words.isEmpty()) {
            term.emitNewLine(); return
        }

        var col: Int
        if (leftLen + 1 <= tabCol) {
            term.emitPlain(" ".repeat(tabCol - leftLen))
            col = tabCol
        } else {
            term.emitPlain(" ")
            col = leftLen + 1
        }

        var atLineStart = (col == tabCol)

        for (word in words) {
            val prefixSpace = if (atLineStart) 0 else if (word.leadingSpace) 1 else 0
            val need = prefixSpace + word.text.length
            if (col + need > width) {
                term.emitNewLine()
                term.emitPlain(" ".repeat(tabCol))
                term.emit(TextFragment(word.style, word.text))
                col = tabCol + word.text.length
                atLineStart = false
            } else {
                if (!atLineStart && word.leadingSpace) {
                    term.emitPlain(" ")
                    col++
                }
                term.emit(TextFragment(word.style, word.text))
                col += word.text.length
                atLineStart = false
            }
        }
        term.emitNewLine()
    }

    private data class StyledWord(val text: String, val style: ContentStyle, val leadingSpace: Boolean)

    private fun fragmentsToWords(fragments: List<TextFragment>): List<StyledWord> {
        if (fragments.isEmpty()) return emptyList()
        val words = mutableListOf<StyledWord>()
        var pendingSpace = false
        for (fragment in fragments) {
            val text = fragment.text
            var index = 0
            while (index < text.length) {
                val ch = text[index]
                if (ch.isWhitespace()) {
                    pendingSpace = pendingSpace || words.isNotEmpty()
                    while (index < text.length && text[index].isWhitespace()) index++
                } else {
                    val start = index
                    while (index < text.length && !text[index].isWhitespace()) index++
                    val wordText = text.substring(start, index)
                    words += StyledWord(wordText, fragment.style, pendingSpace && words.isNotEmpty())
                    pendingSpace = false
                }
            }
        }
        return words
    }

    private fun List<TextFragment>.joinWithSeparator(separator: String): List<TextFragment> {
        if (isEmpty()) return emptyList()
        val result = ArrayList<TextFragment>(size * 2)
        forEachIndexed { index, fragment ->
            if (index > 0) result += TextFragment(ContentStyle.PLAIN, separator)
            result += fragment
        }
        return result
    }

    private fun buildChoiceFragments(raw: String, separator: String): List<TextFragment>? {
        if (raw.isBlank()) return null
        val body = raw.removePrefix("[").removeSuffix("]")
        if (body.isBlank()) return null
        val items = body.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) return null
        val fragments = ArrayList<TextFragment>(items.size * 2)
        items.forEachIndexed { index, item ->
            if (index > 0) fragments += TextFragment(ContentStyle.PLAIN, ", ")
            fragments += TextFragment(ContentStyle.STRONG, item)
        }
        return fragments
    }

    // ---------- Switch rendering helpers ----------

    /** Text form used for width calc & Usage (no styling). Includes "--(no-)param" for negatable booleans. */
    private fun rawOptionToken(ss: Snapshot, opt: Arguments.SpecInfo): String {
        val pref = ss.settings.defaultLongPrefix
        val forms = opt.switches.ifEmpty { listOf(pref + opt.ownerName) }

        // Use only the longest option name instead of all aliases
        val longestForm = forms.maxByOrNull { it.length } ?: forms.first()

        return if (opt.booleanFlag && opt.booleanNegatable &&
            longestForm.startsWith(pref) && opt.negationPrefix != null
        ) {
            val base = longestForm.removePrefix(pref)
            val negPrefix = opt.negationPrefix
            if (!base.startsWith(negPrefix)) {
                "${pref}($negPrefix)$base"
            } else longestForm
        } else longestForm
    }

    /** Calculate the actual rendered length of all switches (including separators) for alignment calculation. */
    private fun calculateSwitchesLength(ss: Snapshot, opt: Arguments.SpecInfo): Int {
        val pref = ss.settings.defaultLongPrefix
        val forms = opt.switches.ifEmpty { listOf(pref + opt.ownerName) }
        val separatorLen = ss.settings.argumentSeparator.length

        var totalLen = 0
        forms.forEachIndexed { idx, sw ->
            if (opt.booleanFlag && opt.booleanNegatable &&
                sw.startsWith(pref) && opt.negationPrefix != null
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
    private fun emitLeftSwitches(term: Terminal, ss: Snapshot, opt: Arguments.SpecInfo) {
        val pref = ss.settings.defaultLongPrefix
        val forms = opt.switches.ifEmpty { listOf(pref + opt.ownerName) }

        forms.forEachIndexed { idx, sw ->
            if (opt.booleanFlag && opt.booleanNegatable &&
                sw.startsWith(pref) && opt.negationPrefix != null
            ) {
                val base = sw.removePrefix(pref)
                val negPrefix = opt.negationPrefix
                if (!base.startsWith(negPrefix)) {
                    term.emitParam(pref)     // e.g. "--"
                    term.emitPlain("(")      // "(" plain
                    term.emitParam(negPrefix) // style custom prefix as PARAM
                    term.emitPlain(")")      // ")" plain
                    term.emitParam(base)     // "param"
                } else {
                    term.emitParam(sw)
                }
            } else {
                term.emitParam(sw)
            }
            if (idx < forms.lastIndex) term.emitPlain(ss.settings.argumentSeparator) // separator plain
        }
    }

    /** Strong type token for options (rendered once after aliases). */
    private fun strongTypeToken(opt: Arguments.SpecInfo): String =
        "<" + when (val e = opt.expectedDesc?.trim()?.lowercase()) {
            null -> "string"
            else -> when {
                e.startsWith("one of:") -> "string"
                "integer" in e -> "int"
                "long" in e -> "long"
                "decimal" in e -> "decimal"
                else -> "string"
            }
        } + ">"

    private fun posType(expected: String?): String {
        val e = expected?.trim()?.lowercase()
        return when {
            e == null -> "string"
            e.startsWith("one of:") -> "string"
            "integer" in e -> "int"
            "long" in e -> "long"
            "decimal" in e -> "decimal"
            else -> "string"
        }
    }

/** For options: render "[A|B|C]" text for one-of/enum expected (NOT for positionals). */
private fun commaChoices(ss: Snapshot, expected: String?): String? {
        if (expected.isNullOrBlank()) return null
        val s = expected.trim()
        if (!s.startsWith("one of:", ignoreCase = true)) return null
        val items = s.removePrefix("one of:")
            .trim()
            .split(',', ' ', '\t', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (items.isEmpty()) null else "[${items.joinToString(ss.settings.argumentSeparator)}]"
    }

    /** Fallback description for switches: Capitalized best switch name (only for options). */
    private fun fallbackSwitchDescription(opt: Arguments.SpecInfo): String {
        val best = opt.switches.maxByOrNull { if (it.startsWith("--")) Int.MAX_VALUE else it.length }.orEmpty()
        val name = best.dropWhile { !it.isLetterOrDigit() && it != '_' }
        val words = StringUtils.splitWhitespace(name.replace('_', ' ').replace('-', ' ').trim())
        if (words.isEmpty()) return name.replaceFirstChar { it.uppercase() }
        return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

/** Longest switch helper for constraints. */
private fun longestSwitch(opt: Arguments.SpecInfo): String {
        val forms = opt.switches
        return forms.maxByOrNull { it.length } ?: forms.first()
    }

/** Show owners using the *longest* switch of each option; fallback to "<name>" for positionals. */
private fun ownerFragments(s: Snapshot, owners: Collection<String>): List<TextFragment> {
    val fragments = mutableListOf<TextFragment>()
    owners.forEachIndexed { index, owner ->
        val opt = s.options.find { it.ownerName == owner }
        val token = if (opt != null && opt.switches.isNotEmpty()) longestSwitch(opt) else "<$owner>"
        fragments += TextFragment(ContentStyle.PARAM, token)
        if (index < owners.size - 1) fragments += TextFragment(ContentStyle.PLAIN, ", ")
    }
    return fragments
}

private fun emitMessage(
    term: Terminal,
    pattern: String,
    vararg params: Pair<String, Any?>
) {
    term.emitAll(messageRuns(pattern, *params))
}
}
