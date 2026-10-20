package ir.mobileshop.inventory

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val CATEGORIES = listOf("گوشی", "لوازم جانبی", "قطعات")

private fun categoryIcon(category: String): ImageVector = when (category) {
    "لوازم جانبی" -> Icons.Filled.Headphones
    "قطعات" -> Icons.Filled.Build
    else -> Icons.Filled.PhoneAndroid
}

/** Lets the seller type Persian/Arabic digits too. */
private fun String.latinDigits(): String = map {
    when (it) {
        in '۰'..'۹' -> '0' + (it - '۰')
        in '٠'..'٩' -> '0' + (it - '٠')
        else -> it
    }
}.joinToString("")

// ───────────────────────────── Inventory tab ─────────────────────────────

@Composable
fun InventoryTab(
    vm: InventoryViewModel,
    contentPadding: PaddingValues,
    onEdit: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val status by vm.syncStatus.collectAsState()
    val list by vm.filtered.collectAsState()
    val all by vm.allProducts.collectAsState()
    val query by vm.query.collectAsState()
    val cat by vm.categoryFilter.collectAsState()
    val brand by vm.brandFilter.collectAsState()
    val sort by vm.sortOrder.collectAsState()
    var toDelete by remember { mutableStateOf<Product?>(null) }

    val totalQty = all.sumOf { it.quantity }
    val stockValue = all.sumOf { it.buyPrice * it.quantity }
    val lowCount = all.count { it.quantity <= LOW_STOCK_LIMIT }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroHeader(height = 168.dp) {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("فروشگاه بلاد شاپور", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                                Text("مدیریت موجودی کالا", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            HeaderIcon(Icons.Filled.Share, "خروجی اکسل") { vm.exportCsv(context) }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            placeholder = { Text("جستجو در کالاها: برند، مدل، رنگ یا IMEI", fontSize = 13.sp, color = InkGrey) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = InkGrey) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = shopFieldColors()
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStat("تعداد کل", "${totalQty.faNum()} عدد", Modifier.weight(1f))
                    MiniStat("ارزش موجودی", stockValue.faCompact(), Modifier.weight(1.2f))
                    MiniStat("کم‌موجود", lowCount.faNum(), Modifier.weight(1f), if (lowCount > 0) Red else GoodGreen)
                }
            }
            item {
                Text(
                    "امروز: ${JalaliDates.getShamsiToday()}  |  میلادی: " +
                        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PHONE_BRANDS.forEach { b ->
                            val selected = if (b == "همه برندها") brand == null else brand == b
                            AssistChip(
                                onClick = { vm.brandFilter.value = if (b == "همه برندها") null else b },
                                label = { Text(b, fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) RedSoft else Color.White
                                )
                            )
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SortOrder.entries.forEach { o ->
                            AssistChip(
                                onClick = { vm.sortOrder.value = o },
                                label = { Text(o.title, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (sort == o) RedSoft else Color.White
                                )
                            )
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryChip("همه", cat == null) { vm.categoryFilter.value = null }
                        CATEGORIES.forEach { c ->
                            CategoryChip(c, cat == c) { vm.categoryFilter.value = c }
                        }
                    }
                    SyncChip(status, onClick = {
                        if (status is SyncStatus.NotConfigured) onOpenSettings() else vm.syncNow()
                    })
                }
            }
            if (list.isEmpty()) {
                item { EmptyState() }
            } else {
                items(list, key = { it.uuid }) { p ->
                    ProductCard(
                        p = p,
                        onClick = { onEdit(p.uuid) },
                        onSell = { vm.adjustQuantity(p, -1) },
                        onAdd = { vm.adjustQuantity(p, +1) },
                        onDelete = { toDelete = p }
                    )
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Color.White,
            title = { Text("حذف کالا", fontWeight = FontWeight.Bold) },
            text = { Text("«${p.brand} ${p.model}» حذف شود؟") },
            confirmButton = {
                TextButton(onClick = { vm.delete(p); toDelete = null }) {
                    Text("حذف", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("انصراف", color = InkGrey) }
            }
        )
    }
}

@Composable
private fun MiniStat(title: String, value: String, modifier: Modifier, valueColor: Color = InkDark) {
    WhiteCard(modifier) {
        Text(title, fontSize = 11.sp, color = InkGrey)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Inventory2, null, tint = InkGrey, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("کالایی پیدا نشد", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "با دکمه‌ی «افزودن کالا» اولین کالا را ثبت کنید.",
            color = InkGrey, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ProductCard(
    p: Product,
    onClick: () -> Unit,
    onSell: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    val low = p.quantity <= LOW_STOCK_LIMIT
    WhiteCard(
        modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onClick),
        borderColor = if (low) Red.copy(alpha = 0.35f) else CardBorder
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(PageGrey),
                contentAlignment = Alignment.Center
            ) { Icon(categoryIcon(p.category), null, tint = InkGrey, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${p.brand} ${p.model}",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val sub = listOf(p.category, p.color, p.storage).filter { it.isNotBlank() }.joinToString(" • ")
                Text(sub, fontSize = 12.sp, color = InkGrey, modifier = Modifier.padding(top = 2.dp))
                if (p.imei.isNotBlank()) {
                    Text("IMEI: ${p.imei.fa()}", fontSize = 11.sp, color = InkGrey)
                }
            }
            Spacer(Modifier.width(8.dp))
            QuantityBadge(p.quantity, low)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("قیمت فروش", fontSize = 11.sp, color = InkGrey)
                Price(p.sellPrice)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("قیمت خرید", fontSize = 11.sp, color = InkGrey)
                Text(p.buyPrice.faMoney(), fontSize = 12.sp, color = InkDark)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSell,
                enabled = p.quantity > 0,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.Remove, null, Modifier.size(16.dp))
                Text(" فروش ۱ عدد", fontSize = 12.sp)
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = Red)
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                Text(" افزایش ۱ عدد", fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "حذف", tint = InkGrey)
            }
        }
    }
}

@Composable
private fun QuantityBadge(quantity: Int, low: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (low) RedSoft else GoodGreenSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            "${quantity.faNum()} عدد",
            color = if (low) Red else GoodGreen,
            fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
    }
}

// ───────────────────────────── Debtors tab ─────────────────────────────

@Composable
fun DebtorsTab(vm: InventoryViewModel, contentPadding: PaddingValues) {
    val debtors by vm.debtors.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var purchaseDate by rememberSaveable { mutableStateOf(JalaliDates.getShamsiToday()) }
    var dueDate by rememberSaveable { mutableStateOf("") }
    var datesInvalid by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    var remindEnabled by remember { mutableStateOf(ReminderPrefs.enabled(context) && hasSmsPermission(context)) }
    var remindDays by rememberSaveable { mutableStateOf(ReminderPrefs.daysBefore(context).toString()) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindEnabled = granted
        ReminderPrefs.setEnabled(context, granted)
    }

    // No photo banner on this screen: a plain title on the grey page background.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "مدیریت بدهکاران",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            WhiteCard(Modifier.padding(horizontal = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام بدهکار") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("شماره تماس") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("مبلغ بدهی") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = purchaseDate,
                        onValueChange = { purchaseDate = it; datesInvalid = false },
                        label = { Text("تاریخ خرید (شمسی)") },
                        placeholder = { Text("۱۴۰۵/۰۷/۲۸") },
                        singleLine = true,
                        isError = datesInvalid && JalaliDates.parseShamsi(purchaseDate) == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it; datesInvalid = false },
                        label = { Text("تاریخ تسویه حساب (شمسی)") },
                        placeholder = { Text("۱۴۰۵/۰۸/۱۵") },
                        singleLine = true,
                        isError = datesInvalid && dueDate.isNotBlank() && JalaliDates.parseShamsi(dueDate) == null,
                        supportingText = if (datesInvalid) {
                            { Text("تاریخ را به شکل ۱۴۰۵/۰۸/۱۵ وارد کنید") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("توضیحات") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        val value = amount.latinDigits().filter { it.isDigit() }.toLongOrNull() ?: 0L
                        val purchase = if (purchaseDate.isBlank()) "" else JalaliDates.normalizeShamsi(purchaseDate)
                        val due = if (dueDate.isBlank()) "" else JalaliDates.normalizeShamsi(dueDate)
                        if (purchase == null || due == null) {
                            datesInvalid = true
                        } else if (name.isNotBlank()) {
                            vm.saveDebtor(
                                Debtor(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    amount = value,
                                    note = note.trim(),
                                    purchaseDate = purchase,
                                    dueDate = due
                                )
                            )
                            name = ""; phone = ""; amount = ""; note = ""
                            purchaseDate = JalaliDates.getShamsiToday(); dueDate = ""; datesInvalid = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ثبت بدهکار") }
            }
        }
        item {
            WhiteCard(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("پیامک خودکار یادآوری تسویه", fontWeight = FontWeight.Bold)
                        Text(
                            "چند روز قبل از تاریخ تسویه، برای بدهکار پیامک فرستاده می‌شود.",
                            fontSize = 12.sp, color = InkGrey
                        )
                    }
                    Switch(
                        checked = remindEnabled,
                        onCheckedChange = { on ->
                            if (!on) {
                                remindEnabled = false
                                ReminderPrefs.setEnabled(context, false)
                            } else if (hasSmsPermission(context)) {
                                remindEnabled = true
                                ReminderPrefs.setEnabled(context, true)
                            } else {
                                smsPermission.launch(Manifest.permission.SEND_SMS)
                            }
                        }
                    )
                }
                if (remindEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = remindDays,
                        onValueChange = { v ->
                            val digits = v.latinDigits().filter { it.isDigit() }.take(2)
                            remindDays = digits
                            digits.toIntOrNull()?.takeIf { it in 1..30 }?.let { ReminderPrefs.setDaysBefore(context, it) }
                        },
                        label = { Text("چند روز قبل از تسویه") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (debtors.isEmpty()) {
            item {
                Text(
                    "هنوز بدهکاری ثبت نشده است",
                    modifier = Modifier.padding(24.dp),
                    color = InkGrey
                )
            }
        }
        items(debtors, key = { it.id }) { d ->
            WhiteCard(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (d.phone.isNotBlank()) {
                            Text(d.phone, fontSize = 12.sp, color = InkGrey)
                        }
                        Text(
                            "بدهی: " + d.amount.faCompact(),
                            color = Red, fontWeight = FontWeight.SemiBold
                        )
                        if (d.purchaseDate.isNotBlank()) {
                            Text("تاریخ خرید: " + d.purchaseDate.fa(), fontSize = 12.sp, color = InkGrey)
                        }
                        if (d.dueDate.isNotBlank()) {
                            val left = JalaliDates.daysUntil(d.dueDate)
                            Text("تاریخ تسویه: " + d.dueDate.fa(), fontSize = 12.sp, color = InkGrey)
                            if (left != null) {
                                Text(
                                    when {
                                        left > 0 -> "${left.faNum()} روز تا تسویه"
                                        left == 0 -> "امروز موعد تسویه است"
                                        else -> "${(-left).faNum()} روز از موعد گذشته"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (left <= 0) Red else GoodGreen
                                )
                            }
                            if (d.lastReminderDue == d.dueDate) {
                                Text("پیامک یادآوری ارسال شد", fontSize = 12.sp, color = GoodGreen)
                            }
                        }
                        if (d.note.isNotBlank()) {
                            Text(d.note, fontSize = 12.sp, color = InkGrey)
                        }
                    }
                    IconButton(onClick = { vm.deleteDebtor(d) }) {
                        Icon(Icons.Filled.Delete, "حذف بدهکار", tint = Red)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Form ─────────────────────────────

@Composable
fun FormScreen(vm: InventoryViewModel, productId: String, onDone: () -> Unit) {
    val all by vm.allProducts.collectAsState()
    val existing = remember(productId, all) { all.firstOrNull { it.uuid == productId } }

    var category by rememberSaveable(productId) { mutableStateOf(existing?.category ?: "گوشی") }
    var brand by rememberSaveable(productId) { mutableStateOf(existing?.brand ?: "") }
    var model by rememberSaveable(productId) { mutableStateOf(existing?.model ?: "") }
    var color by rememberSaveable(productId) { mutableStateOf(existing?.color ?: "") }
    var storage by rememberSaveable(productId) { mutableStateOf(existing?.storage ?: "") }
    var imei by rememberSaveable(productId) { mutableStateOf(existing?.imei ?: "") }
    var buy by rememberSaveable(productId) { mutableStateOf(existing?.buyPrice?.toString() ?: "") }
    var sell by rememberSaveable(productId) { mutableStateOf(existing?.sellPrice?.toString() ?: "") }
    var qty by rememberSaveable(productId) { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var note by rememberSaveable(productId) { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(PageGrey).imePadding()) {
        HeroHeader(height = 84.dp) {
            Row(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", onDone)
                Text(
                    if (productId.isEmpty()) "کالای جدید" else "ویرایش کالا",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WhiteCard {
                SectionTitle("مشخصات کالا")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c -> CategoryChip(c, category == c) { category = c } }
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("برند (مثلاً سامسونگ)", brand) { brand = it }
                    Field("مدل (مثلاً Galaxy A55)", model) { model = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Field("رنگ", color) { color = it } }
                        Box(Modifier.weight(1f)) { Field("حافظه (مثلاً 128GB)", storage) { storage = it } }
                    }
                    Field("IMEI (اختیاری)", imei, KeyboardType.Number) {
                        imei = it.latinDigits().filter(Char::isDigit).take(15)
                    }
                }
            }
            WhiteCard {
                SectionTitle("قیمت و موجودی")
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("قیمت خرید (تومان)", buy, KeyboardType.Number) { buy = it.latinDigits().filter(Char::isDigit) }
                    Field("قیمت فروش (تومان)", sell, KeyboardType.Number) { sell = it.latinDigits().filter(Char::isDigit) }
                    Field("تعداد", qty, KeyboardType.Number) { qty = it.latinDigits().filter(Char::isDigit).take(6) }
                    Field("توضیحات", note, singleLine = false) { note = it }
                }
            }
            error?.let { Text(it, color = Red, fontSize = 13.sp) }
        }
        Surface(color = Color.White, shadowElevation = 10.dp) {
            Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                Button(
                    onClick = {
                        if (brand.isBlank() || model.isBlank()) {
                            error = "برند و مدل را وارد کنید."
                            return@Button
                        }
                        val product = (existing ?: Product()).copy(
                            category = category,
                            brand = brand.trim(),
                            model = model.trim(),
                            color = color.trim(),
                            storage = storage.trim(),
                            imei = imei,
                            buyPrice = buy.toLongOrNull() ?: 0L,
                            sellPrice = sell.toLongOrNull() ?: 0L,
                            quantity = qty.toIntOrNull() ?: 0,
                            note = note.trim()
                        )
                        vm.save(product)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
                ) { Text("ثبت کالا", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = shopFieldColors()
    )
}

// ───────────────────────────── Report tab ─────────────────────────────

@Composable
fun ReportTab(vm: InventoryViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val all by vm.allProducts.collectAsState()

    val totalQty = all.sumOf { it.quantity }
    val costValue = all.sumOf { it.buyPrice * it.quantity }
    val saleValue = all.sumOf { it.sellPrice * it.quantity }
    val profit = saleValue - costValue
    val byBrand = all.groupBy { it.brand.ifBlank { "—" } }
        .map { (b, l) -> Triple(b, l.sumOf { it.quantity }, l.sumOf { it.buyPrice * it.quantity }) }
        .sortedByDescending { it.third }
    val maxValue = (byBrand.maxOfOrNull { it.third } ?: 0L).coerceAtLeast(1L)
    val low = all.filter { it.quantity <= LOW_STOCK_LIMIT }.sortedBy { it.quantity }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroHeader(height = 84.dp) {
                Row(
                    Modifier.fillMaxSize().statusBarsPadding().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "گزارش موجودی", color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, modifier = Modifier.weight(1f)
                    )
                    HeaderIcon(Icons.Filled.Share, "خروجی اکسل") { vm.exportCsv(context) }
                }
            }
            Column(
                Modifier.padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Filled.ShoppingBag, "تعداد کل کالا", "${totalQty.faNum()} عدد", Modifier.weight(1f))
                    StatTile(Icons.Filled.Payments, "ارزش خرید", costValue.faCompact() + " تومان", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Filled.Payments, "ارزش فروش", saleValue.faCompact() + " تومان", Modifier.weight(1f), Red)
                    StatTile(
                        Icons.Filled.TrendingUp, "سود احتمالی", profit.faCompact() + " تومان",
                        Modifier.weight(1f), if (profit >= 0) GoodGreen else Red
                    )
                }

                SectionTitle("موجودی به تفکیک برند")
                WhiteCard {
                    if (byBrand.isEmpty()) {
                        Text("هنوز کالایی ثبت نشده.", color = InkGrey, fontSize = 13.sp)
                    }
                    byBrand.forEachIndexed { i, (b, q, v) ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${q.faNum()} عدد", color = InkGrey, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { v.toFloat() / maxValue.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Red,
                            trackColor = PageGrey
                        )
                        Text(v.faMoney(), fontSize = 11.sp, color = InkGrey, modifier = Modifier.padding(top = 3.dp))
                    }
                }

                SectionTitle("کالاهای رو به اتمام")
                WhiteCard(borderColor = if (low.isEmpty()) CardBorder else Red.copy(alpha = 0.35f)) {
                    if (low.isEmpty()) {
                        Text("همه‌ی کالاها موجودی کافی دارند.", color = GoodGreen, fontSize = 13.sp)
                    }
                    low.forEachIndexed { i, p ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${p.brand} ${p.model} ${p.color}".trim(), Modifier.weight(1f), fontSize = 13.sp)
                            Text("${p.quantity.faNum()} عدد", color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = InkDark
) {
    WhiteCard(modifier) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(PageGrey),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = InkGrey, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 12.sp, color = InkGrey)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
