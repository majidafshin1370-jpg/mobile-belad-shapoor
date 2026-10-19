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

enum class SortOrder(val title: String) {
    NEWEST("جدیدترین"), OLDEST("قدیمی‌ترین"), HIGHEST_PRICE("بالاترین قیمت"),
    LOWEST_PRICE("پایین‌ترین قیمت"), HIGHEST_STOCK("بیشترین موجودی")
}
val PHONE_BRANDS = listOf("Samsung", "Apple", "Xiaomi", "Nokia", "Huawei", "Honor", "Oppo", "همه برندها")
private const val AUTO_SYNC_MS = 60_000L

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).productDao()
    val sync = SyncManager(app, dao)

    val query = MutableStateFlow("")
    val brandFilter = MutableStateFlow<String?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val debtors: StateFlow<List<Debtor>> = dao.observeDebtors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveDebtor(debtor: Debtor) = viewModelScope.launch { dao.upsertDebtor(debtor.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteDebtor(debtor: Debtor) = viewModelScope.launch { dao.deleteDebtor(debtor.id) }

    val categoryFilter = MutableStateFlow<String?>(null)
    val syncStatus = MutableStateFlow<SyncStatus>(
        if (sync.isConfigured) SyncStatus.Ok(0L) else SyncStatus.NotConfigured
    )

    val allProducts: StateFlow<List<Product>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<Product>> =
        combine(allProducts, query, categoryFilter, brandFilter, sortOrder) { list, q, cat, brand, sort ->
            val s = q.trim()
            list.filter { p ->
                (cat == null || p.category == cat) &&
                    (brand == null || p.brand.equals(brand, true)) &&
                    (s.isEmpty() ||
                        p.brand.contains(s, true) ||
                        p.model.contains(s, true) ||
                        p.imei.contains(s) ||
                        p.color.contains(s, true))
            }.let { items -> when (sort) {
                SortOrder.NEWEST -> items.sortedByDescending { it.createdAt }
                SortOrder.OLDEST -> items.sortedBy { it.createdAt }
                SortOrder.HIGHEST_PRICE -> items.sortedByDescending { it.sellPrice }
                SortOrder.LOWEST_PRICE -> items.sortedBy { it.sellPrice }
                SortOrder.HIGHEST_STOCK -> items.sortedByDescending { it.quantity }
            } }
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

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ارسال گزارش موجودی"))
    }

    private fun csvEscape(s: String): String {
        // Prefix formula-like cells so Excel does not execute them, then quote.
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
