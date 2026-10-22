package ir.mobileshop.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ───────────────────────────── Drop-down lists for the product form ─────────────────────────────

/** [value] is what gets stored, [label] what the list shows, [dot] a small colour sample. */
class ComboOption(val value: String, val label: String = value, val dot: Color? = null)

private fun brand(en: String, fa: String) = ComboOption(en, "$en  —  $fa")

/** Phone brands first (same spelling as the brand filter chips), then accessory brands. */
val BRAND_OPTIONS = listOf(
    brand("Samsung", "سامسونگ"), brand("Apple", "اپل"), brand("Xiaomi", "شیائومی"),
    brand("Redmi", "ردمی"), brand("Poco", "پوکو"), brand("Huawei", "هوآوی"), brand("Honor", "آنر"),
    brand("Nokia", "نوکیا"), brand("Oppo", "اوپو"), brand("Vivo", "ویوو"), brand("Realme", "ریلمی"),
    brand("OnePlus", "وان‌پلاس"), brand("Motorola", "موتورولا"), brand("Sony", "سونی"),
    brand("Google", "گوگل (Pixel)"), brand("Infinix", "اینفینیکس"), brand("Tecno", "تکنو"),
    brand("itel", "آیتل"), brand("Nothing", "ناتینگ"), brand("Asus", "ایسوس"), brand("Lenovo", "لنوو"),
    brand("LG", "ال‌جی"), brand("ZTE", "زد‌تی‌ای"), brand("HTC", "اچ‌تی‌سی"), brand("Alcatel", "آلکاتل"),
    brand("Meizu", "میزو"), brand("BlackBerry", "بلک‌بری"), brand("GLX", "جی‌ال‌ایکس"),
    brand("Gplus", "جی‌پلاس"), brand("Dimo", "دیمو"), brand("Tecno Spark", "تکنو اسپارک"),
    // accessories
    brand("Anker", "انکر"), brand("Baseus", "بیسوس"), brand("JBL", "جی‌بی‌ال"), brand("Remax", "ریمکس"),
    brand("Hoco", "هوکو"), brand("Joyroom", "جوی‌روم"), brand("Havit", "هویت"), brand("Awei", "اوی"),
    brand("Edifier", "ادیفایر"), brand("Logitech", "لاجیتک"), brand("Belkin", "بلکین"),
    brand("Ugreen", "یوگرین"), brand("Energizer", "انرجایزر"), brand("Choetech", "چوتک"),
    brand("Proone", "پرووان"), brand("TSCO", "تسکو"), brand("Orico", "اوریکو"), brand("Samsung Original", "سامسونگ اورجینال")
)

private fun color(name: String, argb: Long? = null) = ComboOption(name, name, argb?.let { Color(it) })

val COLOR_OPTIONS = listOf(
    color("مشکی", 0xFF111111), color("سفید", 0xFFFFFFFF), color("نقره‌ای", 0xFFC0C0C0),
    color("طوسی", 0xFF9AA0A6), color("خاکستری", 0xFF808080), color("مدادی", 0xFF555B61),
    color("گرافیتی", 0xFF41424C), color("طلایی", 0xFFD4AF37), color("رزگلد", 0xFFB76E79),
    color("مسی", 0xFFB87333), color("برنزی", 0xFFCD7F32), color("کرم", 0xFFF5E6C8), color("بژ", 0xFFE8D8B9),
    color("قرمز", 0xFFD32F2F), color("نارنجی", 0xFFFF8A00), color("نارنجی کیهانی", 0xFFE9692C),
    color("زرد", 0xFFFFD600), color("لیمویی", 0xFFD5F25E), color("سبز", 0xFF2E9E4F),
    color("سبز تیره", 0xFF1B5E20), color("سبز زیتونی", 0xFF708238), color("سبز نعنایی", 0xFFA7E8C8),
    color("فیروزه‌ای", 0xFF30D5C8), color("آبی", 0xFF1E73D8), color("آبی روشن", 0xFF7EC8F2),
    color("آبی یخی", 0xFFCFE8F7), color("آبی تیره", 0xFF0D3B8C), color("سرمه‌ای", 0xFF1A237E),
    color("نیمه‌شب", 0xFF1C1F2A), color("بنفش", 0xFF7B3FA0), color("بنفش تیره", 0xFF4A235A),
    color("یاسی", 0xFFC9B6E4), color("صورتی", 0xFFF4A6C0), color("صورتی روشن", 0xFFFAD1DC),
    color("قهوه‌ای", 0xFF6D4C41), color("نور ستاره", 0xFFF0E6D2),
    color("تیتانیوم طبیعی", 0xFFB8B2A7), color("تیتانیوم مشکی", 0xFF2B2B2D),
    color("تیتانیوم سفید", 0xFFE6E4DF), color("تیتانیوم آبی", 0xFF4B5A6E),
    color("تیتانیوم صحرایی", 0xFFC4A484), color("شفاف"), color("چندرنگ")
)

val STORAGE_OPTIONS = listOf(
    "4GB", "8GB", "16GB", "32GB", "64GB", "128GB", "256GB", "512GB", "1TB", "2TB",
    "4/64GB", "4/128GB", "6/128GB", "8/128GB", "8/256GB", "12/256GB", "12/512GB", "16/512GB", "16/1TB"
).map { ComboOption(it) }

/**
 * A text field with a drop-down list. Pick from the list, or type your own value when it is not in it.
 */
@Composable
fun ComboField(
    label: String,
    value: String,
    options: List<ComboOption>,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = shopFieldColors(),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Filled.ArrowDropDown, "باز کردن لیست", tint = InkGrey)
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label, fontSize = 14.sp) },
                    leadingIcon = if (o.dot != null) {
                        {
                            Box(
                                Modifier.size(16.dp).clip(CircleShape).background(o.dot)
                                    .border(1.dp, CardBorder, CircleShape)
                            )
                        }
                    } else null,
                    onClick = { onChange(o.value); expanded = false }
                )
            }
        }
    }
}

// ───────────────────────────── Shamsi date from three lists ─────────────────────────────

private val MONTH_NAMES = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
private const val YEAR_MIN = 1300
private const val YEAR_MAX = 1500

private fun isJalaliLeap(y: Int) = (y % 33) in setOf(1, 5, 9, 13, 17, 22, 26, 30)

private fun daysInJalaliMonth(y: Int, m: Int) = when {
    m <= 6 -> 31
    m <= 11 -> 30
    else -> if (isJalaliLeap(y)) 30 else 29
}

/**
 * Picks a Shamsi date from three lists: year (۱۳۰۰ تا ۱۵۰۰), month name, then day.
 * [value] is "yyyy/MM/dd" or empty; [onChange] always receives a complete valid date (or "" after «حذف تاریخ»).
 * Choosing any part of an empty date fills the other parts with today's date, so it is never half-filled.
 */
@Composable
fun ShamsiDateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    onChange: (String) -> Unit
) {
    val parsed = JalaliDates.parseShamsi(value)
    val today = remember { JalaliDates.parseShamsi(JalaliDates.getShamsiToday()) ?: intArrayOf(YEAR_MIN, 1, 1) }
    val base = parsed ?: today
    val years = remember { (YEAR_MIN..YEAR_MAX).map { it.toString().fa() } }
    val days = (1..daysInJalaliMonth(base[0], base[1])).map { it.toString().fa() }

    fun emit(y: Int, m: Int, d: Int) {
        val day = d.coerceIn(1, daysInJalaliMonth(y, m))
        onChange(String.format(java.util.Locale.US, "%04d/%02d/%02d", y, m, day))
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = InkGrey, modifier = Modifier.weight(1f))
            if (allowClear && parsed != null) {
                TextButton(onClick = { onChange("") }) { Text("حذف تاریخ", color = Red, fontSize = 12.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerBox(
                "سال", parsed?.let { it[0].toString().fa() }, years,
                parsed?.get(0)?.minus(YEAR_MIN), Modifier.weight(1.1f)
            ) { emit(YEAR_MIN + it, base[1], base[2]) }
            PickerBox(
                "ماه", parsed?.let { MONTH_NAMES[it[1] - 1] }, MONTH_NAMES,
                parsed?.get(1)?.minus(1), Modifier.weight(1.4f)
            ) { emit(base[0], it + 1, base[2]) }
            PickerBox(
                "روز", parsed?.let { it[2].toString().fa() }, days,
                parsed?.get(2)?.minus(1), Modifier.weight(0.8f)
            ) { emit(base[0], base[1], it + 1) }
        }
    }
}

/** A box that looks like a field; tapping it opens a scrollable list that starts at the current choice. */
@Composable
private fun PickerBox(
    placeholder: String,
    text: String?,
    items: List<String>,
    selected: Int?,
    modifier: Modifier,
    onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(52.dp).clip(shape).background(Color.White).border(1.dp, CardBorder, shape)
            .clickable { open = true }.padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text ?: placeholder, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1,
            color = if (text == null) InkGrey else InkDark
        )
        Icon(Icons.Filled.ArrowDropDown, null, tint = InkGrey)
    }
    if (open) {
        PickerDialog(placeholder, items, selected, onDismiss = { open = false }) {
            onPick(it)
            open = false
        }
    }
}

@Composable
private fun PickerDialog(
    title: String,
    items: List<String>,
    selected: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = ((selected ?: 0) - 2).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    title, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                LazyColumn(Modifier.heightIn(max = 360.dp), state = state) {
                    itemsIndexed(items) { i, t ->
                        Text(
                            t,
                            color = if (i == selected) Red else InkDark,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (i == selected) RedSoft else Color.Transparent)
                                .clickable { onPick(i) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
