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

/** Lets the seller type Persian/Arabic digits too. */
fun String.latinDigits(): String = map {
    when (it) {
        in '۰'..'۹' -> '0' + (it - '۰')
        in '٠'..'٩' -> '0' + (it - '٠')
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

/** IMEI check digit (Luhn): catches almost every single typing mistake. */
fun isValidImei(imei: String): Boolean {
    if (imei.length != 15 || !imei.all { it in '0'..'9' }) return false
    var sum = 0
    for (i in imei.indices) {
        var d = imei[i] - '0'
        if (i % 2 == 1) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
    }
    return sum % 10 == 0
}

private val IMEI_PATTERN = Regex("(?<!\\d)\\d{15,16}(?!\\d)")

/** Finds a valid IMEI inside text read from a barcode ("356938035643809", "IMEI:3569..." ...). */
fun extractImei(text: String): String? =
    IMEI_PATTERN.findAll(text).map { it.value.take(15) }.firstOrNull { isValidImei(it) }
