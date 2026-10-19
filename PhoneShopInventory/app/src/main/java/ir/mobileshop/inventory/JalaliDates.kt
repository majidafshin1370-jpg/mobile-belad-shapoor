package ir.mobileshop.inventory

import java.util.Calendar
import java.util.Date
import java.util.Locale

object JalaliDates {
    val jMonths = listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    val weekDays = listOf("شنبه","یک‌شنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنج‌شنبه","جمعه")
    val gMonths = listOf("January","February","March","April","May","June","July","August","September","October","November","December")
    private val faDigits = "۰۱۲۳۴۵۶۷۸۹"
    private fun fa(n: Int): String = n.toString().map { faDigits[it - '0'] }.joinToString("")

    /** Gregorian -> Jalali (Borkowski algorithm). */
    fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gDaysInMonth = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
        var jy = if (gy <= 1600) 0 else 979
        var gy2 = if (gy <= 1600) gy - 621 else gy - 1600
        val gy3 = if (gm > 2) gy2 + 1 else gy2
        var days = 365L*gy2 + (gy3+3)/4 - (gy3+99)/100 + (gy3+399)/400
        for (i in 0 until gm-1) days += gDaysInMonth[i]
        days += gd
        jy += 33*(days/12053); days %= 12053
        jy += 4*(days/1461); days %= 1461
        if (days > 365) { jy += (days-1)/365; days = (days-1)%365 }
        val jm = if (days < 186) 1 + days/31 else 7 + (days-186)/30
        val jd = 1 + (if (days < 186) days%31 else (days-186)%30)
        return intArrayOf(jy.toInt(), jm.toInt(), jd.toInt())
    }

    fun calendar(d: Date): Calendar = Calendar.getInstance().apply { time = d }

    /** «دوشنبه، ۲۸ مهر ۱۴۰۵» */
    fun jalaliString(d: Date = Date()): String {
        val c = calendar(d)
        val j = toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH))
        val wd = weekDays[(c.get(Calendar.DAY_OF_WEEK) + 1) % 7] // Calendar SUNDAY=1 -> شنبه index0
        return "$wd، ${fa(j[2])} ${jMonths[j[1]-1]} ${fa(j[0])}"
    }

    /** «19 October 2026» */
    fun gregorianString(d: Date = Date()): String {
        val c = calendar(d)
        return "${c.get(Calendar.DAY_OF_MONTH)} ${gMonths[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
    }

    fun todayLine(): String = jalaliString() + "  •  " + gregorianString()
}
