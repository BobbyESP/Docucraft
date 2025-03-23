package com.bobbyesp.docucraft.core.util.state

/**
 * An [Event] is a wrapper class that provides a way to handle events once.
 *
 * It's often used in situations where you need to prevent multiple observers from
 * processing the same event multiple times, such as navigation events or showing
 * a transient message (e.g., a toast).
 *
 * The core idea is that an [Event] contains some data ([content]) that can only be
 * retrieved once using [getContentIfNotHandled]. After it's retrieved, the [Event]
 * considers it "handled," and subsequent calls to [getContentIfNotHandled] will return `null`.
 *
 * [peekContent] provides a way to access the data without marking the event as handled.
 *
 * @param T The type of data contained within the event.
 * @property content The data encapsulated by the event. This is private and only accessible through provided functions.
 */
open class Event<out T>(private val content: T) {
    private var hasBeenHandled = false

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}
