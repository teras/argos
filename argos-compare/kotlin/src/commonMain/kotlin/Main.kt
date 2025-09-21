class Parser(args: Array<String>) {
    var name = "World"
    var number = 0

    init {
        for (i in args.indices) {
            when (args[i]) {
                "--name" -> name = args.getOrNull(i + 1) ?: name
                "--number" -> number = args.getOrNull(i + 1)?.toIntOrNull() ?: number
            }
        }
    }

    fun run() {
        for (i in 0..<number)
            println("Hello, $name! (from kotlin)")
    }
}

fun main(args: Array<String>) {
    val testArgs = if (args.isEmpty()) arrayOf("--number", "1", "--name", "Test") else args
    Parser(testArgs).run()
}
