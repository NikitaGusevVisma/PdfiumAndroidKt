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
import java.util.concurrent.atomic.AtomicReference

/**
 * Reproduces the document-close vs compat-render race that consumers such as
 * AndroidPdfViewer hit in production: a rendering thread drives the deprecated
 * PdfiumCore compat chain (openPage -> renderPageBitmap -> close) while another
 * thread closes the document.
 *
 * Before the fix, the compat methods took PdfiumCore.lock in three separate
 * synchronized scopes, so a close could interleave between them: the next
 * openPage() then hit its raw check(!isClosed) and threw IllegalStateException
 * even under AlreadyClosedBehavior.IGNORE (or, worse, a page handle outlived
 * its document and the native render dereferenced freed memory).
 *
 * With the fix the whole chain holds the lock once and honors the configured
 * behavior, so under IGNORE this hammer must complete with zero throwables.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumCoreCloseRaceTest : BasePDFTest() {
    @After
    fun restoreDefaultConfig() {
        // pdfiumConfig is process-global state — reset it so other test classes
        // keep the default EXCEPTION behavior.
        PdfiumCore(config = Config())
    }

    @Test
    fun concurrentCloseDuringCompatRenderNeverThrowsUnderIgnore() {
        val core = PdfiumCore(config = Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
        val pdfBytes = getPdfBytes("f01.pdf")
        assertThat(pdfBytes).isNotNull()

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val firstFailure = AtomicReference<Throwable?>(null)

        repeat(ITERATIONS) { iteration ->
            val document = core.newDocument(pdfBytes)
            val renderer =
                Thread {
                    try {
                        repeat(RENDERS_PER_ITERATION) {
                            core.renderPageBitmap(document, bitmap, 0, 0, 0, 200, 200)
                            core.getPageSize(document, 0)
                        }
                    } catch (t: Throwable) {
                        firstFailure.compareAndSet(null, t)
                    }
                }
            renderer.start()
            // Sweep the close point across the render chain instead of using a
            // fixed delay, so closes land before, between, and after the
            // individual lock scopes over the course of the run.
            Thread.sleep(iteration % CLOSE_DELAY_SWEEP_MS)
            document.close()
            renderer.join()

            firstFailure.get()?.let { failure ->
                throw AssertionError(
                    "Close race escaped AlreadyClosedBehavior.IGNORE on iteration $iteration",
                    failure,
                )
            }
        }
    }

    companion object {
        private const val ITERATIONS = 400
        private const val RENDERS_PER_ITERATION = 10
        private const val CLOSE_DELAY_SWEEP_MS = 5L
    }
}
