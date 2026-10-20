package ir.mobileshop.inventory

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/** Persian digits and thousands separator, like the shopping apps people are used to. */
fun String.fa(): String = map {
    when (it) {
        in '0'..'9' -> '۰' + (it - '0')
        ',' -> '٬'
        else -> it
    }
}.joinToString("")

fun Long.faNum(): String = NumberFormat.getInstance(Locale.US).format(this).fa()
fun Int.faNum(): String = toLong().faNum()
fun Long.faMoney(): String = faNum() + " تومان"

/** Short form for small tiles: «۱۲٫۵ میلیون». */
fun Long.faCompact(): String {
    fun trim(d: Double) = String.format(Locale.US, "%.1f", d).removeSuffix(".0").fa().replace('.', '٫')
    return when {
        this >= 1_000_000_000L -> trim(this / 1e9) + " میلیارد"
        this >= 1_000_000L -> trim(this / 1e6) + " میلیون"
        else -> faNum()
    }
}

/** Bold amount with a small grey «تومان» next to it. */
@Composable
fun Price(
    amount: Long,
    size: TextUnit = 17.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size, color = color)) {
                append(amount.faNum())
            }
            withStyle(SpanStyle(fontSize = 11.sp, color = InkGrey)) { append(" تومان") }
        }
    )
}
