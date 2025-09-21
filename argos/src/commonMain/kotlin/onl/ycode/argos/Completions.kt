/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

// Completions.kt
package onl.ycode.argos

/**
 * Utilities for generating shell completion scripts from Arguments definitions.
 *
 * This object provides functions to generate completion scripts for various shells,
 * enabling tab completion of command-line options and arguments in interactive shells.
 */
object Completions {

    /**
     * Generates a Fish shell completion script for the given Arguments instance.
     *
     * Creates a complete Fish shell completion script that supports:
     * - Option completion with descriptions
     * - Domain/subcommand completion
     * - Value completion for enum options
     * - Short and long option forms
     *
     * @param args The Arguments instance to generate completions for
     * @return A Fish shell completion script as a string
     */
    @Suppress("unused")
    fun toFishCompletion(args: Arguments): String {
        val s = args.snapshot()
        val b = StringBuilder()
        val cmd = s.settings.appName

        fun esc(text: String) = text.replace("'", "\\'")

        if (s.domains.isNotEmpty()) {
            val domWords = buildList {
                s.domains.forEach { d -> add(d.id); addAll(d.aliases) }
            }.distinct().joinToString(" ")
            b.appendLine("complete -c $cmd -n '__fish_use_subcommand' -a '$domWords' -d 'domain'")
        }

        for (opt in s.options) {
            val desc = buildDesc(opt)
            val needsVal = if (opt.requiresValue) " -r" else ""
            val (shorts, longs) = splitSwitches(opt.switches)

            for (lo in longs) {
                b.append("complete -c $cmd -l ${esc(lo)}")
                if (desc.isNotEmpty()) b.append(" -d '${esc(desc)}'")
                b.append(needsVal)
                val choices = choicesFromExpected(opt.expectedDesc)
                if (opt.requiresValue && choices.isNotEmpty()) b.append(" -a '${choices.joinToString(" "){esc(it)}}'")
                b.appendLine()
            }
            for (sh in shorts) {
                b.append("complete -c $cmd -s ${esc(sh)}")
                if (desc.isNotEmpty()) b.append(" -d '${esc(desc)}'")
                b.append(needsVal)
                val choices = choicesFromExpected(opt.expectedDesc)
                if (opt.requiresValue && choices.isNotEmpty()) b.append(" -a '${choices.joinToString(" "){esc(it)}}'")
                b.appendLine()
            }

            if (opt.booleanFlag && opt.booleanNegatable) {
                for (lo in longs) {
                    val base = lo.removePrefix("no-")
                    if (base == lo) {
                        b.append("complete -c $cmd -l no-$lo")
                        if (desc.isNotEmpty()) b.append(" -d '${esc("$desc (negate)")}'")
                        b.appendLine()
                    }
                }
            }
        }
        return b.toString()
    }

    /**
     * Generates a Zsh shell completion script for the given Arguments instance.
     *
     * Creates a complete Zsh completion script that supports:
     * - Option completion with descriptions
     * - Domain/subcommand completion
     * - Value completion for enum options
     * - Both short and long option forms
     * - Negatable boolean flags (--no-flag)
     *
     * @param args The Arguments instance to generate completions for
     * @return A Zsh completion script as a string
     */
    @Suppress("unused")
    fun toZshCompletion(args: Arguments): String {
        val s = args.snapshot()
        val cmd = s.settings.appName
        val b = StringBuilder()
        b.appendLine("#compdef $cmd")
        b.appendLine("_${cmd}() {")
        b.appendLine("  _arguments -s \\")
        val lines = mutableListOf<String>()

        for (opt in s.options) {
            val desc = buildDesc(opt)
            val (shorts, longs) = splitSwitches(opt.switches)

            // FIXED: build forms correctly
            val forms = buildList {
                shorts.forEach { add("-$it") }
                longs.forEach  { add("--$it") }
            }

            val choiceList = if (opt.requiresValue) choicesFromExpected(opt.expectedDesc) else emptyList()
            val valueSpec = when {
                opt.requiresValue && choiceList.isNotEmpty() -> ":(value):(${choiceList.joinToString(" ")})"
                opt.requiresValue -> ":value:"
                else -> ""
            }
            val spec = if (forms.size == 1)
                "'${forms.first()}[${escapeZ(desc)}]$valueSpec'"
            else
                "'{${forms.joinToString(",")}}[${escapeZ(desc)}]$valueSpec'"
            lines += "    $spec \\"

            if (opt.booleanFlag && opt.booleanNegatable) {
                longs.forEach { lo ->
                    val base = lo.removePrefix("no-")
                    if (base == lo) lines += "    '--no-$lo[${escapeZ("$desc (negate)")} ]' \\"
                }
            }
        }

        if (s.domains.isNotEmpty()) {
            val doms = buildList { s.domains.forEach { d -> add(d.id); addAll(d.aliases) } }.distinct().joinToString(" ")
            lines += "    ':domain:(${doms})' \\"
        }
        val singles = s.positionals.filter { it.kind == PositionalKind.SINGLE }
        for (p in singles) lines += "    ':${p.name}:' \\"
        s.positionals.lastOrNull { it.kind != PositionalKind.SINGLE }?.let { tail ->
            lines += "    '*:${tail.name}:' \\"
        }

        val last = lines.removeLastOrNull()
        lines.forEach { b.appendLine(it) }
        if (last != null) b.appendLine(last.removeSuffix(" \\"))

        b.appendLine("}")
        b.appendLine("compdef _${cmd} $cmd")
        return b.toString()
    }

    /**
     * Generates a Bash shell completion script for the given Arguments instance.
     *
     * Creates a comprehensive Bash completion script that supports:
     * - Option completion with short and long forms
     * - Domain/subcommand completion (shows domains before any non-option arguments)
     * - Value completion for options with predefined choices (enum-like options)
     * - Negatable boolean flags (--no-flag)
     * - --option=value format completion
     * - Context-aware completion that respects argument position
     *
     * The generated script uses bash-completion conventions and requires the
     * bash-completion package to be installed for optimal functionality.
     *
     * @param args The Arguments instance to generate completions for
     * @return A Bash completion script as a string
     */
    @Suppress("unused")
    fun toBashCompletion(args: Arguments): String {
        val s = args.snapshot()
        val cmd = s.settings.appName
        val b = StringBuilder()

        val allOpts = s.options.flatMap { it.switches }.toMutableSet()
        s.options.filter { it.booleanFlag && it.booleanNegatable }.forEach { opt ->
            val longs = opt.switches.filter { it.startsWith("--") }.map { it.removePrefix("--") }
            longs.forEach { ln -> if (!ln.startsWith("no-")) allOpts += "--no-$ln" }
        }
        val allOptsStr = allOpts.joinToString(" ")

        fun esc(x: String) = x.replace("'", "\\'")

        b.appendLine("_${cmd}_complete() {")
        b.appendLine("  local cur prev words cword")
        b.appendLine("  _init_completion -n : || return")
        b.appendLine("  local opts=\"$allOptsStr\"")
        if (s.domains.isNotEmpty()) {
            val doms = buildList { s.domains.forEach { d -> add(d.id); addAll(d.aliases) } }.distinct().joinToString(" ")
            b.appendLine("  local domains=\"$doms\"")
        } else b.appendLine("  local domains=\"\"")

        b.appendLine($$"  case \"$prev\" in")
        for (opt in s.options.filter { it.requiresValue }) {
            for (sw in opt.switches) {
                val ch = choicesFromExpected(opt.expectedDesc)
                b.appendLine("    $sw)")
                if (ch.isNotEmpty()) b.appendLine($$"      COMPREPLY=( $(compgen -W '$${ch.joinToString(" ") { esc(it) }}' -- \"$cur\") ) ; return ;;")
                else b.appendLine("      return ;;")
            }
        }
        b.appendLine("  esac")

        b.appendLine($$"  if [[ \"$cur\" == --*=* ]]; then")
        b.appendLine($$"    local key=\"${cur%%=*}\"")
        b.appendLine($$"    case \"$key\" in")
        s.options.filter { it.requiresValue }.forEach { opt ->
            opt.switches.filter { it.startsWith("--") }.forEach { sw ->
                val ch = choicesFromExpected(opt.expectedDesc)
                b.appendLine("      $sw)")
                if (ch.isNotEmpty()) {
                    b.appendLine($$"        local prefix=\"${cur%%=*}=\"; local part=\"${cur#*=}\"")
                    b.appendLine($$"        COMPREPLY=( $(compgen -W '$${ch.joinToString(" ") { esc(it) }}' -- \"$part\") )")
                    b.appendLine($$"        if [[ \"${#COMPREPLY[@]}\" -gt 0 ]]; then")
                    b.appendLine($$"          local i; for i in \"${!COMPREPLY[@]}\"; do COMPREPLY[\"$i\"]=\"$prefix${COMPREPLY[$i]}\"; done")
                    b.appendLine("        fi")
                    b.appendLine("        return ;;")
                } else b.appendLine("        return ;;")
            }
        }
        b.appendLine("    esac")
        b.appendLine("  fi")

        b.appendLine("  local seen_nonopt=0")
        b.appendLine($$"  for w in \"${COMP_WORDS[@]:1}\"; do")
        b.appendLine($$"    [[ \"$w\" == -- ]] && break")
        b.appendLine($$"    if [[ \"$w\" != -* ]]; then seen_nonopt=1; break; fi")
        b.appendLine("  done")
        b.appendLine($$"  if [[ \"$seen_nonopt\" -eq 0 && -n \"$domains\" && \"$cur\" != -* ]]; then")
        b.appendLine($$"    COMPREPLY=( $(compgen -W \"$domains\" -- \"$cur\") ) ; return")
        b.appendLine("  fi")

        b.appendLine($$"  COMPREPLY=( $(compgen -W \"$opts\" -- \"$cur\") )")
        b.appendLine("}")
        b.appendLine("complete -F _${cmd}_complete $cmd")
        return b.toString()
    }

    // ---------- helpers ----------

    private fun splitSwitches(switches: List<String>): Pair<List<String>, List<String>> {
        val shorts = mutableListOf<String>()
        val longs = mutableListOf<String>()
        for (sw in switches) {
            val prefix = sw.takeWhile { !it.isLetterOrDigit() && it != '_' }
            val name = sw.drop(prefix.length)
            when (prefix) {
                "-" -> if (name.length == 1) shorts += name else longs += name
                "--" -> longs += name
                "/" -> if (name.length == 1) shorts += name else longs += name
                else -> longs += name
            }
        }
        return shorts to longs
    }

    private fun buildDesc(opt: Arguments.SpecInfo): String {
        val parts = mutableListOf<String>()
        opt.help?.takeIf { it.isNotBlank() }?.let { parts += it }
        when {
            opt.booleanFlag -> parts += "boolean"
            opt.expectedDesc?.startsWith("one of:", true) == true -> parts += "one of: " + opt.expectedDesc.removePrefix("one of:").trim()
            opt.requiresValue -> parts += "value"
        }
        return parts.joinToString(" — ")
    }

    private fun choicesFromExpected(expected: String?): List<String> {
        if (expected.isNullOrBlank()) return emptyList()
        val s = expected.trim()
        val body = when {
            s.startsWith("one of:", ignoreCase = true) -> s.removePrefix("one of:").trim()
            else -> return emptyList()
        }
        return body.split(',', ' ', '\t', '/').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun escapeZ(s: String): String = s.replace("'", "''")
}
