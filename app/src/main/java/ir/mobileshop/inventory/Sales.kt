package ir.mobileshop.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Text field used inside the dialogs of the sale and debtor screens. */
@Composable
fun DialogField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    isError: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, fontSize = 13.sp, color = InkGrey) }
        } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = shopFieldColors()
    )
}

/** Keeps only the digits (Persian digits are converted), for money and count fields. */
fun String.digitsOnly(): String = latinDigits().filter { it.isDigit() }

// ───────────────────────────── Sale dialog ─────────────────────────────

/** «ثبت فروش»: count, price, buyer, date and cash / credit. Saves through [InventoryViewModel.sell]. */
@Composable
fun SaleDialog(vm: InventoryViewModel, product: Product, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var qty by rememberSaveable { mutableStateOf("1") }
    var price by rememberSaveable { mutableStateOf(product.sellPrice.toString()) }
    var buyer by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(JalaliDates.getShamsiToday()) }
    var credit by rememberSaveable { mutableStateOf(false) }
    var paidNow by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val q = qty.toIntOrNull() ?: 0
    val unit = price.toLongOrNull() ?: 0L
    val total = unit * q
    val paid = paidNow.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("ثبت فروش", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${product.brand} ${product.model} ${product.color}".trim() +
                        "  •  موجودی: ${product.quantity.faNum()} عدد",
                    fontSize = 13.sp, color = InkGrey
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogField("تعداد", qty, Modifier.weight(1f), KeyboardType.Number) {
                        qty = it.digitsOnly().take(6)
                    }
                    DialogField("قیمت واحد (تومان)", price, Modifier.weight(1.6f), KeyboardType.Number) {
                        price = it.digitsOnly().take(13)
                    }
                }
                Text("جمع فروش: " + total.faMoney(), fontWeight = FontWeight.Bold, color = InkDark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip("نقدی", !credit) { credit = false }
                    CategoryChip("نسیه (بدهکار)", credit) { credit = true }
                }
                DialogField(
                    if (credit) "نام خریدار (الزامی)" else "نام خریدار (اختیاری)", buyer
                ) { buyer = it }
                DialogField(
                    if (credit) "شماره تماس" else "شماره تماس (اختیاری)", phone,
                    keyboard = KeyboardType.Phone
                ) { phone = it }
                DialogField("تاریخ فروش (شمسی)", date, placeholder = "۱۴۰۵/۰۸/۱۵") { date = it }
                if (credit) {
                    DialogField("پیش‌پرداخت (اختیاری)", paidNow, keyboard = KeyboardType.Number) {
                        paidNow = it.digitsOnly().take(13)
                    }
                    DialogField(
                        "تاریخ تسویه (شمسی، اختیاری)", dueDate, placeholder = "۱۴۰۵/۰۹/۱۵"
                    ) { dueDate = it }
                    if (total > 0) {
                        Text(
                            "به بدهی خریدار اضافه می‌شود: " + (total - paid).coerceAtLeast(0L).faMoney(),
                            fontSize = 12.sp, color = Red
                        )
                    }
                }
                error?.let { Text(it, color = Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val day = JalaliDates.normalizeShamsi(date)
                    val due = if (dueDate.isBlank()) "" else JalaliDates.normalizeShamsi(dueDate)
                    error = when {
                        q < 1 -> "تعداد را وارد کنید."
                        q > product.quantity -> "موجودی کافی نیست (موجودی: ${product.quantity.faNum()} عدد)."
                        unit <= 0 -> "قیمت فروش را وارد کنید."
                        day == null -> "تاریخ را به شکل ۱۴۰۵/۰۸/۱۵ وارد کنید."
                        credit && buyer.isBlank() -> "برای فروش نسیه نام خریدار لازم است."
                        credit && due == null -> "تاریخ تسویه را به شکل ۱۴۰۵/۰۸/۱۵ وارد کنید."
                        credit && paid >= total -> "پیش‌پرداخت باید کمتر از جمع فروش باشد؛ برای پرداخت کامل «نقدی» را بزنید."
                        else -> null
                    }
                    if (error == null && day != null) {
                        busy = true
                        scope.launch {
                            val problem = vm.sell(
                                SaleInput(
                                    product = product, quantity = q, unitPrice = unit,
                                    buyerName = buyer, buyerPhone = phone, date = day,
                                    credit = credit, paidNow = if (credit) paid else 0L,
                                    dueDate = due ?: ""
                                )
                            )
                            busy = false
                            if (problem == null) onDismiss() else error = problem
                        }
                    }
                }
            ) { Text("ثبت فروش", color = Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف", color = InkGrey) }
        }
    )
}

// ───────────────────────────── Sales tab ─────────────────────────────

private data class PeriodRow(val label: String, val count: Int, val total: Long, val profit: Long)

@Composable
fun SalesTab(vm: InventoryViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val sales by vm.sales.collectAsState()
    var monthly by rememberSaveable { mutableStateOf(false) }
    var toCancel by remember { mutableStateOf<Sale?>(null) }

    val today = JalaliDates.getShamsiToday()
    val month = today.take(7)
    val todaySales = sales.filter { it.date == today }
    val monthSales = sales.filter { it.date.startsWith(month) }

    val rows = sales
        .groupBy { if (monthly) it.date.take(7) else it.date }
        .map { (k, l) -> PeriodRow(k, l.sumOf { it.quantity }, l.sumOf { it.total }, l.sumOf { it.profit }) }
        .sortedByDescending { it.label }
        .take(if (monthly) 12 else 31)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroHeader(height = 84.dp) {
                    Row(
                        Modifier.fillMaxSize().statusBarsPadding().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "فروش و سود", color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, modifier = Modifier.weight(1f)
                        )
                        HeaderIcon(Icons.Filled.Share, "خروجی اکسل") { vm.exportSalesCsv(context) }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryTile("امروز", todaySales.sumOf { it.total }, todaySales.sumOf { it.profit }, Modifier.weight(1f))
                    SummaryTile("این ماه", monthSales.sumOf { it.total }, monthSales.sumOf { it.profit }, Modifier.weight(1f))
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("گزارش سود")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryChip("روزانه", !monthly) { monthly = false }
                        CategoryChip("ماهانه", monthly) { monthly = true }
                    }
                    WhiteCard {
                        if (rows.isEmpty()) {
                            Text("هنوز فروشی ثبت نشده. از تب موجودی، دکمه‌ی «ثبت فروش» را بزنید.", color = InkGrey, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            if (rows.isNotEmpty()) {
                                Text("تاریخ", Modifier.weight(1.2f), fontSize = 11.sp, color = InkGrey)
                                Text("فروش", Modifier.weight(1.4f), fontSize = 11.sp, color = InkGrey)
                                Text("سود", Modifier.weight(1.4f), fontSize = 11.sp, color = InkGrey)
                            }
                        }
                        rows.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1.2f)) {
                                    Text(r.label.fa(), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("${r.count.faNum()} عدد", fontSize = 11.sp, color = InkGrey)
                                }
                                Text(r.total.faCompact(), Modifier.weight(1.4f), fontSize = 13.sp)
                                Text(
                                    r.profit.faCompact(), Modifier.weight(1.4f), fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (r.profit >= 0) GoodGreen else Red
                                )
                            }
                        }
                    }
                    Text(
                        "سود = (قیمت فروش − قیمت خرید کالا هنگام فروش) × تعداد؛ فروش نسیه هم در همان روز فروش حساب می‌شود.",
                        fontSize = 11.sp, color = InkGrey
                    )
                    SectionTitle("فروش‌های اخیر")
                }
            }
            items(sales.take(100), key = { it.id }) { s ->
                SaleCard(s, Modifier.padding(horizontal = 16.dp)) { toCancel = s }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }

    toCancel?.let { s ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            containerColor = Color.White,
            title = { Text("برگشت فروش", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "فروش «${s.productName}» لغو شود؟ " + s.quantity.faNum() + " عدد به موجودی برمی‌گردد" +
                        if (s.credit) " و بدهی و پیش‌پرداخت این فروش هم از حساب خریدار برداشته می‌شود." else "."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.cancelSale(s); toCancel = null }) {
                    Text("برگشت فروش", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("انصراف", color = InkGrey) } }
        )
    }
}

@Composable
private fun SummaryTile(title: String, total: Long, profit: Long, modifier: Modifier) {
    WhiteCard(modifier) {
        Text(title, fontSize = 12.sp, color = InkGrey)
        Spacer(Modifier.height(4.dp))
        Text("فروش", fontSize = 11.sp, color = InkGrey)
        Text(total.faCompact() + " تومان", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("سود", fontSize = 11.sp, color = InkGrey)
        Text(
            profit.faCompact() + " تومان", fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (profit >= 0) GoodGreen else Red
        )
    }
}

@Composable
private fun SaleCard(s: Sale, modifier: Modifier, onCancel: () -> Unit) {
    WhiteCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${s.quantity.faNum()} عدد × ${s.unitPrice.faNum()}  =  ${s.total.faMoney()}",
                    fontSize = 12.sp, color = InkDark
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (s.credit) "نسیه" else "نقدی",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (s.credit) Red else GoodGreen,
                modifier = Modifier
                    .background(if (s.credit) RedSoft else GoodGreenSoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val who = listOf(s.buyerName, s.buyerPhone).filter { it.isNotBlank() }.joinToString(" • ")
                Text(s.date.fa() + if (who.isNotEmpty()) "  |  $who" else "", fontSize = 11.sp, color = InkGrey)
                Text(
                    "سود: " + s.profit.faMoney(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (s.profit >= 0) GoodGreen else Red
                )
            }
            TextButton(onClick = onCancel) { Text("برگشت فروش", color = InkGrey, fontSize = 12.sp) }
        }
    }
}
