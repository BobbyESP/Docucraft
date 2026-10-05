/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.Locale

/**
 * What the screenshots ask of the device itself rather than of the app: a status bar that says the
 * same in every picture, the app in a given language, and a picture of the whole screen.
 */
internal class CaptureDevice(private val instrumentation: Instrumentation) {

    private val packageName = instrumentation.targetContext.packageName

    /** Runs [command] as the shell user and waits for it. */
    fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            )
            .use { it.readBytes().decodeToString() }

    /**
     * Wakes the screen and keeps it on: a screen that went to sleep is photographed black, and
     * takes the status bar's demo mode with it.
     */
    fun keepAwake() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("svc power stayon true")
    }

    /**
     * Puts the status bar in the system's demo mode: 9:41, a full battery, full Wi-Fi and nothing
     * else, whatever the device is really doing.
     *
     * The mobile signal is hidden rather than set: recent versions of the system show the device's
     * own network type beside it whatever demo mode is told. Nor does demo mode hide the icons of
     * the notifications any more, so those are turned off as a whole.
     */
    fun cleanStatusBar() {
        shell("settings put global sysui_demo_allowed 1")
        demo("enter")
        demo("clock -e hhmm 0941")
        demo("battery -e level 100 -e plugged false")
        demo("network -e wifi show -e level 4 -e fully true")
        demo("network -e mobile hide")
        shell("cmd statusbar send-disable-flag notification-icons")
    }

    fun restoreStatusBar() {
        demo("exit")
        // No flag is every flag off.
        shell("cmd statusbar send-disable-flag")
        shell("svc power stayon false")
    }

    private fun demo(command: String) {
        shell("am broadcast -a com.android.systemui.demo -e command $command")
    }

    /**
     * Sets the app's language as the system's per-app setting does, and waits for the app to be
     * told. An empty [languageTag] gives the choice back to the system.
     */
    fun setAppLanguage(languageTag: String) {
        // Without a list of languages the command sets none, which is the system's choice.
        val languages = if (languageTag.isEmpty()) "" else " --locales $languageTag"
        shell("cmd locale set-app-locales $packageName --user current$languages")
        if (languageTag.isEmpty()) return
        val language = Locale.forLanguageTag(languageTag).language
        val deadline = System.currentTimeMillis() + LANGUAGE_TIMEOUT_MS
        while (appLanguage() != language && System.currentTimeMillis() < deadline) Thread.sleep(100)
        check(appLanguage() == language) {
            "The app is in '${appLanguage()}', not in '$language': the device did not apply it"
        }
    }

    private fun appLanguage(): String =
        instrumentation.targetContext.applicationContext.resources.configuration.locales[0].language

    // What follows reads and touches the screen as a person does, through the system, in real
    // time. Compose's own test clock is not used: it stands still unless told to move, and a
    // screen that asks for every frame, as the viewer does while a finger holds a selection,
    // never comes to the rest it waits for.

    /** Whether something on screen says [text], in what it shows or in what it is called. */
    fun shows(text: String): Boolean = find(text) != null

    /** Waits for something on screen to say [text]. */
    fun waitFor(text: String) {
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (!shows(text)) {
            check(SystemClock.uptimeMillis() < deadline) { "Nothing on screen says \"$text\"" }
            Thread.sleep(POLL_MS)
        }
    }

    /** Taps the middle of what says [text]. */
    fun tap(text: String) {
        val bounds = Rect()
        checkNotNull(find(text)) { "Nothing on screen says \"$text\"" }.getBoundsInScreen(bounds)
        val at = PointF(bounds.exactCenterX(), bounds.exactCenterY())
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, at)
        touch(MotionEvent.ACTION_UP, down, at)
    }

    /** Writes [text] in the one field on screen that takes text. */
    fun type(text: String) {
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        var field = editable(root())
        while (field == null) {
            check(SystemClock.uptimeMillis() < deadline) { "No field to write in" }
            Thread.sleep(POLL_MS)
            field = editable(root())
        }
        field.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            },
        )
    }

    /** Presses at [from] until it is a long press, drags to [to] and lets go. */
    fun longPressAndDrag(from: PointF, to: PointF) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, from)
        Thread.sleep(LONG_PRESS_MS)
        repeat(DRAG_STEPS) { step ->
            val fraction = (step + 1f) / DRAG_STEPS
            val at =
                PointF(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction)
            touch(MotionEvent.ACTION_MOVE, down, at)
            Thread.sleep(DRAG_STEP_MS)
        }
        touch(MotionEvent.ACTION_UP, down, to)
    }

    /** The size of the screen, in pixels. */
    fun screenSize(): PointF {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        return PointF(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    }

    private fun touch(action: Int, downTime: Long, at: PointF) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun root(): AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow

    // Walked by hand: the system's own search by text does not look inside a Compose screen.
    private fun find(text: String): AccessibilityNodeInfo? =
        first(root()) {
            it.text?.contains(text, ignoreCase = true) == true ||
                it.contentDescription?.contains(text, ignoreCase = true) == true
        }

    private fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        first(node) { it.isEditable }

    private fun first(
        node: AccessibilityNodeInfo?,
        matches: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (matches(node)) return node
        for (index in 0 until node.childCount) {
            first(node.getChild(index), matches)?.let {
                return it
            }
        }
        return null
    }

    /** The whole screen, system bars included, [width] pixels wide. */
    fun screenshot(width: Int): Bitmap {
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot" }
        // A screenshot lives in graphics memory, where it can be neither compared nor scaled.
        val pixels = screen.copy(Bitmap.Config.ARGB_8888, false)
        val height = pixels.height * width / pixels.width
        return Bitmap.createScaledBitmap(pixels, width, height, true)
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val LANGUAGE_TIMEOUT_MS = 10_000L
        const val WAIT_MS = 20_000L
        const val POLL_MS = 150L

        /** Past any device's long-press timeout. */
        const val LONG_PRESS_MS = 900L
        const val DRAG_STEPS = 8
        const val DRAG_STEP_MS = 30L
    }
}
