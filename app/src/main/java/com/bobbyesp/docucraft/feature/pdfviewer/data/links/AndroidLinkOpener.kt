/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.links

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkLook
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkOpener

/**
 * Opens links from an activity (D4), never from the application context: a Custom Tab started with
 * a new task would open outside the app, and back would no longer return to the document.
 *
 * - A web page opens in a Custom Tab, which stays in the app's task, shares the browser's passwords
 *   and sessions, and offers to move to the browser itself. Without a browser that supports them,
 *   it opens with `ACTION_VIEW`; without any app for the web, it reports so.
 * - An email opens with `ACTION_SENDTO`, and a phone number with `ACTION_DIAL`, which types the
 *   number in and leaves calling to the reader: no permission is needed.
 */
class AndroidLinkOpener(private val activity: Activity) : LinkOpener {

    override fun open(action: LinkAction, look: LinkLook): Boolean =
        when (action) {
            is LinkAction.OpenWeb -> openWeb(action.url.toUri(), look)
            is LinkAction.ComposeEmail -> start(Intent(Intent.ACTION_SENDTO, action.uri.toUri()))
            is LinkAction.Dial -> start(Intent(Intent.ACTION_DIAL, action.uri.toUri()))
            is LinkAction.GoTo,
            is LinkAction.Blocked -> false
        }

    private fun openWeb(uri: Uri, look: LinkLook): Boolean {
        // The default browser if it offers Custom Tabs, else any that does; null if none.
        val provider = CustomTabsClient.getPackageName(activity, emptyList())
        if (provider != null) {
            val colors =
                CustomTabColorSchemeParams.Builder().setToolbarColor(look.toolbarColor).build()
            val tab =
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setColorScheme(
                        if (look.darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK
                        else CustomTabsIntent.COLOR_SCHEME_LIGHT
                    )
                    .setDefaultColorSchemeParams(colors)
                    .build()
            tab.intent.setPackage(provider)
            try {
                tab.launchUrl(activity, uri)
                return true
            } catch (_: ActivityNotFoundException) {
                // The provider went away between asking and opening: fall through.
            }
        }
        return start(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    private fun start(intent: Intent): Boolean =
        try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
