package com.faiza.ai

import android.view.View
import app.cash.paparazzi.DeviceConfig
import com.android.ide.common.rendering.api.SessionParams
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** Renders each screen to an image (no phone needed) so the design can be reviewed. */
class ScreensTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "Theme.FaizaAI",
        maxPercentDifference = 0.1,
        // Render the whole scrolling page, not just the first screen.
        renderingMode = SessionParams.RenderingMode.V_SCROLL
    )

    private fun shot(layout: Int, name: String) {
        val view = paparazzi.inflate<View>(layout)
        paparazzi.snapshot(view, name)
    }

    @Test fun splash() = shot(R.layout.activity_main, "splash")
    @Test fun license() = shot(R.layout.activity_license, "license")
    // Dashboard (a DrawerLayout) is rendered by LongScreensTest: V_SCROLL can't measure drawers.
    @Test fun settings() = shot(R.layout.activity_settings, "settings")
    @Test fun skills() = shot(R.layout.activity_skills, "skills")
    @Test fun apiSetup() = shot(R.layout.activity_api_setup, "api_setup")
    @Test fun orb() = shot(R.layout.floating_orb, "orb")
    @Test fun memory() = shot(R.layout.activity_memory, "memory")
    @Test fun voiceGuardian() = shot(R.layout.activity_voice_guardian, "voice_guardian")
    @Test fun sos() = shot(R.layout.activity_sos, "sos")
    @Test fun contacts() = shot(R.layout.activity_contacts, "contacts")
    @Test fun orbSettings() = shot(R.layout.activity_orb_settings, "orb_settings")
    @Test fun macros() = shot(R.layout.activity_macros, "macros")
    @Test fun chat() = shot(R.layout.activity_chat, "chat")
    @Test fun scan() = shot(R.layout.activity_scan, "scan")
    @Test fun imageViewer() = shot(R.layout.activity_image_viewer, "image_viewer")
    @Test fun routines() = shot(R.layout.activity_routines, "routines")
    @Test fun activityLog() = shot(R.layout.activity_activity_log, "activity_log")
}
