/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.snapshot

import onl.ycode.argos.snapshot.PositionalKind

/**
 * Information about a positional argument specification.
 *
 * @property name The internal name of the positional argument (property name)
 * @property kind The kind of positional argument (SINGLE, LIST, or SET)
 * @property repeatable Whether this positional can accept multiple values
 * @property declaredDomains Set of domain IDs where this positional is allowed, null means all domains
 * @property help Optional help text describing this positional argument
 * @property expectedDesc Optional list of descriptions for expected values
 * @property seq Sequence number determining the order of positional arguments
 * @property minRequiredGlobal Minimum number of times this positional must be provided globally
 */
data class PositionalInfo(
    val name: String,
    val kind: PositionalKind,
    val repeatable: Boolean,
    val declaredDomains: Set<String>?,
    val help: String?,
    val expectedDesc: List<String>?,
    val seq: Int,
    val minRequiredGlobal: Int
)