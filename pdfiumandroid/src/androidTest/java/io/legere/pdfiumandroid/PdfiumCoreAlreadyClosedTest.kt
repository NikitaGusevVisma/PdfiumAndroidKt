package io.legere.pdfiumandroid

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.legere.pdfiumandroid.base.BasePDFTest
import io.legere.pdfiumandroid.util.AlreadyClosedBehavior
import io.legere.pdfiumandroid.util.Config
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The deprecated PdfiumCore compat methods chain openPage() -> render/measure ->
 * close(). openPage()'s raw check(!isClosed) used to throw IllegalStateException
 * even under AlreadyClosedBehavior.IGNORE, bypassing the configured behavior —
 * the dominant crash frame when a consumer (e.g. AndroidPdfViewer's rendering
 * thread) races a document close. These tests pin the IGNORE contract for the
 * compat entry points, and that EXCEPTION (the default) still throws.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumCoreAlreadyClosedTest : BasePDFTest() {
    @After
    fun restoreDefaultConfig() {
        // pdfiumConfig is process-global state — reset it so other test classes
        // keep the default EXCEPTION behavior.
        PdfiumCore(config = Config())
    }

    private fun closedDocument(core: PdfiumCore): PdfDocument {
        val pdfBytes = getPdfBytes("f01.pdf")
        assertThat(pdfBytes).isNotNull()
        val document = core.newDocument(pdfBytes)
        document.close()
        return document
    }

    @Test
    fun renderPageBitmapOnClosedDocumentIsIgnored() {
        val core = PdfiumCore(config = Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
        val document = closedDocument(core)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Must not throw — before the fix this hit openPage()'s raw check().
        core.renderPageBitmap(document, bitmap, 0, 0, 0, 100, 100)
    }

    @Test
    fun getPageSizeOnClosedDocumentIsIgnored() {
        val core = PdfiumCore(config = Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
        val document = closedDocument(core)

        val size = core.getPageSize(document, 0)

        assertThat(size.width).isEqualTo(0)
        assertThat(size.height).isEqualTo(0)
    }

    @Test
    fun getPageLinksOnClosedDocumentIsIgnored() {
        val core = PdfiumCore(config = Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
        val document = closedDocument(core)

        val links = core.getPageLinks(document, 0)

        assertThat(links).isEmpty()
    }

    @Test(expected = IllegalStateException::class)
    fun renderPageBitmapOnClosedDocumentStillThrowsByDefault() {
        val core = PdfiumCore(config = Config())
        val document = closedDocument(core)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        core.renderPageBitmap(document, bitmap, 0, 0, 0, 100, 100)
    }
}
