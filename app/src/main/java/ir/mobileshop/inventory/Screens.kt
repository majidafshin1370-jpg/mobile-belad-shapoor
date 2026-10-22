package ir.mobileshop.inventory

import android.Manifest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
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
    var toSell by remember { mutableStateOf<Product?>(null) }

    // The usual brands plus every brand that is really in stock (the brand list in the form has many more).
    val brandChips = remember(all) {
        (PHONE_BRANDS.filter { it != "همه برندها" } + all.map { it.brand.trim() }.filter { it.isNotEmpty() })
            .distinctBy { it.lowercase() } + "همه برندها"
    }

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
                        brandChips.forEach { b ->
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
                        onSell = { toSell = p },
                        onAdd = { vm.adjustQuantity(p, +1) },
                        onDelete = { toDelete = p }
                    )
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }

    toSell?.let { p ->
        SaleDialog(vm = vm, product = p, onDismiss = { toSell = null })
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
            ProductThumb(p, categoryIcon(p.category))
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
                Text(" ثبت فروش", fontSize = 12.sp)
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

    // A new product already needs its id now, because the photo is stored under it.
    val productUuid = rememberSaveable(productId) { existing?.uuid ?: UUID.randomUUID().toString() }
    var photoAt by rememberSaveable(productId) { mutableStateOf(existing?.photoAt ?: 0L) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var problems by remember { mutableStateOf<List<String>?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun photoSaved(at: Long?) {
        if (at != null) photoAt = at else error = "عکس خوانده نشد؛ دوباره امتحان کنید."
    }

    val captureFile = remember { File(File(context.cacheDir, "capture").apply { mkdirs() }, "photo.jpg") }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            val at = withContext(Dispatchers.IO) {
                ProductPhotos.save(context, productUuid) { FileInputStream(captureFile) }
            }
            photoSaved(at)
        }
    }
    val startCamera: () -> Unit = {
        captureFile.delete()
        captureFile.createNewFile()
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else error = "برای عکس گرفتن اجازه‌ی دوربین لازم است."
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val at = withContext(Dispatchers.IO) {
                ProductPhotos.save(context, productUuid) { context.contentResolver.openInputStream(uri) }
            }
            photoSaved(at)
        }
    }

    // IMEI checks: duplicate of another product, wrong length, wrong check digit (typing mistake).
    val duplicate = remember(imei, all, productUuid) {
        if (imei.isBlank()) null else all.firstOrNull { it.imei == imei && it.uuid != productUuid }
    }
    val imeiProblem = when {
        imei.isBlank() -> null
        imei.length != 15 -> "IMEI باید ۱۵ رقم باشد (الان ${imei.length.faNum()} رقم)"
        !isValidImei(imei) -> "رقم کنترل IMEI درست نیست؛ احتمالاً اشتباه تایپی است"
        else -> null
    }
    val duplicateText = duplicate?.let {
        "این IMEI قبلاً برای «${it.brand} ${it.model}» ثبت شده (موجودی: ${it.quantity.faNum()})"
    }

    if (scanning) {
        ImeiScannerScreen(
            onResult = { imei = it; scanning = false },
            onClose = { scanning = false }
        )
        return
    }

    val saveProduct = {
        val product = (existing ?: Product(uuid = productUuid)).copy(
            category = category,
            brand = brand.trim(),
            model = model.trim(),
            color = color.trim(),
            storage = storage.trim(),
            imei = imei,
            buyPrice = buy.toLongOrNull() ?: 0L,
            sellPrice = sell.toLongOrNull() ?: 0L,
            quantity = qty.toIntOrNull() ?: 0,
            note = note.trim(),
            photoAt = photoAt
        )
        vm.save(product)
        onDone()
    }

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
                SectionTitle("عکس کالا")
                Spacer(Modifier.height(6.dp))
                val photo = rememberProductPhoto(productUuid, photoAt, 700)
                Box(
                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(PageGrey),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) {
                        Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Filled.PhotoCamera, null, tint = InkGrey, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (hasCameraPermission(context)) startCamera()
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = Red)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(16.dp))
                        Text(" عکس گرفتن", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = Red)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(16.dp))
                        Text(" از گالری", fontSize = 12.sp)
                    }
                }
            }
            WhiteCard {
                SectionTitle("مشخصات کالا")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c -> CategoryChip(c, category == c) { category = c } }
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComboField("برند", brand, BRAND_OPTIONS) { brand = it }
                    Field("مدل (مثلاً Galaxy A55)", model) { model = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ComboField("رنگ", color, COLOR_OPTIONS, Modifier.weight(1f)) { color = it }
                        ComboField("حافظه", storage, STORAGE_OPTIONS, Modifier.weight(1f)) { storage = it }
                    }
                    Field(
                        "IMEI (اختیاری)", imei, KeyboardType.Number,
                        isError = duplicateText != null || imeiProblem != null,
                        supporting = duplicateText ?: imeiProblem,
                        trailing = {
                            IconButton(onClick = { scanning = true }) {
                                Icon(Icons.Filled.QrCodeScanner, "اسکن IMEI با دوربین", tint = Red)
                            }
                        }
                    ) {
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
                        val found = listOfNotNull(duplicateText, imeiProblem)
                        if (found.isEmpty()) saveProduct() else problems = found
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
                ) { Text("ثبت کالا", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    problems?.let { list ->
        AlertDialog(
            onDismissRequest = { problems = null },
            containerColor = Color.White,
            title = { Text("هشدار IMEI", fontWeight = FontWeight.Bold) },
            text = { Text(list.joinToString("\n\n")) },
            confirmButton = {
                TextButton(onClick = { problems = null; saveProduct() }) {
                    Text("با همین ثبت شود", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { problems = null }) { Text("بازگشت و اصلاح", color = InkGrey) }
            }
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        supportingText = if (supporting != null) {
            { Text(supporting, fontSize = 12.sp) }
        } else null,
        trailingIcon = trailing,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = shopFieldColors()
    )
}

// ───────────────────────────── Report tab ─────────────────────────────

@Composable
fun ReportTab(vm: InventoryViewModel, contentPadding: PaddingValues, onOpenHistory: () -> Unit) {
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
                    HeaderIcon(Icons.Filled.History, "سابقه‌ی تغییرات", onOpenHistory)
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

                InsightsSections(vm)
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
