/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.snapshot

/**
 * Information about a domain/subcommand.
 *
 * @property id The unique identifier for this domain
 * @property label Optional human-readable label for the domain
 * @property description Optional description of what this domain does
 * @property aliases Set of alternative names that can be used to refer to this domain
 */
data class DomainInfo(
    val id: String,
    val label: String?,
    val description: String?,
    val aliases: Set<String>
)