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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KIND_FILTERS = listOf(
    null to "همه",
    "stock" to "موجودی",
    "sale" to "فروش",
    "product" to "کالا",
    "debtor" to "بدهکار"
)

private fun kindLabel(kind: String) = KIND_FILTERS.firstOrNull { it.first == kind }?.second ?: kind

private fun kindColor(kind: String) = when (kind) {
    "stock" -> Red
    "sale" -> GoodGreen
    "debtor" -> InkDark
    else -> InkGrey
}

private fun whenText(ms: Long): String =
    JalaliDates.shamsiOf(ms).fa() + "  " + SimpleDateFormat("HH:mm", Locale.US).format(Date(ms)).fa()

/** Who changed what and when, from every phone that syncs with the server. */
@Composable
fun HistoryScreen(vm: InventoryViewModel, onBack: () -> Unit) {
    val all by vm.history.collectAsState()
    var who by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }

    val people = all.map { it.who }.distinct().sorted()
    val s = search.trim()
    val list = all.filter {
        (who == null || it.who == who) &&
            (kind == null || it.kind == kind) &&
            (s.isEmpty() || it.title.contains(s, true) || it.detail.contains(s, true))
    }

    Column(Modifier.fillMaxSize().background(PageGrey)) {
        HeroHeader(height = 84.dp) {
            Row(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", onBack)
                Text("سابقه‌ی تغییرات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("جستجو (نام کالا یا بدهکار)", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = shopFieldColors()
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KIND_FILTERS.forEach { (k, label) -> CategoryChip(label, kind == k) { kind = k } }
                    }
                    if (people.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryChip("همه‌ی کاربران", who == null) { who = null }
                            people.forEach { p -> CategoryChip(p, who == p) { who = p } }
                        }
                    }
                    if (list.isEmpty()) {
                        Text(
                            if (all.isEmpty()) "هنوز تغییری ثبت نشده. از این به بعد هر تغییر موجودی، فروش و پرداخت اینجا می‌آید."
                            else "موردی پیدا نشد.",
                            color = InkGrey, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
            items(list, key = { it.id }) { e ->
                WhiteCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            kindLabel(e.kind), color = kindColor(e.kind), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(kindColor(e.kind).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(e.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(e.detail, fontSize = 13.sp, color = InkDark)
                    Spacer(Modifier.height(4.dp))
                    Text("${e.who}  •  ${whenText(e.happenedAt)}", fontSize = 11.sp, color = InkGrey)
                }
            }
        }
    }
}
