package ir.mobileshop.inventory

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/** Small, dependency-free Jalali/Gregorian date converter. */
object JalaliDates {
    data class DateParts(val year: Int, val month: Int, val day: Int)
    private val gMonthDays = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
    private val jMonthDays = intArrayOf(31,31,31,31,31,31,30,30,30,30,30,29)

    fun fromGregorian(year: Int, month: Int, day: Int): DateParts {
        var gy = year - 1600; var gm = month - 1; val gd = day - 1
        var gDayNo = 365*gy + (gy+3)/4 - (gy+99)/100 + (gy+399)/400
        for (i in 0 until gm) gDayNo += gMonthDays[i]
        if (gm > 1 && isGregorianLeap(year)) gDayNo++
        gDayNo += gd
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053; jDayNo %= 12053
        var jy = 979 + 33*jNp + 4*(jDayNo/1461); jDayNo %= 1461
        if (jDayNo >= 366) { jy += (jDayNo-1)/365; jDayNo = (jDayNo-1)%365 }
        var jm = 0
        while (jm < 11 && jDayNo >= jMonthDays[jm]) { jDayNo -= jMonthDays[jm]; jm++ }
        return DateParts(jy, jm+1, jDayNo+1)
    }

    fun toGregorian(year: Int, month: Int, day: Int): DateParts {
        require(month in 1..12 && day >= 1 && day <= jMonthDays[month-1])
        var jy = year - 979; var jDayNo = 365*jy + (jy/33)*8 + ((jy%33+3)/4)
        for (i in 0 until month-1) jDayNo += jMonthDays[i]
        jDayNo += day-1
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400*(gDayNo/146097); gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) { gDayNo--; gy += 100*(gDayNo/36524); gDayNo %= 36524; if (gDayNo >= 365) { gDayNo++; } else { leap = false } }
        gy += 4*(gDayNo/1461); gDayNo %= 1461
        if (gDayNo >= 366) { leap = false; gDayNo--; gy += gDayNo/365; gDayNo %= 365 }
        var gm = 0
        while (gm < 11) { val days = gMonthDays[gm] + if (gm == 1 && leap) 1 else 0; if (gDayNo < days) break; gDayNo -= days; gm++ }
        return DateParts(gy, gm+1, gDayNo+1)
    }

    fun today(): DateParts = Calendar.getInstance().let { fromGregorian(it.get(Calendar.YEAR), it.get(Calendar.MONTH)+1, it.get(Calendar.DAY_OF_MONTH)) }
    fun format(parts: DateParts): String = "%04d/%02d/%02d".format(Locale.US, parts.year, parts.month, parts.day)
    fun formatGregorian(date: Date = Date()): String = GregorianCalendar().apply { time = date }.let { "%04d/%02d/%02d".format(Locale.US, it.get(Calendar.YEAR), it.get(Calendar.MONTH)+1, it.get(Calendar.DAY_OF_MONTH)) }
}
