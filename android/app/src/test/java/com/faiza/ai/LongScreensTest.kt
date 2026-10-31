package com.faiza.ai

import android.widget.ScrollView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** Long screens rendered one phone-screen at a time, at full sharpness, for design review. */
class LongScreensTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "Theme.FaizaAI",
        maxPercentDifference = 0.1
    )

    private fun pages(layout: Int, name: String, count: Int) {
        val view = paparazzi.inflate<ScrollView>(layout)
        val screen = (DeviceConfig.PIXEL_5.screenHeight * 0.85).toInt()
        for (i in 0 until count) {
            view.scrollTo(0, i * screen)
            paparazzi.snapshot(view, "${name}_$i")
        }
    }

    @Test fun settings() = pages(R.layout.activity_settings, "settings", 5)
    @Test fun secret() = pages(R.layout.activity_secret, "secret", 2)

    @Test fun dashboard() {
        val root = paparazzi.inflate<android.view.View>(R.layout.activity_dashboard)
        paparazzi.snapshot(root, "dashboard_top")
        root.findViewById<ScrollView>(R.id.dashScroll).scrollTo(0, 1500)
        paparazzi.snapshot(root, "dashboard_bottom")
    }

    @Test fun edgeGlow() {
        val root = android.widget.FrameLayout(paparazzi.context).apply { setBackgroundColor(0xFF15151C.toInt()) }
        val glow = com.faiza.ai.ui.orb.EdgeGlowView(paparazzi.context)
        root.addView(glow, android.widget.FrameLayout.LayoutParams(-1, -1))
        glow.setMode(com.faiza.ai.ui.orb.OrbMode.LISTENING)
        root.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 2340)
        val scratch = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888))
        repeat(60) { glow.draw(scratch) } // let the brightness fade in
        paparazzi.snapshot(root, "edge_glow")
    }
}
