/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

/**
 * Exception thrown when argument parsing fails due to invalid input,
 * missing required arguments, or constraint violations.
 */
class ParseError(msg: String) : IllegalArgumentException(msg)

/**
 * Exception thrown when attempting to access an uninitialized property
 * that was declared as non-nullable but no value was provided.
 */
class UninitializedPropertyException(
    propertyName: String
) : Exception("Property '$propertyName' was not initialized.")