/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.snapshot

/**
 * Information about an option specification.
 *
 * @property ownerName The internal name of the option (property name)
 * @property switches List of switches that can trigger this option (e.g., ["-v", "--verbose"])
 * @property booleanFlag Whether this is a boolean flag option
 * @property negationPrefix Optional prefix for negating boolean flags (e.g., "no-")
 * @property requiresValue Whether this option requires a value
 * @property repeatable Whether this option can be specified multiple times
 * @property declaredDomains Set of domain IDs where this option is allowed, null means all domains
 * @property help Optional help text describing this option
 * @property expectedDesc Optional list of descriptions for expected values
 * @property minRequiredGlobal Minimum number of times this option must be provided globally
 * @property hidden Whether this option is hidden from help output
 * @property defaultValue Optional default value as a string
 * @property envVar Optional environment variable that can provide a value for this option
 */
data class SpecInfo(
    val ownerName: String,
    val switches: List<String>,
    val booleanFlag: Boolean,
    val negationPrefix: String?,
    val requiresValue: Boolean,
    val repeatable: Boolean,
    val declaredDomains: Set<String>?,
    val help: String?,
    val expectedDesc: List<String>?,
    val minRequiredGlobal: Int,
    val hidden: Boolean = false,
    val defaultValue: String? = null,
    val envVar: String? = null
)