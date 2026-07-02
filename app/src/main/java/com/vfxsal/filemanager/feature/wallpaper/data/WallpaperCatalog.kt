package com.vfxsal.filemanager.feature.wallpaper.data

import androidx.compose.ui.graphics.Color

/** Fixed catalog of AMOLED designs, generated on-device - no image assets or network needed. */
object WallpaperCatalog {
    val designs: List<WallpaperDesign> = listOf(
        WallpaperDesign("orb_azure", "Azure Glow", WallpaperStyle.ORB_GLOW, Color(0xFF2979FF), seed = 1L),
        WallpaperDesign("orb_violet", "Violet Glow", WallpaperStyle.ORB_GLOW, Color(0xFF9C4DFF), seed = 2L),
        WallpaperDesign("orb_emerald", "Emerald Glow", WallpaperStyle.ORB_GLOW, Color(0xFF00E676), seed = 3L),
        WallpaperDesign("orb_amber", "Amber Glow", WallpaperStyle.ORB_GLOW, Color(0xFFFFAB00), seed = 4L),
        WallpaperDesign("dual_sunset", "Sunset Duo", WallpaperStyle.DUAL_ORB, Color(0xFFFF5252), Color(0xFFFFAB40), seed = 5L),
        WallpaperDesign("dual_ocean", "Ocean Duo", WallpaperStyle.DUAL_ORB, Color(0xFF00B0FF), Color(0xFF00E5FF), seed = 6L),
        WallpaperDesign("dual_neon", "Neon Duo", WallpaperStyle.DUAL_ORB, Color(0xFFFF4081), Color(0xFF7C4DFF), seed = 7L),
        WallpaperDesign("wave_cyan", "Cyan Waves", WallpaperStyle.LINE_WAVE, Color(0xFF18FFFF), seed = 8L),
        WallpaperDesign("wave_magenta", "Magenta Waves", WallpaperStyle.LINE_WAVE, Color(0xFFE040FB), seed = 9L),
        WallpaperDesign("ring_gold", "Gold Ring", WallpaperStyle.RING, Color(0xFFFFD740), seed = 10L),
        WallpaperDesign("ring_teal", "Teal Ring", WallpaperStyle.RING, Color(0xFF1DE9B6), seed = 11L),
        WallpaperDesign("particles_ice", "Ice Particles", WallpaperStyle.PARTICLES, Color(0xFF80D8FF), seed = 12L),
        WallpaperDesign("particles_ember", "Ember Particles", WallpaperStyle.PARTICLES, Color(0xFFFF6E40), seed = 13L),
        WallpaperDesign("aurora_green", "Aurora Green", WallpaperStyle.AURORA_EDGE, Color(0xFF00E676), Color(0xFF00B8D4), seed = 14L),
        WallpaperDesign("aurora_purple", "Aurora Purple", WallpaperStyle.AURORA_EDGE, Color(0xFF7C4DFF), Color(0xFFE040FB), seed = 15L),
        WallpaperDesign("triangle_red", "Crimson Edge", WallpaperStyle.TRIANGLE_OUTLINE, Color(0xFFFF1744), seed = 16L),
    )
}
