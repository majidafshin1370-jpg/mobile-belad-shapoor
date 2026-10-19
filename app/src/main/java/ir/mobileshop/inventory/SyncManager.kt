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
            val pushed = dao.getDirty()
            val body = JSONObject()
                .put("cursor", cursor)
                .put("changes", JSONArray(pushed.map { it.toJson() }))
            val response = withContext(Dispatchers.IO) { post("$serverUrl/sync", body.toString()) }
            val json = JSONObject(response)
            val pulled = json.getJSONArray("products").let { arr ->
                List(arr.length()) { arr.getJSONObject(it).toProduct() }
            }
            dao.applySync(pushed, pulled)
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
