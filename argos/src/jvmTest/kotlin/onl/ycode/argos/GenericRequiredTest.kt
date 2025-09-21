/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenericRequiredTest {

    data class CustomType(val value: String)

    @Test
    fun customType_required_makesNonNullable() {
        class CustomTypeArgs : Arguments() {
            // This should now work: .map{} returns OptionBuilder<CustomType?>
            // and .required() should convert it to OptionBuilder<CustomType>
            val customValue by option("--custom").map { CustomType(it!!) }.required()
        }

        val args = CustomTypeArgs()
        args.parseWithException(arrayOf("--custom", "test"))

        // This should work without NPE since customValue is non-nullable
        assertEquals("test", args.customValue.value)
    }


    @Test
    fun customType_required_throwsUninitializedPropertyException_whenNotProvided() {
        class CustomTypeArgs : Arguments() {
            val help by option("--help").bool().eager().help("Show help")
            val customValue by option("--custom").map { CustomType(it!!) }.required()
        }

        val args = CustomTypeArgs()
        args.parseWithException(arrayOf("--help"))

        // Should throw our descriptive exception instead of NPE
        assertFailsWith<UninitializedPropertyException> {
            args.customValue.value
        }
    }

    @Test
    fun customType_nullable_returnsNull_whenNotProvided() {
        class CustomTypeArgs : Arguments() {
            // Without .required(), it should remain nullable
            val customValue by option("--custom").map { CustomType(it!!) }
        }

        val args = CustomTypeArgs()
        args.parseWithException(arrayOf())

        // Should return null for nullable custom type
        assertEquals(null, args.customValue)
    }
}