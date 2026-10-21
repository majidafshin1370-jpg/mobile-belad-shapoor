package ir.mobileshop.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DAY_MS = 86_400_000L

private data class OrderRow(val product: Product, val sold30: Int, val suggest: Int)
private data class SlowRow(val product: Product, val days: Int, val everSold: Boolean, val tiedUp: Long)

private fun label(p: Product) = listOf(p.brand, p.model, p.color, p.storage).filter { it.isNotBlank() }.joinToString(" ")

/** «لیست سفارش» and «کالاهای راکد»: both are worked out from stock and the recorded sales. */
@Composable
fun InsightsSections(vm: InventoryViewModel) {
    val context = LocalContext.current
    val products by vm.allProducts.collectAsState()
    val sales by vm.sales.collectAsState()
    var slowDays by rememberSaveable { mutableStateOf(30) }

    val now = System.currentTimeMillis()

    // ── order list: everything that is low on stock ──
    val sold30 = sales.filter { it.createdAt >= now - 30 * DAY_MS }
        .groupBy { it.productId }.mapValues { (_, l) -> l.sumOf { it.quantity } }
    val orderRows = products.filter { it.quantity <= LOW_STOCK_LIMIT }.map { p ->
        val sold = sold30[p.uuid] ?: 0
        // Aim to hold a month of sales, and never less than 3 pieces.
        OrderRow(p, sold, (maxOf(sold, 3) - p.quantity).coerceAtLeast(1))
    }.sortedBy { it.product.quantity }
    val orderCost = orderRows.sumOf { it.product.buyPrice * it.suggest }

    SectionTitle("فهرست سفارش")
    WhiteCard(borderColor = if (orderRows.isEmpty()) CardBorder else Red.copy(alpha = 0.35f)) {
        if (orderRows.isEmpty()) {
            Text("فعلاً چیزی برای سفارش لازم نیست.", color = GoodGreen, fontSize = 13.sp)
        } else {
            Text(
                "کالاهایی که موجودی‌شان ${LOW_STOCK_LIMIT.faNum()} یا کمتر است. تعداد پیشنهادی = فروش ۳۰ روز اخیر (حداقل ۳) منهای موجودی.",
                fontSize = 11.sp, color = InkGrey
            )
            Spacer(Modifier.height(8.dp))
            orderRows.forEachIndexed { i, r ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(label(r.product), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "موجودی ${r.product.quantity.faNum()} • فروش ۳۰ روز اخیر ${r.sold30.faNum()}",
                            fontSize = 11.sp, color = InkGrey
                        )
                    }
                    Text("سفارش ${r.suggest.faNum()} عدد", color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("هزینه‌ی تقریبی خرید: " + orderCost.faMoney(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val text = buildString {
                        appendLine("لیست سفارش — ${JalaliDates.getShamsiToday().fa()}")
                        orderRows.forEach {
                            appendLine("• ${label(it.product)}: ${it.suggest.faNum()} عدد (موجودی ${it.product.quantity.faNum()})")
                        }
                    }
                    vm.shareText(context, "ارسال لیست سفارش", text)
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = Red)
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                Text(" ارسال لیست", fontSize = 12.sp)
            }
        }
    }

    // ── stagnant stock: still in the shop but not sold for a long time ──
    val lastSale = sales.groupBy { it.productId }.mapValues { (_, l) -> l.maxOf { it.createdAt } }
    val slowRows = products.filter { it.quantity > 0 }.mapNotNull { p ->
        val reference = lastSale[p.uuid] ?: p.createdAt
        val days = ((now - reference) / DAY_MS).toInt()
        if (days >= slowDays) SlowRow(p, days, lastSale.containsKey(p.uuid), p.buyPrice * p.quantity) else null
    }.sortedByDescending { it.tiedUp }
    val tied = slowRows.sumOf { it.tiedUp }

    SectionTitle("کالاهای راکد و کم‌فروش")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30, 60, 90).forEach { d ->
            CategoryChip("بدون فروش ${d.faNum()} روز", slowDays == d) { slowDays = d }
        }
    }
    WhiteCard {
        if (slowRows.isEmpty()) {
            Text("کالای راکدی نیست؛ همه در ${slowDays.faNum()} روز اخیر فروش داشته‌اند.", color = GoodGreen, fontSize = 13.sp)
        } else {
            Text(
                "سرمایه‌ی خوابیده در این کالاها: " + tied.faMoney(),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Red
            )
            Spacer(Modifier.height(8.dp))
            slowRows.take(30).forEachIndexed { i, r ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(label(r.product), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            (if (r.everSold) "${r.days.faNum()} روز از آخرین فروش" else "از زمان ثبت هنوز فروخته نشده (${r.days.faNum()} روز)") +
                                " • موجودی ${r.product.quantity.faNum()}",
                            fontSize = 11.sp, color = InkGrey
                        )
                    }
                    Text(r.tiedUp.faCompact(), fontSize = 12.sp, color = InkDark, fontWeight = FontWeight.SemiBold)
                }
            }
            if (slowRows.size > 30) {
                Text("و ${(slowRows.size - 30).faNum()} کالای دیگر…", fontSize = 11.sp, color = InkGrey, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    Text(
        "«راکد» یعنی موجودی دارد ولی در این مدت فروشی ثبت نشده. فروش‌هایی که قبل از نصب این نسخه انجام شده‌اند در گزارش نیستند.",
        fontSize = 11.sp, color = InkGrey
    )
}
