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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PAYMENT_METHODS = listOf("نقدی", "کارت‌به‌کارت", "چک", "قسط")

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
    var showClosed by rememberSaveable { mutableStateOf(false) }

    var paying by remember { mutableStateOf<DebtorView?>(null) }
    var settling by remember { mutableStateOf<DebtorView?>(null) }
    var deleting by remember { mutableStateOf<DebtorView?>(null) }
    var deletingPayment by remember { mutableStateOf<Payment?>(null) }

    val context = LocalContext.current
    var remindEnabled by remember { mutableStateOf(ReminderPrefs.enabled(context) && hasSmsPermission(context)) }
    var remindDays by rememberSaveable { mutableStateOf(ReminderPrefs.daysBefore(context).toString()) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindEnabled = granted
        ReminderPrefs.setEnabled(context, granted)
    }

    val open = debtors.filter { !it.debtor.closed }
    val closed = debtors.filter { it.debtor.closed }
    val totalOwed = open.sumOf { it.remaining }

    // No photo banner on this screen: a plain title on the grey page background.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("مدیریت بدهکاران", modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.exportDebtorsCsv(context) }) {
                    Icon(Icons.Filled.Share, "خروجی اکسل بدهکاران", tint = InkDark)
                }
            }
        }
        item {
            WhiteCard(Modifier.padding(horizontal = 16.dp)) {
                Text("جمع باقی‌مانده‌ی بدهی‌ها", fontSize = 12.sp, color = InkGrey)
                Price(totalOwed, size = 20.sp, color = if (totalOwed > 0) Red else GoodGreen)
                Text("${open.size.faNum()} بدهکار باز", fontSize = 12.sp, color = InkGrey)
            }
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
                    ShamsiDateField("تاریخ خرید", purchaseDate) { purchaseDate = it; datesInvalid = false }
                    ShamsiDateField("تاریخ تسویه حساب (اختیاری)", dueDate, allowClear = true) {
                        dueDate = it; datesInvalid = false
                    }
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
                        val value = amount.digitsOnly().toLongOrNull() ?: 0L
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
                            val digits = v.digitsOnly().take(2)
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
        if (open.isEmpty()) {
            item {
                Text(
                    "هنوز بدهکاری ثبت نشده است",
                    modifier = Modifier.padding(24.dp),
                    color = InkGrey
                )
            }
        }
        items(open, key = { it.debtor.id }) { v ->
            DebtorCard(
                v,
                onPay = { paying = v },
                onSettle = { settling = v },
                onDelete = { deleting = v },
                onDeletePayment = { deletingPayment = it }
            )
        }
        if (closed.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { showClosed = !showClosed }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تسویه‌شده‌ها (${closed.size.faNum()})",
                        Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Icon(
                        if (showClosed) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        null, tint = InkGrey
                    )
                }
            }
            if (showClosed) {
                items(closed, key = { "closed-" + it.debtor.id }) { v ->
                    WhiteCard(Modifier.padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.debtor.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "تسویه شد • جمع پرداختی: " + v.paid.faMoney(),
                                    fontSize = 12.sp, color = GoodGreen
                                )
                            }
                            TextButton(onClick = { vm.reopenDebtor(v.debtor) }) {
                                Text("بازگشایی", color = InkGrey, fontSize = 12.sp)
                            }
                            IconButton(onClick = { deleting = v }) {
                                Icon(Icons.Filled.Delete, "حذف بدهکار", tint = InkGrey)
                            }
                        }
                    }
                }
            }
        }
    }

    paying?.let { v ->
        PaymentDialog(
            view = v,
            onDismiss = { paying = null },
            onSave = { amountPaid, date, method, payNote ->
                vm.addPayment(v.debtor.id, amountPaid, date, method, payNote)
                paying = null
            }
        )
    }

    settling?.let { v ->
        AlertDialog(
            onDismissRequest = { settling = null },
            containerColor = Color.White,
            title = { Text("تسویه شد", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (v.remaining > 0) {
                        "باقی‌مانده‌ی ${v.remaining.faMoney()} به‌عنوان پرداخت نهایی ثبت می‌شود و «${v.debtor.name}» بسته می‌شود."
                    } else {
                        "بدهی «${v.debtor.name}» صفر است. بسته شود؟"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.settleDebtor(v); settling = null }) {
                    Text("تسویه شد", color = GoodGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { settling = null }) { Text("انصراف", color = InkGrey) } }
        )
    }

    deleting?.let { v ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color.White,
            title = { Text("حذف بدهکار", fontWeight = FontWeight.Bold) },
            text = { Text("«${v.debtor.name}» با همه‌ی پرداخت‌هایش حذف شود؟") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDebtor(v.debtor); deleting = null }) {
                    Text("حذف", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف", color = InkGrey) } }
        )
    }

    deletingPayment?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingPayment = null },
            containerColor = Color.White,
            title = { Text("حذف پرداخت", fontWeight = FontWeight.Bold) },
            text = { Text("پرداخت ${p.amount.faMoney()} (${p.date.fa()}) حذف شود؟ باقی‌مانده دوباره زیاد می‌شود.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePayment(p); deletingPayment = null }) {
                    Text("حذف", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deletingPayment = null }) { Text("انصراف", color = InkGrey) } }
        )
    }
}

@Composable
private fun DebtorCard(
    v: DebtorView,
    onPay: () -> Unit,
    onSettle: () -> Unit,
    onDelete: () -> Unit,
    onDeletePayment: (Payment) -> Unit
) {
    val d = v.debtor
    var expanded by rememberSaveable(d.id) { mutableStateOf(false) }

    WhiteCard(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (d.phone.isNotBlank()) Text(d.phone, fontSize = 12.sp, color = InkGrey)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف بدهکار", tint = Red) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountBox("بدهی کل", v.charged, InkDark, Modifier.weight(1f))
            AmountBox("پرداخت‌شده", v.paid, GoodGreen, Modifier.weight(1f))
            AmountBox("باقی‌مانده", v.remaining, if (v.remaining > 0) Red else GoodGreen, Modifier.weight(1f))
        }
        if (v.charged > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (v.paid.toFloat() / v.charged.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = GoodGreen,
                trackColor = PageGrey
            )
        }
        Spacer(Modifier.height(6.dp))
        if (d.purchaseDate.isNotBlank()) {
            Text("تاریخ خرید: " + d.purchaseDate.fa(), fontSize = 12.sp, color = InkGrey)
        }
        if (d.dueDate.isNotBlank()) {
            val left = JalaliDates.daysUntil(d.dueDate)
            Text("تاریخ تسویه: " + d.dueDate.fa(), fontSize = 12.sp, color = InkGrey)
            if (left != null && v.remaining > 0) {
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
        if (d.note.isNotBlank()) Text(d.note, fontSize = 12.sp, color = InkGrey)

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPay,
                enabled = v.remaining > 0,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
            ) { Text("ثبت پرداخت", fontSize = 12.sp) }
            Button(
                onClick = onSettle,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoodGreenSoft, contentColor = GoodGreen)
            ) { Text("تسویه شد", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "بستن" else "سابقه (${(v.payments.size + v.sales.size).faNum()})", color = InkGrey, fontSize = 12.sp)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null, tint = InkGrey, modifier = Modifier.size(18.dp)
                )
            }
        }

        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
            Spacer(Modifier.height(8.dp))
            if (d.amount > 0) {
                Text("بدهی ثبت‌شده‌ی اولیه: " + d.amount.faMoney(), fontSize = 12.sp, color = InkDark)
            }
            v.sales.sortedByDescending { it.date }.forEach { s ->
                Text(
                    "فروش نسیه ${s.date.fa()} — ${s.productName}: ${s.creditAmount.faMoney()}",
                    fontSize = 12.sp, color = Red
                )
            }
            if (v.payments.isEmpty()) {
                Text("هنوز پرداختی ثبت نشده.", fontSize = 12.sp, color = InkGrey, modifier = Modifier.padding(top = 4.dp))
            }
            v.payments.sortedWith(compareByDescending<Payment> { it.date }.thenByDescending { it.createdAt }).forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.amount.faMoney(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GoodGreen)
                        val sub = listOf(p.date.fa(), p.method, p.note).filter { it.isNotBlank() }.joinToString(" • ")
                        Text(sub, fontSize = 11.sp, color = InkGrey)
                    }
                    IconButton(onClick = { onDeletePayment(p) }) {
                        Icon(Icons.Filled.Delete, "حذف پرداخت", tint = InkGrey, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountBox(title: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(PageGrey).padding(8.dp)) {
        Text(title, fontSize = 10.sp, color = InkGrey)
        Text(amount.faCompact(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** One partial payment: amount (not above what is still owed), date and how it was paid. */
@Composable
private fun PaymentDialog(
    view: DebtorView,
    onDismiss: () -> Unit,
    onSave: (amount: Long, date: String, method: String, note: String) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(JalaliDates.getShamsiToday()) }
    var method by rememberSaveable { mutableStateOf(PAYMENT_METHODS.first()) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val value = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("ثبت پرداخت ${view.debtor.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("باقی‌مانده: " + view.remaining.faMoney(), fontWeight = FontWeight.SemiBold, color = Red)
                DialogField("مبلغ پرداخت (تومان)", amount, keyboard = KeyboardType.Number) {
                    amount = it.digitsOnly().take(13); error = null
                }
                TextButton(onClick = { amount = view.remaining.toString(); error = null }) {
                    Text("کل باقی‌مانده", color = Red, fontSize = 12.sp)
                }
                ShamsiDateField("تاریخ پرداخت", date) { date = it; error = null }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_METHODS.forEach { m -> CategoryChip(m, method == m) { method = m } }
                }
                DialogField("توضیحات (مثلاً شماره چک یا قسط)", note) { note = it }
                error?.let { Text(it, color = Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = JalaliDates.normalizeShamsi(date)
                when {
                    value <= 0 -> error = "مبلغ پرداخت را وارد کنید."
                    value > view.remaining -> error = "مبلغ از باقی‌مانده (${view.remaining.faMoney()}) بیشتر است."
                    day == null -> error = "تاریخ را انتخاب کنید."
                    else -> onSave(value, day, method, note.trim())
                }
            }) { Text("ثبت", color = Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = InkGrey) } }
    )
}
