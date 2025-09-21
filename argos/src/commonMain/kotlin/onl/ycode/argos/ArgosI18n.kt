/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

object ArgosI18n {
    fun register(translations: Map<String, String>) {
        registerMessages(translations)
    }

    fun register(vararg translations: Pair<String, String>) {
        if (translations.isNotEmpty()) {
            registerMessages(mapOf(*translations))
        }
    }
}
