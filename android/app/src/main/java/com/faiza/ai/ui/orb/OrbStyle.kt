package com.faiza.ai.ui.orb

import android.graphics.Color

/** The orb designs the user can pick (video 25:56). The first two are free, the rest are Pro. */
enum class OrbDesign(val id: Int, val title: String, val subtitle: String, val isPro: Boolean) {
    SPHERE(0, "Sphere", "Glossy glowing ball", false),
    PULSE(1, "Pulse reactor", "Pulsing rings", false),
    LIQUID(2, "Liquid core", "Soft morphing blob", true),
    RINGS(3, "Rings", "Orbiting halo", true),
    PARTICLES(4, "Particle swarm", "Swirling sparks", true),
    WAVEFORM(5, "Waveform", "Voice bars", true),
    HEXAGON(6, "Future hex", "Sci-fi hexagon", true),
    MINIMAL(7, "Minimal dot", "Small & quiet", true);

    companion object {
        fun from(id: Int): OrbDesign = values().firstOrNull { it.id == id } ?: SPHERE
    }
}

/** Colour themes (video 26:32). */
enum class OrbTheme(val id: Int, val title: String, val main: Int, val light: Int, val dark: Int) {
    VIOLET(0, "Violet", Color.parseColor("#8B5CF6"), Color.parseColor("#E9DFFF"), Color.parseColor("#3B1782")),
    BLUE(1, "Blue", Color.parseColor("#3B82F6"), Color.parseColor("#DBEAFE"), Color.parseColor("#1E3A8A")),
    CYAN(2, "Cyan", Color.parseColor("#22D3EE"), Color.parseColor("#CFFAFE"), Color.parseColor("#155E75")),
    GREEN(3, "Matrix green", Color.parseColor("#22C55E"), Color.parseColor("#DCFCE7"), Color.parseColor("#14532D")),
    GOLD(4, "Gold", Color.parseColor("#F59E0B"), Color.parseColor("#FEF3C7"), Color.parseColor("#78350F")),
    RED(5, "Iron red", Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2"), Color.parseColor("#7F1D1D")),
    PINK(6, "Pink", Color.parseColor("#EC4899"), Color.parseColor("#FCE7F3"), Color.parseColor("#831843")),
    WHITE(7, "Ghost white", Color.parseColor("#E5E7EB"), Color.parseColor("#FFFFFF"), Color.parseColor("#4B5563"));

    companion object {
        fun from(id: Int): OrbTheme = values().firstOrNull { it.id == id } ?: VIOLET
    }
}

/** What the orb is doing — drives its animation. */
enum class OrbMode { IDLE, CONNECTING, LISTENING, SPEAKING, WORKING, MIC_BUSY }
