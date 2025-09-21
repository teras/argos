import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class ClicktCommand : CliktCommand(
    name = "clikt",
    help = "A simple hello world CLI application"
) {
    private val name by option("--name", help = "Name to greet").default("World")
    private val number by option("--number", help = "Number of times to greet").int().default(0)

    override fun run() {
        for (i in 0..<number)
            println("Hello, $name! (from clikt)")
    }
}

fun main(args: Array<String>) {
    val args = if (args.isEmpty()) arrayOf("--number", "1", "--name", "Test") else args
    ClicktCommand().main(args)
}