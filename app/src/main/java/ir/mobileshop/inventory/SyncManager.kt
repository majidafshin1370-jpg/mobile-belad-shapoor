package ir.mobileshop.inventory

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface SyncStatus {
    data object NotConfigured : SyncStatus
    data object Syncing : SyncStatus
    data class Ok(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** The server accepts at most 1000 changes per list; the rest goes in the next round. */
private const val MAX_PUSH = 500

class SyncManager(context: Context, private val dao: ProductDao) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    var serverUrl: String
        get() = prefs.getString("url", "") ?: ""
        set(v) = prefs.edit().putString("url", v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    private var cursor: Long
        get() = prefs.getLong("cursor", 0L)
        set(v) = prefs.edit().putLong("cursor", v).apply()

    val isConfigured: Boolean get() = serverUrl.isNotEmpty() && token.isNotEmpty()

    /** Called when the person points the app at a different server: upload everything again. */
    suspend fun resetForNewServer() {
        cursor = 0L
        dao.markAllDirty()
    }

    /** Uploads local changes and downloads what other phones changed. Safe to call any time. */
    suspend fun sync(): SyncStatus = mutex.withLock {
        if (!isConfigured) return SyncStatus.NotConfigured
        if (!serverUrl.startsWith("https://")) {
            return SyncStatus.Failed("آدرس سرور باید با https:// شروع شود")
        }
        try {
            val pushedProducts = dao.getDirty().take(MAX_PUSH)
            val pushedDebtors = dao.getDirtyDebtors().take(MAX_PUSH)
            val pushedPayments = dao.getDirtyPayments().take(MAX_PUSH)
            val pushedSales = dao.getDirtySales().take(MAX_PUSH)
            val body = JSONObject()
                .put("cursor", cursor)
                .put("changes", JSONArray(pushedProducts.map { it.toJson() }))
                .put("debtors", JSONArray(pushedDebtors.map { it.toJson() }))
                .put("payments", JSONArray(pushedPayments.map { it.toJson() }))
                .put("sales", JSONArray(pushedSales.map { it.toJson() }))
            val response = withContext(Dispatchers.IO) { post("$serverUrl/sync", body.toString()) }
            val json = JSONObject(response)
            dao.applySync(
                pushedProducts, json.list("products") { it.toProduct() },
                pushedDebtors, json.list("debtors") { it.toDebtor() },
                pushedPayments, json.list("payments") { it.toPayment() },
                pushedSales, json.list("sales") { it.toSale() }
            )
            cursor = json.getLong("cursor")
            SyncStatus.Ok(System.currentTimeMillis())
        } catch (e: AuthException) {
            SyncStatus.Failed("توکن اشتباه است")
        } catch (e: IOException) {
            SyncStatus.Failed("اتصال به سرور برقرار نشد (آفلاین)")
        } catch (e: Exception) {
            SyncStatus.Failed("خطا در همگام‌سازی")
        }
    }

    private class AuthException : Exception()

    private fun post(url: String, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 401) throw AuthException()
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

private fun Product.toJson() = JSONObject()
    .put("uuid", uuid).put("category", category).put("brand", brand).put("model", model)
    .put("color", color).put("storage", storage).put("imei", imei)
    .put("buyPrice", buyPrice).put("sellPrice", sellPrice).put("quantity", quantity)
    .put("note", note).put("createdAt", createdAt).put("updatedAt", updatedAt)
    .put("deleted", deleted)

private fun JSONObject.toProduct() = Product(
    uuid = getString("uuid"),
    category = optString("category"),
    brand = optString("brand"),
    model = optString("model"),
    color = optString("color"),
    storage = optString("storage"),
    imei = optString("imei"),
    buyPrice = optLong("buyPrice"),
    sellPrice = optLong("sellPrice"),
    quantity = optInt("quantity"),
    note = optString("note"),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt"),
    deleted = optBoolean("deleted"),
    dirty = false
)

/** Reads an optional JSON array (an old server does not send the newer lists). */
private fun <T> JSONObject.list(key: String, convert: (JSONObject) -> T): List<T> {
    val arr = optJSONArray(key) ?: return emptyList()
    return List(arr.length()) { convert(arr.getJSONObject(it)) }
}

private fun Debtor.toJson() = JSONObject()
    .put("id", id).put("name", name).put("phone", phone).put("amount", amount)
    .put("note", note).put("createdAt", createdAt).put("updatedAt", updatedAt)
    .put("purchaseDate", purchaseDate).put("dueDate", dueDate)
    .put("lastReminderDue", lastReminderDue).put("closed", closed).put("deleted", deleted)

private fun JSONObject.toDebtor() = Debtor(
    id = getString("id"),
    name = optString("name"),
    phone = optString("phone"),
    amount = optLong("amount"),
    note = optString("note"),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt"),
    purchaseDate = optString("purchaseDate"),
    dueDate = optString("dueDate"),
    lastReminderDue = optString("lastReminderDue"),
    closed = optBoolean("closed"),
    deleted = optBoolean("deleted"),
    dirty = false
)

private fun Payment.toJson() = JSONObject()
    .put("id", id).put("debtorId", debtorId).put("saleId", saleId).put("amount", amount)
    .put("date", date).put("method", method).put("note", note)
    .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

private fun JSONObject.toPayment() = Payment(
    id = getString("id"),
    debtorId = optString("debtorId"),
    saleId = optString("saleId"),
    amount = optLong("amount"),
    date = optString("date"),
    method = optString("method"),
    note = optString("note"),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt"),
    deleted = optBoolean("deleted"),
    dirty = false
)

private fun Sale.toJson() = JSONObject()
    .put("id", id).put("productId", productId).put("productName", productName)
    .put("quantity", quantity).put("unitPrice", unitPrice).put("unitCost", unitCost)
    .put("buyerName", buyerName).put("buyerPhone", buyerPhone).put("date", date)
    .put("credit", credit).put("debtorId", debtorId).put("creditAmount", creditAmount)
    .put("note", note).put("createdAt", createdAt).put("updatedAt", updatedAt)
    .put("deleted", deleted)

private fun JSONObject.toSale() = Sale(
    id = getString("id"),
    productId = optString("productId"),
    productName = optString("productName"),
    quantity = optInt("quantity"),
    unitPrice = optLong("unitPrice"),
    unitCost = optLong("unitCost"),
    buyerName = optString("buyerName"),
    buyerPhone = optString("buyerPhone"),
    date = optString("date"),
    credit = optBoolean("credit"),
    debtorId = optString("debtorId"),
    creditAmount = optLong("creditAmount"),
    note = optString("note"),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt"),
    deleted = optBoolean("deleted"),
    dirty = false
)
