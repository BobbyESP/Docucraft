package com.composepdf.renderer

import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.composepdf.internal.service.pdf.PdfDocumentManager
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDocumentManagerContextTest {
    @Test
    fun storesApplicationContextForLongLivedWork() {
        val appContext =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val activityLikeContext =
            ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault)

        val manager = PdfDocumentManager(activityLikeContext)

        val contextField = PdfDocumentManager::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true

        assertSame(appContext, contextField.get(manager))

        manager.close()
    }
}

