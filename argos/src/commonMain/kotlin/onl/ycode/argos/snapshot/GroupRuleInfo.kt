/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.snapshot

/**
 * Information about a group rule constraint.
 *
 * @property kind The type of group rule ("exactly-one", "at-most-one", or "at-least-one")
 * @property owners Set of option names that are part of this group
 */
data class GroupRuleInfo(
    val kind: String,
    val owners: Set<String>
)