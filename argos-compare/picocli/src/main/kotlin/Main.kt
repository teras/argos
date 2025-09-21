import picocli.CommandLine
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "picocli",
    description = ["A simple hello world CLI application"]
)
class HelloCommand : Runnable {

    @CommandLine.Option(
        names = ["--name"],
        description = ["Name to greet"],
        defaultValue = "World"
    )
    var name: String = "World"

    @CommandLine.Option(
        names = ["--number"],
        description = ["Number of times to greet"],
        defaultValue = "0"
    )
    var number: Int = 0

    override fun run() {
        for (i in 0..<number)
            println("Hello, $name! (from picocli)")
    }
}

fun main(args: Array<String>) {
    val args = if (args.isEmpty()) arrayOf("--number", "1", "--name", "Test") else args
    val exitCode = CommandLine(HelloCommand()).execute(*args)
    exitProcess(exitCode)
}