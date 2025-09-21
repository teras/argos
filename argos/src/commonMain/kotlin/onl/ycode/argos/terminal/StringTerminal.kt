package onl.ycode.argos.terminal

/**
 * A terminal implementation that captures output to a string buffer.
 *
 * Useful for testing or generating help text programmatically without writing to stdout.
 * The captured output can be retrieved using [toString].
 *
 * @param infoColumn Optional column position for aligning help text information (defaults to Terminal's default)
 */
class StringTerminal(infoColumn: Int?) : Terminal {

    private val sb = StringBuilder()

    override fun emitPlain(text: String) {
        sb.append(text)
    }

    override val infoColumn = infoColumn ?: super.infoColumn

    override val width get() = Int.MAX_VALUE

    /**
     * Returns the accumulated terminal output as a string.
     * @return All text written to this terminal
     */
    override fun toString() = sb.toString()
}