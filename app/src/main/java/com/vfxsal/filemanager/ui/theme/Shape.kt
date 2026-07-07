package com.vfxsal.filemanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * v4.0 shape language: noticeably rounder than stock Material defaults. Because every
 * Card, Dialog, Sheet and Menu in the app resolves its shape from MaterialTheme, bumping
 * these five values restyles the entire app without touching individual screens.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
