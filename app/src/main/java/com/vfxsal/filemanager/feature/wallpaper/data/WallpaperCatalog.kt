package com.vfxsal.filemanager.feature.wallpaper.data

import androidx.compose.ui.graphics.Color

/** Fixed catalog of AMOLED designs, generated on-device - no image assets or network needed. */
object WallpaperCatalog {

    private val azure = Color(0xFF2979FF)
    private val violet = Color(0xFF9C4DFF)
    private val emerald = Color(0xFF00E676)
    private val amber = Color(0xFFFFAB00)
    private val red = Color(0xFFFF5252)
    private val orange = Color(0xFFFFAB40)
    private val skyBlue = Color(0xFF00B0FF)
    private val cyanBright = Color(0xFF00E5FF)
    private val pink = Color(0xFFFF4081)
    private val purple = Color(0xFF7C4DFF)
    private val cyan = Color(0xFF18FFFF)
    private val magenta = Color(0xFFE040FB)
    private val gold = Color(0xFFFFD740)
    private val teal = Color(0xFF1DE9B6)
    private val ice = Color(0xFF80D8FF)
    private val ember = Color(0xFFFF6E40)
    private val green = Color(0xFF00E676)
    private val blueGreen = Color(0xFF00B8D4)
    private val crimson = Color(0xFFFF1744)
    private val lime = Color(0xFFC6FF00)
    private val rose = Color(0xFFFF80AB)
    private val indigo = Color(0xFF536DFE)
    private val coral = Color(0xFFFF7043)
    private val mint = Color(0xFF64FFDA)
    private val skyLight = Color(0xFF40C4FF)

    // Alan Wake 2 palette: cold flashlight white/blue, the Dark Place's crimson, sodium
    // neon amber, and a foggy forest teal.
    private val flashlightWhite = Color(0xFFEAF2FF)
    private val iceBlue = Color(0xFF8FB7FF)
    private val darkPlaceRed = Color(0xFFE10E2A)
    private val bloodRed = Color(0xFFB00020)
    private val neonAmber = Color(0xFFFFB300)
    private val sodiumOrange = Color(0xFFFF7A1A)
    private val mistTeal = Color(0xFF3FD0C9)
    private val nightBlue = Color(0xFF4A6CC0)

    val designs: List<WallpaperDesign> = listOf(
        // Orb glow (single soft light source)
        WallpaperDesign("orb_azure", "Azure Glow", WallpaperStyle.ORB_GLOW, azure, seed = 1L),
        WallpaperDesign("orb_violet", "Violet Glow", WallpaperStyle.ORB_GLOW, violet, seed = 2L),
        WallpaperDesign("orb_emerald", "Emerald Glow", WallpaperStyle.ORB_GLOW, emerald, seed = 3L),
        WallpaperDesign("orb_amber", "Amber Glow", WallpaperStyle.ORB_GLOW, amber, seed = 4L),

        // Dual orb (two light sources)
        WallpaperDesign("dual_sunset", "Sunset Duo", WallpaperStyle.DUAL_ORB, red, orange, seed = 5L),
        WallpaperDesign("dual_ocean", "Ocean Duo", WallpaperStyle.DUAL_ORB, skyBlue, cyanBright, seed = 6L),
        WallpaperDesign("dual_neon", "Neon Duo", WallpaperStyle.DUAL_ORB, pink, purple, seed = 7L),

        // Line waves
        WallpaperDesign("wave_cyan", "Cyan Waves", WallpaperStyle.LINE_WAVE, cyan, seed = 8L),
        WallpaperDesign("wave_magenta", "Magenta Waves", WallpaperStyle.LINE_WAVE, magenta, seed = 9L),
        WallpaperDesign("wave_lime", "Lime Waves", WallpaperStyle.LINE_WAVE, lime, seed = 40L),

        // Rings
        WallpaperDesign("ring_gold", "Gold Ring", WallpaperStyle.RING, gold, seed = 10L),
        WallpaperDesign("ring_teal", "Teal Ring", WallpaperStyle.RING, teal, seed = 11L),
        WallpaperDesign("ring_rose", "Rose Ring", WallpaperStyle.RING, rose, seed = 41L),

        // Particle fields
        WallpaperDesign("particles_ice", "Ice Particles", WallpaperStyle.PARTICLES, ice, seed = 12L),
        WallpaperDesign("particles_ember", "Ember Particles", WallpaperStyle.PARTICLES, ember, seed = 13L),
        WallpaperDesign("particles_violet", "Violet Particles", WallpaperStyle.PARTICLES, violet, seed = 42L),

        // Aurora edge glow
        WallpaperDesign("aurora_green", "Aurora Green", WallpaperStyle.AURORA_EDGE, green, blueGreen, seed = 14L),
        WallpaperDesign("aurora_purple", "Aurora Purple", WallpaperStyle.AURORA_EDGE, purple, magenta, seed = 15L),
        WallpaperDesign("aurora_sunset", "Aurora Sunset", WallpaperStyle.AURORA_EDGE, red, amber, seed = 43L),

        // Triangle outline
        WallpaperDesign("triangle_red", "Crimson Edge", WallpaperStyle.TRIANGLE_OUTLINE, crimson, seed = 16L),
        WallpaperDesign("triangle_azure", "Azure Edge", WallpaperStyle.TRIANGLE_OUTLINE, azure, seed = 44L),
        WallpaperDesign("triangle_gold", "Gold Edge", WallpaperStyle.TRIANGLE_OUTLINE, gold, seed = 45L),

        // Constellation
        WallpaperDesign("constellation_ice", "Ice Constellation", WallpaperStyle.CONSTELLATION, ice, seed = 17L),
        WallpaperDesign("constellation_violet", "Violet Constellation", WallpaperStyle.CONSTELLATION, violet, seed = 18L),
        WallpaperDesign("constellation_emerald", "Emerald Constellation", WallpaperStyle.CONSTELLATION, emerald, seed = 19L),
        WallpaperDesign("constellation_rose", "Rose Constellation", WallpaperStyle.CONSTELLATION, rose, seed = 20L),

        // Hexagon rings
        WallpaperDesign("hex_gold", "Gold Hexagon", WallpaperStyle.HEXAGON_RINGS, gold, seed = 21L),
        WallpaperDesign("hex_teal", "Teal Hexagon", WallpaperStyle.HEXAGON_RINGS, teal, seed = 22L),
        WallpaperDesign("hex_indigo", "Indigo Hexagon", WallpaperStyle.HEXAGON_RINGS, indigo, seed = 23L),
        WallpaperDesign("hex_coral", "Coral Hexagon", WallpaperStyle.HEXAGON_RINGS, coral, seed = 24L),

        // Spiral
        WallpaperDesign("spiral_cyan", "Cyan Spiral", WallpaperStyle.SPIRAL, cyan, seed = 25L),
        WallpaperDesign("spiral_magenta", "Magenta Spiral", WallpaperStyle.SPIRAL, magenta, seed = 26L),
        WallpaperDesign("spiral_lime", "Lime Spiral", WallpaperStyle.SPIRAL, lime, seed = 27L),
        WallpaperDesign("spiral_amber", "Amber Spiral", WallpaperStyle.SPIRAL, amber, seed = 28L),

        // Ripple rings
        WallpaperDesign("ripple_azure", "Azure Ripple", WallpaperStyle.RIPPLE_RINGS, azure, seed = 29L),
        WallpaperDesign("ripple_violet", "Violet Ripple", WallpaperStyle.RIPPLE_RINGS, violet, seed = 30L),
        WallpaperDesign("ripple_mint", "Mint Ripple", WallpaperStyle.RIPPLE_RINGS, mint, seed = 31L),
        WallpaperDesign("ripple_coral", "Coral Ripple", WallpaperStyle.RIPPLE_RINGS, coral, seed = 32L),

        // Split gradient
        WallpaperDesign("split_sunset", "Sunset Split", WallpaperStyle.SPLIT_GRADIENT, red, orange, seed = 33L),
        WallpaperDesign("split_ocean", "Ocean Split", WallpaperStyle.SPLIT_GRADIENT, skyBlue, cyanBright, seed = 34L),
        WallpaperDesign("split_neon", "Neon Split", WallpaperStyle.SPLIT_GRADIENT, pink, purple, seed = 35L),
        WallpaperDesign("split_aurora", "Aurora Split", WallpaperStyle.SPLIT_GRADIENT, green, blueGreen, seed = 36L),

        // Orbit rings
        WallpaperDesign("orbit_gold", "Gold Orbit", WallpaperStyle.ORBIT_RINGS, gold, seed = 37L),
        WallpaperDesign("orbit_teal", "Teal Orbit", WallpaperStyle.ORBIT_RINGS, teal, seed = 38L),
        WallpaperDesign("orbit_azure", "Azure Orbit", WallpaperStyle.ORBIT_RINGS, azure, seed = 39L),
        WallpaperDesign("orbit_violet", "Violet Orbit", WallpaperStyle.ORBIT_RINGS, violet, seed = 46L),

        // Mountain skyline
        WallpaperDesign("skyline_emerald", "Emerald Skyline", WallpaperStyle.MOUNTAIN_SKYLINE, emerald, seed = 47L),
        WallpaperDesign("skyline_azure", "Azure Skyline", WallpaperStyle.MOUNTAIN_SKYLINE, azure, seed = 48L),
        WallpaperDesign("skyline_amber", "Amber Skyline", WallpaperStyle.MOUNTAIN_SKYLINE, amber, seed = 49L),
        WallpaperDesign("skyline_rose", "Rose Skyline", WallpaperStyle.MOUNTAIN_SKYLINE, rose, seed = 50L),

        // Crosshair burst
        WallpaperDesign("burst_crimson", "Crimson Burst", WallpaperStyle.CROSSHAIR_BURST, crimson, seed = 51L),
        WallpaperDesign("burst_cyan", "Cyan Burst", WallpaperStyle.CROSSHAIR_BURST, cyan, seed = 52L),
        WallpaperDesign("burst_violet", "Violet Burst", WallpaperStyle.CROSSHAIR_BURST, violet, seed = 53L),
        WallpaperDesign("burst_lime", "Lime Burst", WallpaperStyle.CROSSHAIR_BURST, lime, seed = 54L),
        WallpaperDesign("burst_sky", "Sky Burst", WallpaperStyle.CROSSHAIR_BURST, skyLight, seed = 55L),

        // Nebula cloud
        WallpaperDesign("nebula_violet", "Violet Nebula", WallpaperStyle.NEBULA_CLOUD, violet, magenta, seed = 56L),
        WallpaperDesign("nebula_ocean", "Ocean Nebula", WallpaperStyle.NEBULA_CLOUD, skyBlue, cyanBright, seed = 57L),
        WallpaperDesign("nebula_ember", "Ember Nebula", WallpaperStyle.NEBULA_CLOUD, ember, crimson, seed = 58L),
        WallpaperDesign("nebula_emerald", "Emerald Nebula", WallpaperStyle.NEBULA_CLOUD, emerald, teal, seed = 59L),

        // Starfield drift
        WallpaperDesign("starfield_ice", "Ice Starfield", WallpaperStyle.STARFIELD_DRIFT, ice, seed = 60L),
        WallpaperDesign("starfield_gold", "Gold Starfield", WallpaperStyle.STARFIELD_DRIFT, gold, seed = 61L),
        WallpaperDesign("starfield_rose", "Rose Starfield", WallpaperStyle.STARFIELD_DRIFT, rose, seed = 62L),
        WallpaperDesign("starfield_mint", "Mint Starfield", WallpaperStyle.STARFIELD_DRIFT, mint, seed = 63L),

        // Circuit lines
        WallpaperDesign("circuit_lime", "Lime Circuit", WallpaperStyle.CIRCUIT_LINES, lime, seed = 64L),
        WallpaperDesign("circuit_cyan", "Cyan Circuit", WallpaperStyle.CIRCUIT_LINES, cyan, seed = 65L),
        WallpaperDesign("circuit_amber", "Amber Circuit", WallpaperStyle.CIRCUIT_LINES, amber, seed = 66L),
        WallpaperDesign("circuit_magenta", "Magenta Circuit", WallpaperStyle.CIRCUIT_LINES, magenta, seed = 67L),

        // Diagonal stripes
        WallpaperDesign("stripes_azure", "Azure Stripes", WallpaperStyle.DIAGONAL_STRIPES, azure, seed = 68L),
        WallpaperDesign("stripes_coral", "Coral Stripes", WallpaperStyle.DIAGONAL_STRIPES, coral, seed = 69L),
        WallpaperDesign("stripes_indigo", "Indigo Stripes", WallpaperStyle.DIAGONAL_STRIPES, indigo, seed = 70L),
        WallpaperDesign("stripes_lime", "Lime Stripes", WallpaperStyle.DIAGONAL_STRIPES, lime, seed = 71L),

        // Honeycomb
        WallpaperDesign("honeycomb_gold", "Gold Honeycomb", WallpaperStyle.HONEYCOMB, gold, seed = 72L),
        WallpaperDesign("honeycomb_teal", "Teal Honeycomb", WallpaperStyle.HONEYCOMB, teal, seed = 73L),
        WallpaperDesign("honeycomb_violet", "Violet Honeycomb", WallpaperStyle.HONEYCOMB, violet, seed = 74L),
        WallpaperDesign("honeycomb_rose", "Rose Honeycomb", WallpaperStyle.HONEYCOMB, rose, seed = 75L),

        // Meteor shower
        WallpaperDesign("meteor_azure", "Azure Meteors", WallpaperStyle.METEOR_SHOWER, azure, seed = 76L),
        WallpaperDesign("meteor_ember", "Ember Meteors", WallpaperStyle.METEOR_SHOWER, ember, seed = 77L),
        WallpaperDesign("meteor_emerald", "Emerald Meteors", WallpaperStyle.METEOR_SHOWER, emerald, seed = 78L),
        WallpaperDesign("meteor_magenta", "Magenta Meteors", WallpaperStyle.METEOR_SHOWER, magenta, seed = 79L),

        // --- Alan Wake 2 inspired ---------------------------------------------------------
        // Flashlight beam cutting the dark
        WallpaperDesign("aw_beam_torch", "The Torch", WallpaperStyle.LIGHT_BEAM, flashlightWhite, seed = 80L),
        WallpaperDesign("aw_beam_cold", "Cold Beam", WallpaperStyle.LIGHT_BEAM, iceBlue, seed = 81L),
        // Misty pine forest (Cauldron Lake / Bright Falls)
        WallpaperDesign("aw_forest_cauldron", "Cauldron Lake", WallpaperStyle.PINE_FOREST, mistTeal, seed = 82L),
        WallpaperDesign("aw_forest_dawn", "Bright Falls Dawn", WallpaperStyle.PINE_FOREST, sodiumOrange, seed = 83L),
        WallpaperDesign("aw_forest_night", "Night Woods", WallpaperStyle.PINE_FOREST, nightBlue, seed = 84L),
        // Wet-street neon signage (Oceanview Hotel)
        WallpaperDesign("aw_neon_oceanview", "Oceanview Neon", WallpaperStyle.NEON_SIGN, neonAmber, seed = 85L),
        WallpaperDesign("aw_neon_vacancy", "No Vacancy", WallpaperStyle.NEON_SIGN, darkPlaceRed, seed = 86L),
        // The Dark Place descent spiral
        WallpaperDesign("aw_vortex_dark", "The Dark Place", WallpaperStyle.DARK_VORTEX, darkPlaceRed, seed = 87L),
        WallpaperDesign("aw_vortex_blood", "Spiral Descent", WallpaperStyle.DARK_VORTEX, bloodRed, seed = 88L),
    )
}
