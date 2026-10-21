package ir.mobileshop.inventory

import android.content.Context
import android.os.Build

/** Small settings that belong to this phone only. */
object ShopPrefs {
    private const val FILE = "shop"

    /** Name written in the change log; falls back to the phone model so entries are never anonymous. */
    fun userName(c: Context): String {
        val saved = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("userName", "") ?: ""
        return saved.trim().ifEmpty { Build.MODEL ?: "گوشی" }
    }

    fun savedUserName(c: Context): String =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("userName", "") ?: ""

    fun setUserName(c: Context, v: String) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("userName", v.trim().take(40)).apply()
}
