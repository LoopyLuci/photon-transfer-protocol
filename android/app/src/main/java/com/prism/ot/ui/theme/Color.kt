package com.prism.ot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PRISM palette.
 *
 * The palette is deliberately dark-first: the scanning screens sit
 * underneath a live camera preview, and a light theme washes out the
 * overlay controls. The accent colours are drawn from the same CIELAB
 * regions used by the Dense-64 alphabet, so the UI reads as part of the
 * code rather than decoration around it.
 */
object PrismColors {

    // Core surfaces
    val Background = Color(0xFF0B0E14)
    val Surface = Color(0xFF141922)
    val SurfaceElevated = Color(0xFF1C2430)
    val Outline = Color(0xFF2A3444)

    // Text
    val TextPrimary = Color(0xFFE8ECF2)
    val TextSecondary = Color(0xFF9AA6B8)
    val TextDisabled = Color(0xFF5A667A)

    // Accents derived from the Dense-64 CIELAB centres
    val Cyan = Color(0xFF3DD9C0)      // light green region
    val Violet = Color(0xFF8B6CF0)    // purple-blue region
    val Amber = Color(0xFFF0B429)     // warm accent for warnings
    val Magenta = Color(0xFFE85C8A)   // error / critical
    val Blue = Color(0xFF4C8DFF)      // informational

    // Semantic
    val Success = Color(0xFF4CD97B)
    val Warning = Color(0xFFF0B429)
    val Error = Color(0xFFE85C8A)

    // Scanner overlay
    val ScanLine = Color(0xCC3DD9C0)
    val ScanCorner = Color(0xFF3DD9C0)
    val DetectionBox = Color(0xAA8B6CF0)
    val DetectionBoxRejected = Color(0xAAE85C8A)
}
