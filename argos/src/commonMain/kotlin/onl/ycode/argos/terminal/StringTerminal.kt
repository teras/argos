package onl.ycode.argos.terminal

class StringTerminal(infoColumn: Int?) : Terminal {

    private val sb = StringBuilder()

    override fun emitPlain(text: String) {
        sb.append(text)
    }

    override val infoColumn = infoColumn ?: super.infoColumn

    override val width get() = Int.MAX_VALUE

    override fun toString() = sb.toString()
}