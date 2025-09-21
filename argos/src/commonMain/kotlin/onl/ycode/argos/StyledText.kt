package onl.ycode.argos

import onl.ycode.argos.terminal.ContentStyle
import onl.ycode.argos.terminal.ContentStyle.ERROR
import onl.ycode.argos.terminal.ContentStyle.PARAM
import onl.ycode.argos.terminal.ContentStyle.STRONG

/**
 * Removes all style tags from a string, returning plain text.
 *
 * @receiver The styled string to strip
 * @return The plain text without any style tags
 */
fun String.stripStyles(): String {
    var results = this
    ContentStyle.entries.forEach { results = results.replace(it.tag, "") }
    return results
}

/**
 * Formats a string as an option name with appropriate styling for display.
 * @receiver The option name (e.g., "--port")
 * @return Styled string suitable for terminal output
 */
fun String?.asOption() = OptionTag.asTag(this)

/**
 * Formats a collection of option names as a comma-separated list with styling.
 * @receiver Collection of option names
 * @return Styled, comma-separated string
 */
fun Collection<String>?.asOptionList() = asList(this, OptionTag)

/**
 * Formats a string as a value with appropriate styling for display.
 * @receiver The value (e.g., "8080")
 * @return Styled string suitable for terminal output
 */
fun String?.asValue() = ValueTag.asTag(this)

/**
 * Formats a collection of expected values as a comma-separated list with styling.
 * @receiver Collection of expected values
 * @return Styled, comma-separated string
 */
fun Collection<String>?.asValueListExpected() = asList(this, ValueTag)

/**
 * Formats a collection of unexpected values as a quoted, comma-separated list with styling.
 * @receiver Collection of unexpected values
 * @return Styled, quoted, comma-separated string
 */
fun Collection<String>?.asValueListUnexpected() = asList(this, ValueTag, "'")

/**
 * Formats a string as a domain/subcommand name with appropriate styling.
 * @receiver The domain name
 * @return Styled string suitable for terminal output
 */
fun String?.asDomain() = DomainTag.asTag(this)

/**
 * Formats a collection of domain names as a comma-separated list with styling.
 * @receiver Collection of domain names
 * @return Styled, comma-separated string
 */
fun Collection<String>?.asDomainList() = asList(this, DomainTag)

/**
 * Formats a positional argument index with appropriate styling.
 * @receiver The position index
 * @return Styled string suitable for terminal output
 */
fun Int?.asPosition() = PositionalTag.asTag(this?.toString())

/**
 * Formats a string as an application name with appropriate styling.
 * @receiver The application name
 * @return Styled string suitable for terminal output
 */
fun String.asAppName() = AppNameTag.asTag(this)

/**
 * Formats a string as a section header with appropriate styling.
 * @receiver The section name
 * @return Styled string suitable for terminal output
 */
fun String.asSection() = SectionTag.asTag(this)

/**
 * Formats a string as an error message with appropriate styling.
 * @receiver The error message
 * @return Styled string suitable for terminal output
 */
fun String.asError() = ErrorTag.asTag(this)

/**
 * Formats a string as domain information with appropriate styling.
 * @receiver The domain information text
 * @return Styled string suitable for terminal output
 */
fun String.asDomainInfo() = DomainInfoTag.asTag(this)

private val DomainTag = PARAM
private val DomainInfoTag = PARAM
private val OptionTag = PARAM
private val ValueTag = STRONG
private val PositionalTag = STRONG
private val AppNameTag = STRONG
private val SectionTag = STRONG
private val ErrorTag = ERROR


internal val List<String>.asBaseSwitch: String get() = maxBy { it.length }


private fun asList(data: Collection<String>?, style: ContentStyle, quotes: String = "") =
    data?.joinToString(", ") { "$quotes${style.asTag(it)}$quotes" } ?: ""