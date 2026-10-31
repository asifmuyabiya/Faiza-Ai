package com.faiza.ai

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.faiza.ai.ui.orb.OrbDesign
import com.faiza.ai.ui.orb.OrbMode
import com.faiza.ai.ui.orb.OrbTheme
import com.faiza.ai.ui.orb.OrbView
import org.junit.Rule
import org.junit.Test

/** Draws every orb design in several states so the artwork can be reviewed without a phone. */
class OrbGalleryTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.FaizaAI", maxPercentDifference = 0.1)

    private fun gallery(mode: OrbMode, theme: OrbTheme): View {
        val ctx = paparazzi.context
        val density = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#07070C"))
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }
        val designs = OrbDesign.values().toList()
        designs.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { design ->
                val cell = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val orb = OrbView(ctx).apply {
                    this.design = design
                    this.theme = theme
                    this.mode = mode
                    setIconRes(R.drawable.ic_wave)
                }
                cell.addView(orb, LinearLayout.LayoutParams((150 * density).toInt(), (150 * density).toInt()))
                cell.addView(TextView(ctx).apply {
                    text = design.title
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    gravity = Gravity.CENTER
                })
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(row)
        }
        return root
    }

    @Test fun galleryIdle() = paparazzi.snapshot(gallery(OrbMode.IDLE, OrbTheme.VIOLET), "orbs_idle")

    @Test fun gallerySpeaking() = paparazzi.snapshot(gallery(OrbMode.SPEAKING, OrbTheme.CYAN), "orbs_speaking")
}
