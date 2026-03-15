package com.github.barteksc.pdfviewer.bridge

import android.graphics.RectF
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfCoordinateMapperTest {

    @Test
    fun pageRectRoundTripsBetweenPageAndViewSpace() {
        val pageBounds = RectF(20f, 40f, 220f, 440f)
        val pageRect = NormalizedRect(0.1f, 0.2f, 0.4f, 0.6f)

        val viewRect = PdfCoordinateMapper.pageRectToView(pageBounds, pageRect)
        val roundTrip = PdfCoordinateMapper.viewRectToPage(pageBounds, viewRect)

        assertThat(roundTrip.left).isWithin(0.0001f).of(pageRect.left)
        assertThat(roundTrip.top).isWithin(0.0001f).of(pageRect.top)
        assertThat(roundTrip.right).isWithin(0.0001f).of(pageRect.right)
        assertThat(roundTrip.bottom).isWithin(0.0001f).of(pageRect.bottom)
    }

    @Test
    fun pointRoundTripPreservesLegacyViewerCoordinateBehavior() {
        val pageBounds = RectF(18f, 24f, 318f, 624f)
        val pagePointX = 0.42f
        val pagePointY = 0.73f

        val viewPoint = PdfCoordinateMapper.pagePointToView(pageBounds, pagePointX, pagePointY)
        val roundTrip = PdfCoordinateMapper.viewPointToPage(pageBounds, viewPoint.x, viewPoint.y)

        assertThat(roundTrip.first).isWithin(0.0001f).of(pagePointX)
        assertThat(roundTrip.second).isWithin(0.0001f).of(pagePointY)
    }

    @Test
    fun viewRectToPageClampsValuesIntoNormalizedPageSpace() {
        val pageBounds = RectF(20f, 40f, 220f, 440f)
        val offscreenRect = RectF(-100f, -50f, 500f, 800f)

        val mapped = PdfCoordinateMapper.viewRectToPage(pageBounds, offscreenRect)

        assertThat(mapped.left).isAtLeast(0f)
        assertThat(mapped.top).isAtLeast(0f)
        assertThat(mapped.right).isAtMost(1f)
        assertThat(mapped.bottom).isAtMost(1f)
    }
}
