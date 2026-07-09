package com.lumilink.ui.theme

import androidx.compose.ui.graphics.Color

// Palette mirrors the approved wireframe: an amber "camera dial" accent over warm-biased
// neutrals. `internal` = visible only within this module (Kotlin's default-ish visibility for
// implementation detail; roughly Java package-private but module-wide).

internal val AmberDeep = Color(0xFF9E6518) // primary accent on light surfaces
internal val AmberBright = Color(0xFFE0A63E) // primary accent on dark surfaces
internal val OnAmberLight = Color(0xFFFFFFFF)
internal val OnAmberDark = Color(0xFF16130B)

internal val LightBackground = Color(0xFFF4F2ED)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF1F2124)
internal val LightSurfaceVariant = Color(0xFFE7E3DC)

internal val DarkBackground = Color(0xFF101215)
internal val DarkSurface = Color(0xFF1A1D21)
internal val DarkOnSurface = Color(0xFFE9E7E2)
internal val DarkSurfaceVariant = Color(0xFF2A2D32)

// Semantic status colors (separate from the accent): connected = good, error/record = critical.
internal val GoodGreen = Color(0xFF3C8A63)
internal val CriticalRed = Color(0xFFC0473B)
