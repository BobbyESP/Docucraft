/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.navigation.ACTION_OPEN_DOCUMENT
import com.bobbyesp.docucraft.feature.docscanner.navigation.ACTION_SCAN_DOCUMENT
import com.bobbyesp.docucraft.feature.docscanner.navigation.ACTION_SEARCH_DOCUMENTS
import com.bobbyesp.docucraft.feature.docscanner.navigation.ENTRY_ACTIVITY
import com.bobbyesp.docucraft.feature.docscanner.navigation.EXTRA_DOCUMENT_UUID

/**
 * What a press on a widget does. Each opens [MainActivity] directly: a press that went through a
 * receiver first could be refused by the system as a launch from the background, and would then do
 * nothing at all.
 */
internal object WidgetIntents {

    fun scan(context: Context): PendingIntent =
        activity(context, entry(context).setAction(ACTION_SCAN_DOCUMENT))

    fun search(context: Context): PendingIntent =
        activity(
            context,
            entry(context).setAction(ACTION_SEARCH_DOCUMENTS),
        )

    fun openDocument(context: Context, documentUuid: String): PendingIntent =
        activity(
            context,
            entry(context)
                .setAction(ACTION_OPEN_DOCUMENT)
                // The system tells two pending intents apart by everything but their extras, so
                // without this every row would open the document of the last one built.
                .setData(Uri.fromParts(DOCUMENT_SCHEME, documentUuid, null))
                .putExtra(EXTRA_DOCUMENT_UUID, documentUuid),
        )

    /** The app as it was left, as its icon would open it. */
    fun openApp(context: Context): PendingIntent =
        activity(
            context,
            entry(context).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        )

    /** An intent for `MainActivity`, the app's entry, named rather than referred to. */
    private fun entry(context: Context): Intent =
        Intent().setComponent(ComponentName(context.packageName, ENTRY_ACTIVITY))

    private fun activity(context: Context, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val DOCUMENT_SCHEME = "docucraft-document"
}
