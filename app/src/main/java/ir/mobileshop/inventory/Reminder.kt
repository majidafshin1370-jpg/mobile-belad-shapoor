package ir.mobileshop.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Settings for the automatic SMS reminders, kept on this phone. */
object ReminderPrefs {
    private const val FILE = "reminder"

    fun enabled(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("enabled", v).apply()

    fun daysBefore(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("days", 3)
    fun setDaysBefore(c: Context, v: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("days", v).apply()
}

fun hasSmsPermission(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

/** Runs every few hours in the background (even when the app is closed) and texts debtors whose due date is near. */
class DebtReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ReminderPrefs.enabled(ctx) || !hasSmsPermission(ctx)) return Result.success()

        // No messages at night; the next run will pick them up.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 21) return Result.success()

        val dao = AppDatabase.get(ctx).productDao()
        val window = ReminderPrefs.daysBefore(ctx)
        for (d in dao.getDebtors()) {
            if (d.amount <= 0 || d.phone.isBlank() || d.dueDate.isBlank()) continue
            if (d.lastReminderDue == d.dueDate) continue // already reminded for this due date
            val left = JalaliDates.daysUntil(d.dueDate) ?: continue
            if (left < 0 || left > window) continue
            if (sendSms(ctx, d, left)) {
                dao.upsertDebtor(d.copy(lastReminderDue = d.dueDate))
            }
        }
        return Result.success()
    }

    private fun sendSms(ctx: Context, d: Debtor, left: Int): Boolean = try {
        val whenText = when (left) {
            0 -> "امروز"
            1 -> "فردا"
            else -> "${left.faNum()} روز دیگر"
        }
        val text = "${d.name} عزیز، یادآوری از موبایل بلاد شاپور: " +
            "مبلغ ${d.amount.faMoney()} بابت خرید ${d.purchaseDate.fa()} " +
            "$whenText (${d.dueDate.fa()}) موعد تسویه حساب است. با تشکر"
        val sms = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val number = d.phone.map {
            when (it) {
                in '۰'..'۹' -> '0' + (it - '۰')
                in '٠'..'٩' -> '0' + (it - '٠')
                else -> it
            }
        }.joinToString("").filter { it.isDigit() || it == '+' }
        sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
        true
    } catch (e: Exception) {
        false
    }
}

fun scheduleDebtReminders(context: Context) {
    val request = PeriodicWorkRequestBuilder<DebtReminderWorker>(3, TimeUnit.HOURS).build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("debt-reminders", ExistingPeriodicWorkPolicy.KEEP, request)
}
