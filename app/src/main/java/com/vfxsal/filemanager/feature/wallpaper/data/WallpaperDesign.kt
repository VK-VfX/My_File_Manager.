package com.vfxsal.filemanager.feature.wallpaper.data

import androidx.compose.ui.graphics.Color

enum class WallpaperStyle {
    ORB_GLOW, DUAL_ORB, LINE_WAVE, RING, PARTICLES, AURORA_EDGE, TRIANGLE_OUTLINE,
    CONSTELLATION, HEXAGON_RINGS, SPIRAL, RIPPLE_RINGS, SPLIT_GRADIENT,
    ORBIT_RINGS, MOUNTAIN_SKYLINE, CROSSHAIR_BURST,
    NEBULA_CLOUD, STARFIELD_DRIFT, CIRCUIT_LINES, DIAGONAL_STRIPES, HONEYCOMB, METEOR_SHOWER,
}

/** A procedural wallpaper recipe - [WallpaperRenderer] turns this into a true-black bitmap at any resolution. */
data class WallpaperDesign(
    val id: String,
    val name: String,
    val style: WallpaperStyle,
    val primary: Color,
    val secondary: Color = primary,
    val seed: Long,
)
