package onl.ycode.argos.snapshot

/**
 * Configuration settings for the argument parser.
 *
 * @property appName The name of the application
 * @property appDescription Optional description of the application
 * @property defaultLongPrefix The default prefix for long options (e.g., "--")
 * @property clusterChar Optional character for clustering short options (e.g., '-')
 * @property valueSeparators Set of characters that can separate option names from values
 * @property unknownOptionsAsPositionals Whether to treat unknown options as positional arguments
 * @property argumentSeparator Separator used when formatting multiple arguments
 */
data class Settings(
    val appName: String,
    val appDescription: String?,
    val defaultLongPrefix: String,
    val clusterChar: Char?,
    val valueSeparators: Set<Char>,
    val unknownOptionsAsPositionals: Boolean,
    val argumentSeparator: String
)