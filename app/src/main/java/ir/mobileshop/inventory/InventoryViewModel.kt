package ir.mobileshop.inventory

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val LOW_STOCK_LIMIT = 2

val PHONE_BRANDS = listOf("Samsung", "Apple", "Xiaomi", "Nokia", "Huawei", "Honor", "Oppo", "همه برندها")

enum class SortOrder(val title: String) {
    NEWEST("جدیدترین"),
    OLDEST("قدیمی\u200cترین"),
    HIGHEST_PRICE("بالاترین قیمت"),
    LOWEST_PRICE("پایین\u200cترین قیمت"),
    HIGHEST_STOCK("بیشترین موجودی")
}
private const val AUTO_SYNC_MS = 60_000L

/** A debtor plus the numbers the screen needs. [charged] = debt + credit sales, [remaining] = charged − paid. */
data class DebtorView(
    val debtor: Debtor,
    val charged: Long,
    val paid: Long,
    val remaining: Long,
    val payments: List<Payment>,
    val sales: List<Sale>
)

/** What the sale dialog collects. [paidNow] only matters for credit sales (down payment). */
data class SaleInput(
    val product: Product,
    val quantity: Int,
    val unitPrice: Long,
    val buyerName: String,
    val buyerPhone: String,
    val date: String,
    val credit: Boolean,
    val paidNow: Long,
    val dueDate: String
)

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).productDao()
    val sync = SyncManager(app, dao)

    val query = MutableStateFlow("")
    val categoryFilter = MutableStateFlow<String?>(null)
    val brandFilter = MutableStateFlow<String?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val syncStatus = MutableStateFlow<SyncStatus>(
        if (sync.isConfigured) SyncStatus.Ok(0L) else SyncStatus.NotConfigured
    )

    val allProducts: StateFlow<List<Product>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<Payment>> = dao.observePayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<Sale>> = dao.observeSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every debtor with the money charged, paid and still owed already worked out. */
    val debtors: StateFlow<List<DebtorView>> =
        combine(dao.observeDebtors(), payments, sales) { list, pays, sold ->
            list.map { d ->
                val myPays = pays.filter { it.debtorId == d.id }
                val mySales = sold.filter { it.debtorId == d.id && it.creditAmount > 0 }
                val charged = d.amount + mySales.sumOf { it.creditAmount }
                val paid = myPays.sumOf { it.amount }
                DebtorView(d, charged, paid, (charged - paid).coerceAtLeast(0L), myPays, mySales)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<Product>> =
        combine(allProducts, query, categoryFilter, brandFilter, sortOrder) { list, q, cat, brand, order ->
            val s = q.trim()
            val result = list.filter { p ->
                (cat == null || p.category == cat) &&
                    (brand == null || p.brand.equals(brand, true)) &&
                    (s.isEmpty() ||
                        p.brand.contains(s, true) ||
                        p.model.contains(s, true) ||
                        p.imei.contains(s) ||
                        p.color.contains(s, true))
            }
            when (order) {
                SortOrder.NEWEST -> result.sortedByDescending { it.createdAt }
                SortOrder.OLDEST -> result.sortedBy { it.createdAt }
                SortOrder.HIGHEST_PRICE -> result.sortedByDescending { it.sellPrice }
                SortOrder.LOWEST_PRICE -> result.sortedBy { it.sellPrice }
                SortOrder.HIGHEST_STOCK -> result.sortedByDescending { it.quantity }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Keeps the phones in step while the app is open; failures just retry next round.
        viewModelScope.launch {
            while (true) {
                runSync()
                delay(AUTO_SYNC_MS)
            }
        }
    }

    fun syncNow() = viewModelScope.launch { runSync() }

    private suspend fun runSync() {
        if (!sync.isConfigured) {
            syncStatus.value = SyncStatus.NotConfigured
            return
        }
        syncStatus.value = SyncStatus.Syncing
        syncStatus.value = sync.sync()
    }

    /** Saves the server settings; a different server means everything is uploaded again. */
    fun saveServer(url: String, token: String) = viewModelScope.launch {
        val changed = url.trim().trimEnd('/') != sync.serverUrl
        sync.serverUrl = url
        sync.token = token
        if (changed) sync.resetForNewServer()
        runSync()
    }

    fun save(product: Product) = viewModelScope.launch {
        dao.upsert(product.copy(updatedAt = System.currentTimeMillis(), dirty = true))
        runSync()
    }

    fun delete(product: Product) = viewModelScope.launch {
        dao.upsert(product.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
        runSync()
    }

    fun saveDebtor(debtor: Debtor) = viewModelScope.launch {
        dao.upsertDebtor(debtor.copy(updatedAt = System.currentTimeMillis(), dirty = true))
        runSync()
    }

    fun deleteDebtor(debtor: Debtor) = viewModelScope.launch {
        dao.deleteDebtorCascade(debtor.id, System.currentTimeMillis())
        runSync()
    }

    /** Records one partial payment (cheque, instalment, cash...). */
    fun addPayment(debtorId: String, amount: Long, date: String, method: String, note: String) =
        viewModelScope.launch {
            dao.upsertPayment(
                Payment(debtorId = debtorId, amount = amount, date = date, method = method, note = note)
            )
            runSync()
        }

    fun deletePayment(payment: Payment) = viewModelScope.launch {
        dao.upsertPayment(payment.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
        runSync()
    }

    /** «تسویه شد»: whatever is still owed is recorded as a final payment and the debtor is closed. */
    fun settleDebtor(view: DebtorView) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val finalPayment = if (view.remaining > 0) {
            Payment(
                debtorId = view.debtor.id, amount = view.remaining,
                date = JalaliDates.getShamsiToday(), method = "تسویه نهایی"
            )
        } else null
        dao.settleDebtor(view.debtor, finalPayment, now)
        runSync()
    }

    fun reopenDebtor(debtor: Debtor) = viewModelScope.launch {
        dao.upsertDebtor(debtor.copy(closed = false, updatedAt = System.currentTimeMillis(), dirty = true))
        runSync()
    }

    /**
     * Registers a sale: stock goes down, the sale is stored with date, price and buyer, and a credit
     * sale is added to the buyer's debt (an open debtor with the same name is reused).
     * Returns an error text for the screen, or null when saved.
     */
    suspend fun sell(input: SaleInput): String? {
        val now = System.currentTimeMillis()
        val product = dao.get(input.product.uuid)
        if (product == null || product.deleted) return "این کالا دیگر وجود ندارد."
        if (input.quantity < 1 || input.quantity > product.quantity) {
            return "موجودی کافی نیست (موجودی: ${product.quantity.faNum()} عدد)."
        }
        val total = input.unitPrice * input.quantity
        val creditAmount = if (input.credit) total - input.paidNow else 0L
        val isCredit = input.credit && creditAmount > 0

        var debtor: Debtor? = null
        if (isCredit) {
            val name = input.buyerName.trim()
            val phone = input.buyerPhone.trim()
            val existing = dao.getOpenDebtors().firstOrNull { d ->
                d.name.trim().equals(name, ignoreCase = true) &&
                    (phone.isBlank() || d.phone.isBlank() ||
                        d.phone.latinDigits() == phone.latinDigits())
            }
            debtor = if (existing != null) {
                existing.copy(
                    phone = existing.phone.ifBlank { phone },
                    dueDate = existing.dueDate.ifBlank { input.dueDate },
                    updatedAt = now, dirty = true
                )
            } else {
                Debtor(
                    name = name, phone = phone, amount = 0L,
                    purchaseDate = input.date, dueDate = input.dueDate
                )
            }
        }

        val productName = listOf(product.brand, product.model, product.color)
            .filter { it.isNotBlank() }.joinToString(" ")
        val sale = Sale(
            productId = product.uuid, productName = productName, quantity = input.quantity,
            unitPrice = input.unitPrice, unitCost = product.buyPrice,
            buyerName = input.buyerName.trim(), buyerPhone = input.buyerPhone.trim(),
            date = input.date, credit = isCredit, debtorId = debtor?.id ?: "",
            creditAmount = if (isCredit) creditAmount else 0L
        )
        val down = if (isCredit && input.paidNow > 0 && debtor != null) {
            Payment(
                debtorId = debtor.id, saleId = sale.id, amount = input.paidNow,
                date = input.date, method = "پیش‌پرداخت", note = productName
            )
        } else null

        dao.recordSale(
            product.copy(quantity = product.quantity - input.quantity, updatedAt = now, dirty = true),
            sale, debtor, down
        )
        runSync()
        return null
    }

    /** Takes a sale back: stock returns and the credit/down payment on the debtor disappear. */
    fun cancelSale(sale: Sale) = viewModelScope.launch {
        dao.cancelSale(sale.id, System.currentTimeMillis())
        runSync()
    }

    /** Adjusts quantity by [delta] (e.g. -1 when one unit is sold). Never below zero. */
    fun adjustQuantity(product: Product, delta: Int) = viewModelScope.launch {
        dao.upsert(
            product.copy(
                quantity = (product.quantity + delta).coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
                dirty = true
            )
        )
        runSync()
    }

    /** Writes a CSV (UTF-8 with BOM so Excel shows Persian correctly) and opens the share sheet. */
    fun exportCsv(context: Context) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "inventory_$stamp.csv")
        val sb = StringBuilder("﻿")
        sb.appendLine("دسته,برند,مدل,رنگ,حافظه,IMEI,قیمت خرید,قیمت فروش,تعداد,ارزش خرید,توضیحات")
        allProducts.value.forEach { p ->
            sb.appendLine(
                listOf(
                    p.category, p.brand, p.model, p.color, p.storage, p.imei,
                    p.buyPrice.toString(), p.sellPrice.toString(), p.quantity.toString(),
                    (p.buyPrice * p.quantity).toString(), p.note
                ).joinToString(",") { csvEscape(it) }
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)

        shareCsv(context, listOf(file), "ارسال گزارش موجودی")
    }

    /** Debtors (with total, paid, remaining) and every payment, as two Excel-ready files. */
    fun exportDebtorsCsv(context: Context) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val list = debtors.value
        val debtorsText = StringBuilder("\uFEFF")
        debtorsText.appendLine("نام,شماره تماس,بدهی کل,پرداخت‌شده,باقی‌مانده,وضعیت,تاریخ خرید,تاریخ تسویه,توضیحات")
        list.forEach { v ->
            debtorsText.appendLine(
                csvRow(
                    v.debtor.name, v.debtor.phone, v.charged, v.paid, v.remaining,
                    if (v.debtor.closed) "تسویه شده" else "باز",
                    v.debtor.purchaseDate, v.debtor.dueDate, v.debtor.note
                )
            )
        }
        val paymentsText = StringBuilder("\uFEFF")
        paymentsText.appendLine("نام بدهکار,تاریخ پرداخت,مبلغ,نوع پرداخت,توضیحات")
        list.forEach { v ->
            v.payments.sortedBy { it.date }.forEach { p ->
                paymentsText.appendLine(csvRow(v.debtor.name, p.date, p.amount, p.method, p.note))
            }
        }
        shareCsv(
            context,
            listOf(
                writeExport(context, "debtors_$stamp.csv", debtorsText.toString()),
                writeExport(context, "debtor_payments_$stamp.csv", paymentsText.toString())
            ),
            "ارسال لیست بدهکاران"
        )
    }

    fun exportSalesCsv(context: Context) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("تاریخ,کالا,تعداد,قیمت فروش واحد,جمع فروش,قیمت خرید واحد,سود,خریدار,شماره تماس,نوع پرداخت,توضیحات")
        sales.value.forEach { s ->
            sb.appendLine(
                csvRow(
                    s.date, s.productName, s.quantity, s.unitPrice, s.total, s.unitCost, s.profit,
                    s.buyerName, s.buyerPhone, if (s.credit) "نسیه" else "نقدی", s.note
                )
            )
        }
        shareCsv(context, listOf(writeExport(context, "sales_$stamp.csv", sb.toString())), "ارسال گزارش فروش")
    }

    private fun writeExport(context: Context, name: String, text: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name).apply { writeText(text, Charsets.UTF_8) }
    }

    private fun shareCsv(context: Context, files: List<File>, title: String) {
        val uris = ArrayList(files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    /** Numbers are written as-is (so Excel sums them); text is quoted and made formula-safe. */
    private fun csvRow(vararg cells: Any): String =
        cells.joinToString(",") { if (it is Number) it.toString() else csvEscape(it.toString()) }

    private fun csvEscape(s: String): String {
        // Prefix formula-like cells so Excel does not execute them, then quote.
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
