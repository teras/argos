package onl.ycode.argos.snapshot

/**
 * A snapshot of the argument parser configuration.
 *
 * This immutable data structure captures the complete configuration of an Arguments instance,
 * including all options, positionals, domains, and constraints. It can be used for:
 * - Introspection and analysis of the CLI structure
 * - Generating documentation or help text
 * - Validating configurations
 * - Testing and debugging
 *
 * @property settings The parser configuration settings
 * @property domains List of defined domains/subcommands
 * @property options List of option specifications
 * @property positionals List of positional argument specifications
 * @property groups List of group rule constraints
 * @property conflicts List of conflicting option sets
 * @property conditionals List of conditional requirements
 * @property errors List of validation errors encountered during configuration
 */
data class Snapshot(
    val settings: Settings,
    val domains: List<DomainInfo>,
    val options: List<SpecInfo>,
    val positionals: List<PositionalInfo>,
    val groups: List<GroupRuleInfo>,
    val conflicts: List<Set<String>>,
    val conditionals: List<ConditionalInfo>,
    val errors: List<String>
)