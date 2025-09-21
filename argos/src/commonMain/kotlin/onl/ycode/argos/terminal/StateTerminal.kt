/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.terminal

/**
 * Base class for terminal implementations that maintain formatting state.
 *
 * This abstract class provides a foundation for terminals that need to track
 * the current formatting state and transition between different styles.
 * It handles the logic of opening and closing formatting contexts.
 *
 * Subclasses should override the opening/closing methods for each style
 * and implement [sendText] for actual output.
 */
abstract class StateTerminal : Terminal {
    protected var lastState = ContentStyle.PLAIN
        private set

    /**
     * Called when transitioning to plain text formatting.
     *
     * @param previous The previous content style being closed
     */
    open fun openingPlain(previous: ContentStyle) {}

    /**
     * Called when closing plain text formatting.
     */
    open fun closingPlain() {}

    /**
     * Called when transitioning to strong/bold text formatting.
     *
     * @param previous The previous content style being closed
     */
    open fun openingStrong(previous: ContentStyle) {}

    /**
     * Called when closing strong/bold text formatting.
     */
    open fun closingStrong() {}

    /**
     * Called when transitioning to parameter/code text formatting.
     *
     * @param previous The previous content style being closed
     */
    open fun openingParam(previous: ContentStyle) {}

    /**
     * Called when closing parameter/code text formatting.
     */
    open fun closingParam() {}

    /**
     * Called when transitioning to error text formatting.
     *
     * @param previous The previous content style being closed
     */
    open fun openingError(previous: ContentStyle) {}

    /**
     * Called when closing error text formatting.
     */
    open fun closingError() {}

    /**
     * Sends raw text to the output destination.
     *
     * This method should handle the actual output mechanism (stdout, stderr, etc.)
     * and is called after appropriate formatting has been applied.
     *
     * @param text The text to send to output
     */
    abstract fun sendText(text: String)

    protected fun closingPrevious(previous: ContentStyle) {
        when (previous) {
            ContentStyle.PLAIN -> closingPlain()
            ContentStyle.STRONG -> closingStrong()
            ContentStyle.PARAM -> closingParam()
            ContentStyle.ERROR -> closingError()
        }
    }

    override fun emitPlain(text: String) {
        if (lastState != ContentStyle.PLAIN) {
            closingPrevious(lastState)
            openingPlain(lastState)
            lastState = ContentStyle.PLAIN
        }
        sendText(text)
    }

    override fun emitStrong(text: String) {
        if (lastState != ContentStyle.STRONG) {
            closingPrevious(lastState)
            openingStrong(lastState)
            lastState = ContentStyle.STRONG
        }
        sendText(text)
    }

    override fun emitParam(text: String) {
        if (lastState != ContentStyle.PARAM) {
            closingPrevious(lastState)
            openingParam(lastState)
            lastState = ContentStyle.PARAM
        }
        sendText(text)
    }

    override fun emitError(text: String) {
        if (lastState != ContentStyle.ERROR) {
            closingPrevious(lastState)
            openingError(lastState)
            lastState = ContentStyle.ERROR
        }
        sendText(text)
    }

    override fun startEmit() {
        closingPrevious(lastState)
        lastState = ContentStyle.PLAIN
    }

    override fun endEmit() {
        closingPrevious(lastState)
        lastState = ContentStyle.PLAIN
    }
}