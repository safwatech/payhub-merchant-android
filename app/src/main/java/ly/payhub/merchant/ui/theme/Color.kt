package ly.payhub.merchant.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PayHub brand seed — the favicon mark's warm amber.
val PayhubAmber = Color(0xFFFAB64B)
val PayhubAmberDark = Color(0xFFC98B2E)

// A hand-tuned Material 3 palette anchored on the amber seed. (Generating these
// from Material's HCT tonal-palette algorithm at build time would be cleaner,
// but a static scheme keeps the dependency graph small and the colours stable.)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF8A5300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF2C1700),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADEBC),
    onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF52643F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5EABB),
    onTertiaryContainer = Color(0xFF111F03),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF0E0CF),
    onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B4),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF9EFE7),
    inversePrimary = Color(0xFFFFB951),
    surfaceTint = Color(0xFF8A5300),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF492900),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFDDC2A1),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56432B),
    onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFB9CEA1),
    onTertiary = Color(0xFF253515),
    tertiaryContainer = Color(0xFF3B4C2A),
    onTertiaryContainer = Color(0xFFD5EABB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF4F4539),
    inverseSurface = Color(0xFFEAE1D9),
    inverseOnSurface = Color(0xFF34302A),
    inversePrimary = Color(0xFF8A5300),
    surfaceTint = Color(0xFFFFB951),
)

// Semantic status colours for pay-link / payment pills — kept theme-agnostic
// (legible on both light & dark surfaces) and softened with a *Container variant.
object StatusColors {
    val Positive = Color(0xFF1B7A3D)        // succeeded / paid
    val PositiveContainer = Color(0xFFC8F0D2)
    val PositiveOnContainer = Color(0xFF062E12)
    val Pending = Color(0xFFB07400)         // active / in-flight / pending
    val PendingContainer = Color(0xFFFFE6B3)
    val PendingOnContainer = Color(0xFF2C1E00)
    val Neutral = Color(0xFF5C5C5C)         // expired / unknown
    val NeutralContainer = Color(0xFFE4E4E4)
    val NeutralOnContainer = Color(0xFF1B1B1B)
    val Negative = Color(0xFFB3261E)        // cancelled / failed
    val NegativeContainer = Color(0xFFFADAD7)
    val NegativeOnContainer = Color(0xFF410E0B)
}
