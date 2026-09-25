/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import java.io.FileNotFoundException

/**
 * Why a document could not be loaded, as [PdfViewerState.error]. The platform throws the same
 * exception type for different causes (a `SecurityException` means a revoked permission while
 * opening the file, and a password while parsing it), so the cause is told apart where the engine
 * still knows which step failed, and callers read [reason] instead of guessing from [cause].
 */
class PdfLoadException(val reason: Reason, cause: Throwable? = null) :
    Exception("The document could not be loaded: $reason", cause) {

    enum class Reason {
        /** The file is not there any more. */
        NOT_FOUND,

        /** The file is there, but this app may not read it. */
        ACCESS_DENIED,

        /** The document is encrypted, which the platform renderer cannot open. */
        PASSWORD_PROTECTED,

        /** The file was read, but is not a PDF the renderer understands. */
        DAMAGED,

        /** Anything else, such as a failed download. */
        UNKNOWN,
    }

    internal companion object {
        /** A failure while getting at the file's bytes. */
        fun whileReading(cause: Throwable): PdfLoadException =
            cause as? PdfLoadException
                ?: PdfLoadException(
                    reason =
                        when (cause) {
                            is FileNotFoundException -> Reason.NOT_FOUND
                            is SecurityException -> Reason.ACCESS_DENIED
                            else -> Reason.UNKNOWN
                        },
                    cause = cause,
                )

        /** A failure once the bytes are there, while the renderer reads them. */
        fun whileParsing(cause: Throwable): PdfLoadException =
            cause as? PdfLoadException
                ?: PdfLoadException(
                    reason =
                        if (cause is SecurityException) Reason.PASSWORD_PROTECTED
                        else Reason.DAMAGED,
                    cause = cause,
                )
    }
}
