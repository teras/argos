package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Interface providing terminal display metrics for formatting help text and output.
 *
 * Implementations can override these properties to customize text wrapping and alignment behavior.
 */
interface TerminalMetrics {
    /** The terminal width in characters for text wrapping. Defaults to system terminal width. */
    val width: Int get() = OsBound.termWidth()

    /** Column position for aligning help text descriptions. */
    val infoColumn: Int get() = 25

}