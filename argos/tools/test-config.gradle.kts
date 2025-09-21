/**
 * JVM test configuration and result summary functionality.
 */

// Configure JVM test task
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    // Set environment variables for tests
    environment("_ARGOS_TEST_MODE_", "true")

    testLogging {
        events("FAILED", "SKIPPED", "PASSED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
        showCauses = true
        showExceptions = true
        showStackTraces = true
        displayGranularity = 2
    }

    // Add test result summary
    doLast {
        generateTestSummary()
    }
}

/**
 * Generates and displays a summary of test results by parsing JUnit XML files.
 */
fun generateTestSummary() {
    val testResultsDir = file("build/test-results/jvmTest")
    var totalTests = 0
    var passedTests = 0
    var failedTests = 0
    var skippedTests = 0

    if (testResultsDir.exists()) {
        testResultsDir.walkTopDown()
            .filter { it.name.endsWith(".xml") }
            .forEach { xmlFile ->
                try {
                    val content = xmlFile.readText()
                    // Parse test results from XML
                    val testsMatch = "tests=\"(\\d+)\"".toRegex().find(content)
                    val failuresMatch = "failures=\"(\\d+)\"".toRegex().find(content)
                    val errorsMatch = "errors=\"(\\d+)\"".toRegex().find(content)
                    val skippedMatch = "skipped=\"(\\d+)\"".toRegex().find(content)

                    if (testsMatch != null) {
                        val tests = testsMatch.groupValues[1].toIntOrNull() ?: 0
                        val failures = failuresMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val errors = errorsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val skipped = skippedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                        totalTests += tests
                        failedTests += failures + errors
                        skippedTests += skipped
                        passedTests += tests - failures - errors - skipped
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors and continue with other files
                }
            }
    }

    printTestSummary(totalTests, passedTests, failedTests, skippedTests)
}

/**
 * Prints a formatted test summary with emojis and borders.
 */
fun printTestSummary(totalTests: Int, passedTests: Int, failedTests: Int, skippedTests: Int) {
    println("\n" + "=".repeat(80))
    println("🧪 JVM Test Results Summary")
    println("=".repeat(80))

    if (totalTests > 0) {
        println("Total tests: $totalTests")
        println("✅ Passed: $passedTests")
        if (failedTests > 0) {
            println("❌ Failed: $failedTests")
        }
        if (skippedTests > 0) {
            println("⏭️  Skipped: $skippedTests")
        }
        println("=".repeat(80))

        if (failedTests > 0) {
            println("❌ Some tests failed!")
        } else {
            println("✅ All tests passed!")
        }
    } else {
        println("No test results found")
        println("=".repeat(80))
    }
    println()
}