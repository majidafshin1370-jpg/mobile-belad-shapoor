package ir.mobileshop.inventory

import java.util.Calendar
import java.util.GregorianCalendar

object JalaliDates {
    fun getShamsiToday(): String {
        val cal = GregorianCalendar()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val j = g2j(gy, gm, gd)
        return String.format("%04d/%02d/%02d", j[0], j[1], j[2])
    }

    /** Shamsi date (yyyy/MM/dd) of a moment in time. */
    fun shamsiOf(millis: Long): String {
        val cal = GregorianCalendar().apply { timeInMillis = millis }
        val j = g2j(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return String.format("%04d/%02d/%02d", j[0], j[1], j[2])
    }

    private fun g2j(gy: Int, gm: Int, gd: Int): IntArray {
        val g_days_in_month = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val j_days_in_month = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var gy = gy - 1600
        var gm = gm - 1
        var gd = gd - 1
        var g_day_no = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) g_day_no += g_days_in_month[i]
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) g_day_no++
        g_day_no += gd
        var j_day_no = g_day_no - 79
        val j_np = j_day_no / 12053
        j_day_no %= 12053
        var jy = 979 + 33 * j_np + 4 * (j_day_no / 1461)
        j_day_no %= 1461
        if (j_day_no >= 366) {
            jy += (j_day_no - 1) / 365
            j_day_no = (j_day_no - 1) % 365
        }
        var jm = 0
        while (jm < 11 && j_day_no >= j_days_in_month[jm]) {
            j_day_no -= j_days_in_month[jm]
            jm++
        }
        val jd = j_day_no + 1
        return intArrayOf(jy, jm + 1, jd)
    }

    private fun toLatinDigits(s: String): String = s.map {
        when (it) {
            in '۰'..'۹' -> '0' + (it - '۰')
            in '٠'..'٩' -> '0' + (it - '٠')
            else -> it
        }
    }.joinToString("")

    /** Reads "1405/08/15" (Persian digits and / - . separators allowed) into year, month, day. */
    fun parseShamsi(text: String): IntArray? {
        val parts = toLatinDigits(text.trim()).split('/', '-', '.', '٫').map { it.trim() }
        if (parts.size != 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y !in 1300..1700 || m !in 1..12 || d < 1) return null
        if (d > (if (m <= 6) 31 else if (m <= 11) 30 else 30)) return null
        return intArrayOf(y, m, d)
    }

    /** Writes the date in the fixed form yyyy/MM/dd, or null when the text is not a valid date. */
    fun normalizeShamsi(text: String): String? =
        parseShamsi(text)?.let { String.format("%04d/%02d/%02d", it[0], it[1], it[2]) }

    private fun j2g(jy: Int, jm: Int, jd: Int): IntArray {
        val g_days_in_month = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val j_days_in_month = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        val jy2 = jy - 979
        val jm2 = jm - 1
        val jd2 = jd - 1
        var j_day_no = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
        for (i in 0 until jm2) j_day_no += j_days_in_month[i]
        j_day_no += jd2
        var g_day_no = j_day_no + 79
        var gy = 1600 + 400 * (g_day_no / 146097)
        g_day_no %= 146097
        var leap = true
        if (g_day_no >= 36525) {
            g_day_no--
            gy += 100 * (g_day_no / 36524)
            g_day_no %= 36524
            if (g_day_no >= 365) g_day_no++ else leap = false
        }
        gy += 4 * (g_day_no / 1461)
        g_day_no %= 1461
        if (g_day_no >= 366) {
            leap = false
            g_day_no--
            gy += g_day_no / 365
            g_day_no %= 365
        }
        var i = 0
        while (g_day_no >= g_days_in_month[i] + (if (i == 1 && leap) 1 else 0)) {
            g_day_no -= g_days_in_month[i] + (if (i == 1 && leap) 1 else 0)
            i++
        }
        return intArrayOf(gy, i + 1, g_day_no + 1)
    }

    /** Days from today until the given Shamsi date (0 = today, negative = already past); null if invalid. */
    fun daysUntil(shamsi: String): Int? {
        val p = parseShamsi(shamsi) ?: return null
        val g = j2g(p[0], p[1], p[2])
        val target = GregorianCalendar(g[0], g[1] - 1, g[2])
        val now = GregorianCalendar()
        val today = GregorianCalendar(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
        )
        return Math.round((target.timeInMillis - today.timeInMillis) / 86_400_000.0).toInt()
    }
}
