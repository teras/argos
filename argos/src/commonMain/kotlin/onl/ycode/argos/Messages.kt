/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import onl.ycode.argos.terminal.ContentStyle
import onl.ycode.argos.terminal.Terminal

internal data class MessageTemplate(val segments: List<Segment>) {
    sealed interface Segment {
        data class Literal(val text: String) : Segment
        data class Placeholder(val name: String) : Segment
    }
}

private object TemplateParser {
    fun parse(raw: String): MessageTemplate {
        val segments = ArrayList<MessageTemplate.Segment>()
        val literal = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val ch = raw[index]
            if (ch == '{') {
                if (literal.isNotEmpty()) {
                    segments += MessageTemplate.Segment.Literal(literal.toString())
                    literal.setLength(0)
                }
                val end = raw.indexOf('}', startIndex = index + 1)
                if (end == -1) throw IllegalArgumentException("Unmatched '{' in template '$raw'")
                val name = raw.substring(index + 1, end).trim()
                if (name.isEmpty()) throw IllegalArgumentException("Empty placeholder in template '$raw'")
                segments += MessageTemplate.Segment.Placeholder(name)
                index = end + 1
            } else {
                literal.append(ch)
                index++
            }
        }
        if (literal.isNotEmpty()) {
            segments += MessageTemplate.Segment.Literal(literal.toString())
        }
        return MessageTemplate(segments)
    }
}

private object MessageCatalog {
    private val translations = mutableMapOf<String, String>()
    private val cache = mutableMapOf<String, MessageTemplate>()

    fun register(translations: Map<String, String>) {
        if (translations.isEmpty()) return
        this.translations.putAll(translations)
        cache.clear()
    }

    fun runs(pattern: String, vararg args: Pair<String, Any?>): List<TextFragment> {
        val template = templateFor(pattern)
        if (template.segments.isEmpty()) return emptyList()
        val values = if (args.isEmpty()) emptyMap() else LinkedHashMap<String, Any?>(args.size).apply {
            args.forEach { (name, value) -> this[name] = value }
        }
        val fragments = ArrayList<TextFragment>(template.segments.size)
        for (segment in template.segments) {
            when (segment) {
                is MessageTemplate.Segment.Literal -> {
                    if (segment.text.isNotEmpty()) {
                        fragments += TextFragment(ContentStyle.PLAIN, segment.text)
                    }
                }
                is MessageTemplate.Segment.Placeholder -> {
                    fragments += renderPlaceholder(segment.name, values[segment.name])
                }
            }
        }
        return fragments
    }

    fun text(pattern: String, vararg args: Pair<String, Any?>): String =
        runs(pattern, *args).joinToString(separator = "") { it.text }

    fun translate(pattern: String): String = translations[pattern] ?: pattern

    private fun templateFor(pattern: String): MessageTemplate =
        cache.getOrPut(pattern) { TemplateParser.parse(pattern) }

    fun resetMemory() {
        translations.clear()
        cache.clear()
    }

    private fun renderPlaceholder(name: String, raw: Any?): List<TextFragment> = when (name) {
        "option", "reference" -> optionRuns(raw)
        "options", "references" -> optionListRuns(raw)
        "option_token", "hint", "argument" -> coerceRuns(raw, ContentStyle.PARAM)
        else -> coerceRuns(raw, ContentStyle.PLAIN)
    }

    private fun optionRuns(raw: Any?): List<TextFragment> = when (raw) {
        null -> emptyList()
        is TextFragment -> listOf(raw)
        is List<*> -> raw.asFragments(ContentStyle.PARAM) ?: optionListRuns(raw)
        is Arguments.Spec<*> -> raw.label
        is Collection<*> -> optionListRuns(raw)
        else -> coerceRuns(raw, ContentStyle.PARAM)
    }

    private fun optionListRuns(raw: Any?): List<TextFragment> = when (raw) {
        null -> emptyList()
        is TextFragment -> listOf(raw)
        is List<*> -> raw.asFragments(ContentStyle.PARAM) ?: mergeOptionCollection(raw)
        is Arguments.Spec<*> -> raw.label
        is Collection<*> -> mergeOptionCollection(raw)
        else -> coerceRuns(raw, ContentStyle.PARAM)
    }

    private fun mergeOptionCollection(items: Collection<*>): List<TextFragment> {
        if (items.isEmpty()) return emptyList()
        val result = ArrayList<TextFragment>()
        items.forEachIndexed { index, item ->
            if (index > 0) result += TextFragment(ContentStyle.PLAIN, ", ")
            result += optionRuns(item)
        }
        return result
    }

    private fun coerceRuns(raw: Any?, defaultStyle: ContentStyle): List<TextFragment> = when (raw) {
        null -> emptyList()
        is TextFragment -> listOf(raw)
        is List<*> -> raw.asFragments(defaultStyle) ?: raw.flatMap { coerceRuns(it, defaultStyle) }
        is Array<*> -> coerceRuns(raw.toList(), defaultStyle)
        is Arguments.Spec<*> -> raw.label
        is Collection<*> -> raw.flatMap { coerceRuns(it, defaultStyle) }
        is CharSequence -> {
            val text = raw.toString()
            if (text.isEmpty()) emptyList() else listOf(TextFragment(defaultStyle, text))
        }
        else -> {
            val text = raw.toString()
            if (text.isEmpty()) emptyList() else listOf(TextFragment(defaultStyle, text))
        }
    }

    private fun List<*>.asFragments(defaultStyle: ContentStyle): List<TextFragment>? {
        if (isEmpty()) return emptyList()
        val fragments = ArrayList<TextFragment>(size)
        for (item in this) {
            when (item) {
                is TextFragment -> fragments += item
                is CharSequence -> fragments += TextFragment(defaultStyle, item.toString())
                null -> {}
                else -> return null
            }
        }
        return fragments
    }
}

internal fun registerMessages(translations: Map<String, String>) = MessageCatalog.register(translations)

internal fun messageRuns(pattern: String, vararg args: Pair<String, Any?>): List<TextFragment> =
    MessageCatalog.runs(pattern, *args)

internal fun messageText(pattern: String, vararg args: Pair<String, Any?>): String =
    MessageCatalog.text(pattern, *args)

internal fun translate(pattern: String): String = MessageCatalog.translate(pattern)

internal fun Terminal.emit(fragment: TextFragment) {
    when (fragment.style) {
        ContentStyle.PLAIN -> emitPlain(fragment.text)
        ContentStyle.STRONG -> emitStrong(fragment.text)
        ContentStyle.PARAM -> emitParam(fragment.text)
        ContentStyle.ERROR -> emitError(fragment.text)
    }
}

internal fun Terminal.emitAll(fragments: List<TextFragment>) {
    fragments.forEach { emit(it) }
}

internal fun resetMemory() {
    MessageCatalog.resetMemory()
}
