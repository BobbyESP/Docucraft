/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guards the one thing about navigation keys that nothing else would catch: they are restored by
 * reflection, not by code anyone can read.
 *
 * `rememberNavBackStack` saves the back stack through `NavKeySerializer`, which writes a key's
 * fully qualified class name and reads it back with `Class.forName(name).kotlin.serializer()`. A
 * key that R8 renames, or that quietly loses its `@Serializable`, keeps compiling and keeps working
 * right up until the process is killed with that key on the stack — in release builds only.
 *
 * This performs exactly that lookup, so the break surfaces here instead of on someone's device.
 */
class NavKeySerializationTest {

    @Test
    fun `every navigation key resolves a serializer by class name`() {
        allKeys.forEach { key ->
            val className = key.javaClass.name

            assertNotNull(
                "No serializer could be resolved by name for $className",
                runCatching { serializerForClassName(className) }.getOrNull(),
            )
        }
    }

    @Test
    fun `every navigation key survives a round trip through its resolved serializer`() {
        allKeys.forEach { key -> assertEquals("Round trip changed $key", key, roundTrip(key)) }
    }

    /**
     * Data objects and data classes come back by different routes — one by `INSTANCE`, the other
     * through a generated serializer — and only the second can lose its arguments on the way.
     */
    @Test
    fun `a key carrying an argument restores that argument`() {
        val restored = roundTrip(Route.PdfViewer(documentUuid = "a-document-uuid"))

        assertEquals("a-document-uuid", (restored as Route.PdfViewer).documentUuid)
    }

    @Suppress("UNCHECKED_CAST")
    private fun roundTrip(key: NavKey): NavKey {
        val serializer = serializerForClassName(key.javaClass.name) as KSerializer<NavKey>

        return Json.decodeFromString(serializer, Json.encodeToString(serializer, key))
    }

    /** The same lookup that `NavKeySerializer.deserialize` performs. */
    @OptIn(InternalSerializationApi::class)
    private fun serializerForClassName(className: String): KSerializer<out Any> =
        Class.forName(className).kotlin.serializer()

    private companion object {
        /**
         * Listed by hand rather than found by scanning the classpath: a key nobody remembered to
         * add here is a key nobody thought about, and that is worth catching in review.
         */
        val allKeys: List<NavKey> =
            listOf(
                Route.Home,
                Route.PdfViewer(documentUuid = "uuid"),
                Route.Settings,
                Route.Settings.Appearance,
                Route.Settings.CustomerCenter,
            )
    }
}
