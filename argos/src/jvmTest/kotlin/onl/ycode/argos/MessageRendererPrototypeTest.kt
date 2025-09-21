/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class RequiredOptionArgs : Arguments(
    appName = "message-renderer-prototype",
    useANSITerminal = false
) {
    val foo by option("--foo").required()
}

class MessageRendererPrototypeTest {
    @Test
    fun missing_required_option_uses_template_renderer() {
        val args = RequiredOptionArgs()
        val error = assertFailsWith<ParseError> { args.parseWithException(emptyArray()) }
        assertEquals("Option --foo is required", error.message)
    }
}
