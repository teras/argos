/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.snapshot

/**
 * Information about a conditional requirement.
 *
 * @property targetOwner The option that is conditionally required
 * @property kind The type of conditional requirement ("if-any-present", "if-all-present", "if-any-absent", "if-all-absent", "if-value", "allow-only-if-value")
 * @property refs Set of option names that are referenced in this conditional
 * @property predicateRef Optional reference to an option whose value is checked by a predicate
 * @property limitToDomains Optional set of domain IDs where this conditional applies, null means all domains
 */
data class ConditionalInfo(
    val targetOwner: String,
    val kind: String,
    val refs: Set<String>,
    val predicateRef: String?,
    val limitToDomains: Set<String>?
)