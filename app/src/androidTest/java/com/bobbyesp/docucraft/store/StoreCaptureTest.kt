/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.R as AppR
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentSearch
import com.bobbyesp.docucraft.feature.docscanner.navigation.FolderContents
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.settings.ViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfDocumentDetails
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import com.bobbyesp.docucraft.test.R
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.core.Koin

/**
 * Takes the eight screens the store's screenshots are built around, in one language: what
 * `scripts/store/render.mjs` puts inside the phones of the panorama. Not a test of anything: it
 * runs as one because that is what can put the app's screens on a device, and it only runs when
 * asked to (`scripts/store/capture.mjs`).
 *
 * Each screen is the app's own, reached with the back stack a user would have under it, over the
 * sample library ([SampleLibrary]), in the brand's colors ([StoreCaptureTheme]) and under a status
 * bar that always says the same ([CaptureDevice.cleanStatusBar]). The exception is the scanner,
 * which is not the app's to show ([ScannerStandIn]).
 *
 * The app runs in real time and is read and touched through the system, as a person would
 * ([CaptureDevice]), rather than with Compose's test rule and its clock.
 *
 * The frames are numbered as the panorama numbers them, and are left where the build collects what
 * a test writes: `store-captures/<language>/<frame>.png` in the additional test output.
 */
@RunWith(Parameterized::class)
// The text of a page, which selecting and searching need, is the platform's to read from here on.
@SdkSuppress(minSdkVersion = 35)
class StoreCaptureTest(private val language: String) {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = CaptureDevice(instrumentation)
    private val locale: Locale = Locale.forLanguageTag(language)

    /**
     * The app's strings in the screenshot's language, to know what to wait for on screen. The app's
     * own resources, read once the device has put the app in that language: asked for in another
     * language before that, they still answer in the one the app was in.
     */
    private val appStrings by lazy { context.applicationContext.resources }

    private var screen by mutableStateOf<Screen?>(null)

    @Test
    fun captureTheStoreScreens() {
        assumeTrue("Only when the store's screenshots are asked for", requested)

        device.setAppLanguage(language)
        // What the app formats itself, such as a date, follows the default language rather than
        // the resources'.
        Locale.setDefault(locale)
        device.keepAwake()
        device.cleanStatusBar()

        val koin = startCaptureGraph(context)
        val library = runBlocking {
            SampleLibrary.create(
                koin,
                context,
                instrumentation.context.inLanguage().resources,
                language,
            )
        }
        try {
            // The scenario only starts the activity. Everything it offers afterwards first waits
            // for the main thread to have nothing to do, and with a sheet over a document the
            // screen is drawn again on every frame: the wait does not end. So the activity is
            // kept, spoken to on the main thread directly, and finished by hand.
            lateinit var activity: ComponentActivity
            ActivityScenario.launch(ComponentActivity::class.java).onActivity { activity = it }
            try {
                onMainThread {
                    activity.setContent {
                        screen?.let { current ->
                            // Each screen starts from nothing: no back stack, scroll or selection
                            // of the one before it.
                            key(current.frame) {
                                StoreCaptureTheme(
                                    koin,
                                    dark = current.dark,
                                    content = current.content,
                                )
                            }
                        }
                    }
                }
                Frames(activity, koin, library).captureAll()
            } finally {
                onMainThread {
                    screen = null
                    activity.finish()
                }
                Thread.sleep(ACTIVITY_CLOSE_MS)
            }
        } finally {
            runBlocking { library.remove(koin) }
            koin.closeCaptureGraph(context)
        }
    }

    private fun onMainThread(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun Context.inLanguage(): Context =
        createConfigurationContext(
            Configuration(resources.configuration).apply { setLocale(locale) }
        )

    private class Screen(val frame: String, val dark: Boolean, val content: @Composable () -> Unit)

    /** The eight frames, in the order of the panorama. */
    private inner class Frames(
        private val activity: ComponentActivity,
        private val koin: Koin,
        private val library: SampleLibrary,
    ) {
        private val sample = instrumentation.context.inLanguage().resources
        private val contract = ViewerDocumentRef.Catalogued(library.contract)

        fun captureAll() {
            // 01 · Home.
            show("01", Home)
            device.waitFor(sample.getString(R.string.sample_contract_title))
            capture("01")

            // 02 · The scanner.
            val page =
                firstPageOf(
                    context,
                    File(context.filesDir, "documents/${library.receipt}.pdf"),
                    1000,
                )
            show("02", dark = true) { ScannerStandIn(page) }
            capture("02")

            // 03 · A document in the viewer.
            show("03", Home, PdfViewer(library.contract))
            waitForViewer()
            capture("03")

            // 04 · Search, by what the pages say.
            show("04", Home, DocumentSearch)
            device.type(library.searchQuery)
            device.waitFor(sample.getString(R.string.sample_washing_title))
            hideKeyboard()
            capture("04")

            // 05 · A folder.
            show("05", Home, FolderContents(library.billsFolder))
            device.waitFor(sample.getString(R.string.sample_gas_title))
            capture("05")

            // 06 · The viewer at night, with text selected.
            koin
                .get<ViewerSessionSettings>()
                .set(
                    ViewerDocumentRef.Catalogued(library.notes),
                    ViewerDisplaySettings.Factory.copy(nightMode = true),
                )
            show("06", dark = true, Home, PdfViewer(library.notes))
            waitForViewer()
            settle()
            selectSomeText()
            capture("06")

            // 07 · A link's preview.
            show("07", Home, PdfViewer(library.bankLetter))
            device.waitFor(SampleLibrary.BANK_HOST)
            settle()
            device.tap(SampleLibrary.BANK_HOST)
            device.waitFor(appStrings.getString(AppR.string.link_open))
            capture("07")

            // 08 · The details of a document.
            show("08", Home, PdfViewer(library.contract), PdfDocumentDetails(contract))
            device.waitFor(appStrings.getString(AppR.string.document_details))
            capture("08")
        }

        private fun show(frame: String, vararg stack: NavKey) = show(frame, dark = false, *stack)

        private fun show(frame: String, dark: Boolean, vararg stack: NavKey) {
            val keys = stack.toList()
            show(frame, dark) { StoreCaptureApp(keys) }
        }

        private fun show(frame: String, dark: Boolean, content: @Composable () -> Unit) {
            onMainThread {
                // The icons of the system bars are dark over Paper and light over Ink.
                val bars =
                    if (dark) SystemBarStyle.dark(TRANSPARENT)
                    else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                screen = Screen(frame, dark, content)
            }
        }

        /** The document is open once its bars are: they are only drawn for a document that is. */
        private fun waitForViewer() = device.waitFor(appStrings.getString(AppR.string.night_mode))

        /**
         * Presses on a line of the page and drags down and across, as a reader selecting a few
         * lines does. Where exactly a line falls depends on the screen, and a press between two
         * lines takes nothing, so it is tried a little lower each time, by less than a line is
         * tall, until some text is taken.
         */
        private fun selectSomeText() {
            val handle = appStrings.getString(AppR.string.viewer_selection_end_handle)
            val size = device.screenSize()
            var start = 0.36f
            while (start < 0.5f) {
                device.longPressAndDrag(
                    from = PointF(size.x * 0.22f, size.y * start),
                    to = PointF(size.x * 0.66f, size.y * (start + 0.075f)),
                )
                Thread.sleep(SETTLE_STEP_MS)
                if (device.shows(handle)) return
                start += 0.004f
            }
            error("No text could be selected in the viewer")
        }

        private fun hideKeyboard() {
            onMainThread {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
                activity.currentFocus?.clearFocus()
            }
        }

        private fun capture(frame: String) {
            device.save(settle(), File(outputDirectory, "$language/$frame.png"))
        }

        /**
         * The screen once it has stopped changing: previews and pages are drawn as they arrive, off
         * the main thread, and nothing says when the last one has. Two pictures alike in a row are
         * taken for the end; a screen that never rests, such as one with a blinking cursor, is
         * taken as it is after a while.
         */
        private fun settle(): Bitmap {
            var previous: Bitmap? = null
            repeat(SETTLE_TRIES) {
                Thread.sleep(SETTLE_STEP_MS)
                val current = device.screenshot(CAPTURE_WIDTH)
                if (previous?.sameAs(current) == true) return current
                previous = current
            }
            return checkNotNull(previous)
        }
    }

    companion object {
        private const val TRANSPARENT = android.graphics.Color.TRANSPARENT

        /** As wide as a frame of the panorama: the phone drawn in it is narrower still. */
        private const val CAPTURE_WIDTH = 1080

        private const val SETTLE_STEP_MS = 500L
        private const val SETTLE_TRIES = 16

        /** Long enough for the activity to be gone before its catalogue is closed under it. */
        private const val ACTIVITY_CLOSE_MS = 1_500L

        private val arguments
            get() = InstrumentationRegistry.getArguments()

        /** Off unless asked for: an ordinary run of the device tests takes no screenshots. */
        private val requested: Boolean
            get() = arguments.getString("storeCaptures") == "true"

        /** Where the build collects what a test writes, or the app's own files without a build. */
        private val outputDirectory: File
            get() {
                val collected = arguments.getString("additionalTestOutputDir")
                val base =
                    collected?.let(::File)
                        ?: InstrumentationRegistry.getInstrumentation()
                            .targetContext
                            .getExternalFilesDir(null)
                return File(base, "store-captures")
            }

        /**
         * The languages asked for, as `storeLocales=en+es`. English alone when none is. Joined by a
         * plus sign: a comma does not survive the way from the build to the device.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages(): List<String> =
            arguments
                .getString("storeLocales")
                .orEmpty()
                .split('+')
                .filter { it.isNotBlank() }
                .ifEmpty {
                    listOf("en")
                }

        /** The device is left as it was found: its own status bar, and the app in its language. */
        @JvmStatic
        @AfterClass
        fun restoreTheDevice() {
            if (!requested) return
            val device = CaptureDevice(InstrumentationRegistry.getInstrumentation())
            device.restoreStatusBar()
            device.setAppLanguage("")
        }
    }
}
