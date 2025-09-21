/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

/**
 * Exception thrown when command-line argument parsing fails.
 *
 * ParseError indicates that the user provided invalid or incomplete arguments that violate the
 * defined options, constraints, or validation rules. This exception is thrown during the parsing
 * phase when processing command-line arguments. Common causes include:
 *
 * - **Invalid option values**: Type conversion failures (e.g., non-numeric value for `.int()`)
 * - **Missing required options**: Options marked with `.required()` not provided
 * - **Constraint violations**: Violations of constraints like `.conflicts()`, `.requireIfAnyPresent()`, etc.
 * - **Validation failures**: Custom validation predicates returning false
 * - **Unknown options**: Unrecognized command-line switches
 * - **Arity mismatches**: Wrong number of values for options with `.arity(n)`
 *
 * **Handling ParseError:**
 * Use the `.parse()` extension function which provides structured error handling:
 * ```kotlin
 * val args = MyArgs().parse(argv,
 *     onSuccess = { println("Parsed successfully") },
 *     onError = { error, args ->
 *         // error is ParseError
 *         println("Error: ${error.message}")
 *         args.printUsage()
 *     }
 * )
 * ```
 *
 * Alternatively, use `.parseWithException()` to handle exceptions directly:
 * ```kotlin
 * try {
 *     val args = MyArgs().parseWithException(argv)
 * } catch (e: ParseError) {
 *     println("Parse error: ${e.message}")
 * }
 * ```
 *
 * @see Arguments.parse For error-callback-based parsing
 * @see Arguments.parseWithException For exception-based parsing
 */
class ParseError(msg: String) : IllegalArgumentException(msg)

/**
 * Exception thrown when accessing a non-nullable property that was never initialized with a value.
 *
 * This exception occurs when a property is declared as non-nullable (using `.required()` or `.default()`)
 * but no value was actually provided during parsing, and the property is subsequently accessed. This
 * is an internal safety mechanism that should rarely occur in practice if parsing completes successfully.
 *
 * **Common Causes:**
 * - Accessing a property before calling `parse()` or `parseWithException()`
 * - Internal parsing logic errors (should not happen in normal usage)
 * - Manually triggering property access outside normal parsing flow
 *
 * **Prevention:**
 * Always ensure parsing completes before accessing any properties:
 * ```kotlin
 * val app = MyApp()
 * // Don't access app.someOption here!
 * app.parseWithException(args)
 * // Safe to access properties after parsing
 * println(app.someOption)
 * ```
 *
 * @param propertyName The name of the uninitialized property
 */
class UninitializedPropertyException(
    propertyName: String
) : Exception("Property '$propertyName' was not initialized.")

internal class ConfigException(msg: String) : IllegalStateException(msg)
