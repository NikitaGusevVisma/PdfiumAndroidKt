package io.legere.pdfiumandroid

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.legere.pdfiumandroid.base.BasePDFTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Regression test for a pdfium CHECK in RgbByteOrderTransferBitmap (Feb-2024 binaries).
// A filled path with a semi-transparent stroke is drawn through
// CFX_RenderDevice::DrawFillStrokePath, which reads the backdrop with GetDIBits using the
// path's unclipped bbox. When that bbox starts left of the bitmap, the RGB_565 path
// (BGR + FPDF_REVERSE_BYTE_ORDER) ran past the end of the buffer and crashed with SIGTRAP.
// The fixture is a 100x100 pt page with a rectangle at x=-10..40, y=10..50, stroke alpha 0.5.
@RunWith(AndroidJUnit4::class)
class PdfPageFillStrokeEdgeTest : BasePDFTest() {
    private lateinit var pdfDocument: PdfDocument

    @Before
    fun setUp() {
        val pdfBytes = getPdfBytes("fill-stroke-left-edge.pdf")

        assertThat(pdfBytes).isNotNull()

        pdfDocument = PdfiumCore().newDocument(pdfBytes)
    }

    @After
    fun tearDown() {
        pdfDocument.close()
    }

    @Test
    fun renderPageBitmap_rgb565_fillStrokePathCrossingLeftEdge() {
        val bitmap = Bitmap.createBitmap(PAGE_SIZE, PAGE_SIZE, Bitmap.Config.RGB_565)

        pdfDocument.openPage(0).use { page ->
            page.renderPageBitmap(
                bitmap,
                0,
                0,
                PAGE_SIZE,
                PAGE_SIZE,
                pageBackgroundColor = Color.WHITE,
            )
        }

        assertThat(bitmap.getPixel(INSIDE_RECT_X, RECT_CENTER_Y)).isNotEqualTo(Color.WHITE)
        assertThat(bitmap.getPixel(OUTSIDE_RECT_X, RECT_CENTER_Y)).isEqualTo(Color.WHITE)
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val INSIDE_RECT_X = 15
        private const val OUTSIDE_RECT_X = 70
        private const val RECT_CENTER_Y = 70
    }
}
