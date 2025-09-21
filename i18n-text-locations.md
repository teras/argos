# Argos I18n Message Reference

This document tracks every user-visible message that flows through the Argos CLI parser and describes the placeholder contract for translators.

Use `ArgosI18n.register(mapOf("English text" to "Localized text"))` to supply your own translations. Any pattern not present in the map falls back to the English key automatically.

## Error & Validation Messages (`Arguments.kt`)

These english patterns double as lookup keys. Provide translations using the exact text (placeholders stay in braces):

- `Option {option} is required`
- `Option {option} requires at least {min} occurrences (got {count})`
- `Missing value for {option_token}{position}`
- `Missing value {index} of {total} for {option_token}{position}`
- `Unknown option {option_token}{position}`
- `Unknown option {option_token}{position}. Did you mean {hint}?`
- `Unknown option {option_token} in '{cluster}'{position}`
- `Option {option_token} requires a value and must be last in '{cluster}'{position}`
- `{option} allowed only in: {domains}{selected_domain}`
- `Option {option} provided multiple times`
- `Option {option} is not allowed because {reference} has that value`
- `Option {option} is required because {references} are present`
- `Option {option} is required because {references} are all present`
- `Option {option} is required because {references} are absent`
- `Option {option} is required because {references} are all absent`
- `Option {option} is required because {reference} has that value`
- `Conflicting options: {options}`
- `Unknown domain '{domain}'. Expected: {expected}{position}`
- `A domain is required. Choose one of: {domains}`
- `Unexpected positional argument '{argument}'{position}`
- `Unexpected positional arguments: {arguments}`
- `Exactly one of ({options}) must be provided (got {count})`
- `At most one of ({options}) may be provided (got {count})`
- `At least one of ({options}) is required`

## Help & Usage Messages (`Help.kt`)

Usage output now references the following patterns:

- ` — ` (header separator)
- `Usage:`
- `Arguments:`
- `Type {type}`
- `type {type}`
- ` — ` (argument help separator)
- `Options:`
- `default: {value}`
- `env: {value}`
- `required`
- `domains: {domains}`
- `choices: {choices}`
- `Exactly one of {options}`
- `At most one of {options}`
- `At least one of {options}`
- `Only one of {options}`
- `Required {option} if exists any of: {references}`
- `Required {option} if exists all of: {references}`
- `Required {option} if {reference} has specific value`
- `specific option`
- `Required {argument} at least {count} occurrences`
- `Error:`

## Placeholder Contract

Messages may embed named placeholders. Keep the names intact; the renderer substitutes fragments with styling applied where needed.

| Placeholder | Meaning | Style |
|-------------|---------|-------|
| `{option}` | Option reference (switch or positional) | rendered with option styling (`--flag`/`<positional>`) |
| `{options}` | Comma-separated list of option references | same as `{option}` joined with plain separators |
| `{option_token}` | Literal switch token such as `--output` | styled as a parameter |
| `{hint}` | Suggested switch returned by “did you mean?” | styled as a parameter |
| `{position}` | Positional index hint (e.g. ` at position #3`) | plain text |
| `{argument}` | Positional argument token (e.g. `<file>`) | styled as a parameter |
| `{arguments}` | Arbitrary list of arguments rendered as plain text | plain text |
| `{domains}` | Pre-formatted domain list such as `alpha, beta` | plain text |
| `{selected_domain}` | Optional suffix indicating which domain was selected | plain text (empty when not applicable) |
| `{count}` | Numeric minimum in repeatable/constraint messages | plain text |
| `{min}` | Required minimum occurrences in validation errors | plain text |
| `{index}`, `{total}` | Indices used for arity errors | plain text |
| `{type}` | Type descriptor (`int`, `UUID`, …) | plain text |
| `{value}` | Default or environment value | plain text |
| `{choices}` | Comma-separated choice list | plain text |
Pass lists to `{options}` only with fully-qualified labels (either specs or strings); the renderer inserts commas automatically.

When a placeholder is not applicable (for example `{selected_domain}` for global options), supply an empty string. The renderer will omit the segment.

## Styling

The renderer maps message fragments to terminal styles as follows:

- Plain text → regular output
- `{option}`, `{option_token}`, `{hint}`, `{argument}` → parameter style
- Section titles (`help.*.title`) should be emitted with `emitStrong`
- `{count}`, `{value}`, `{domains}` → plain text in the surrounding sentence

## Adding New Messages

1. Define the key and English template in `Messages.kt`.
2. At the call site, wrap the literal with `translate("…")` and pass the translated value into `messageText(…)` or `messageRuns(…)`.
3. If the template introduces a new placeholder, update `Messages.renderPlaceholder` with the styling rules.
4. Update this document with the new key and placeholder description.

Keep messages plural-agnostic when possible. If a template truly requires singular/plural distinctions, prefer wording such as “{count} occurrences” to avoid additional branching in translations.
