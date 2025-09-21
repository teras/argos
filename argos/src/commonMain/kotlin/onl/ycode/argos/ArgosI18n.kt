/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

/**
 * Global internationalization (i18n) registry for customizing error messages and help text.
 *
 * Use this singleton to register custom translations for error messages, help text, and other
 * user-facing strings in the Argos library. By default, all text is in English. Register
 * translations to provide localized messages for your application.
 *
 * ## Usage Example
 *
 * ```kotlin
 * ArgosI18n.register(
 *     "Missing value for {option}" to "Fehlender Wert für {option}",
 *     "Invalid value '{value}' for {option}" to "Ungültiger Wert '{value}' für {option}"
 * )
 * ```
 *
 * Translation strings may contain placeholders like `{option}`, `{value}`, `{position}`, etc.
 * that will be replaced with context-specific values during runtime.
 */
object ArgosI18n {
    private val translations = mutableMapOf<String, String>()

    /**
     * Registers a map of translations.
     *
     * @param translations Map from English source text to translated text
     */
    fun register(translations: Map<String, String>) {
        this.translations.putAll(translations)
    }

    /**
     * Registers translations from variable-argument pairs.
     *
     * @param translations Pairs of (source text, translated text)
     */
    fun register(vararg translations: Pair<String, String>) {
        translations.forEach { this.translations[it.first] = it.second }
    }

    /**
     * Translates a source text string using registered translations.
     *
     * @param text Source text to translate
     * @return Translated text if a translation is registered, otherwise returns the original text
     */
    fun translate(text: String): String = translations[text] ?: text

    /**
     * Clears all registered translations.
     *
     * Useful for testing or resetting i18n state.
     */
    fun resetMemory() {
        translations.clear()
    }
}
