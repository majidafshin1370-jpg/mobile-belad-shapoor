package ir.mobileshop.inventory

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Clean "marketplace" palette: light grey page, white cards, one strong red accent.
val Red = Color(0xFFEF394E)
val RedSoft = Color(0xFFFDECEE)
val PageGrey = Color(0xFFF1F2F4)
val CardBorder = Color(0xFFE6E8EB)
val InkDark = Color(0xFF23254E)
val InkGrey = Color(0xFF81858B)
val GoodGreen = Color(0xFF00A049)
val GoodGreenSoft = Color(0xFFE5F6EC)
val HeroDark = Color(0xFF151824)

private val scheme = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = PageGrey,
    onBackground = InkDark,
    surface = Color.White,
    onSurface = InkDark,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = InkGrey,
    outline = CardBorder,
    error = Red
)

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private fun typography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Vazir),
        displayMedium = b.displayMedium.copy(fontFamily = Vazir),
        displaySmall = b.displaySmall.copy(fontFamily = Vazir),
        headlineLarge = b.headlineLarge.copy(fontFamily = Vazir),
        headlineMedium = b.headlineMedium.copy(fontFamily = Vazir),
        headlineSmall = b.headlineSmall.copy(fontFamily = Vazir),
        titleLarge = b.titleLarge.copy(fontFamily = Vazir),
        titleMedium = b.titleMedium.copy(fontFamily = Vazir),
        titleSmall = b.titleSmall.copy(fontFamily = Vazir),
        bodyLarge = b.bodyLarge.copy(fontFamily = Vazir),
        bodyMedium = b.bodyMedium.copy(fontFamily = Vazir),
        bodySmall = b.bodySmall.copy(fontFamily = Vazir),
        labelLarge = b.labelLarge.copy(fontFamily = Vazir),
        labelMedium = b.labelMedium.copy(fontFamily = Vazir),
        labelSmall = b.labelSmall.copy(fontFamily = Vazir)
    )
}

private val shapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)

@Composable
fun ShopTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = typography(), shapes = shapes, content = content)
