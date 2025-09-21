import onl.ycode.argos.*

class ArgosCommand : Arguments(
    appName = "argos",
    appDescription = "A simple hello world CLI application"
) {
    val name by option("--name").default("World")
    val number by option("--number").int().default(0)

    fun run() {
        for (i in 0..<number)
            println("Hello, $name! (from argos)")
    }
}

fun main(args: Array<String>) {
    val testArgs = if (args.isEmpty()) arrayOf("--number", "1", "--name", "Test") else args
    ArgosCommand().parse(testArgs)?.run()
}
