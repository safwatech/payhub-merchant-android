package ly.payhub.merchant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 type scale with a slightly tighter, friendlier feel for the
// dense lists this app shows. Uses the platform default sans (no bundled font
// to keep the APK small); mono is used for order refs / tokens via [MonoStyle].
internal val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 22.sp),
    )
}

/** For monospaced bits — order references, short tokens, amounts in tables. */
val MonoStyle: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
