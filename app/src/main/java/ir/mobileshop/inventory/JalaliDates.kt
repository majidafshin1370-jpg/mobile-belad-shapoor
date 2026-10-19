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
           if (j_day_no >= 365) {
               jy += j_day_no / 365
               j_day_no %= 365
           }
           var jm = 0
           while (jm < 11 && j_day_no >= j_days_in_month[jm]) {
               j_day_no -= j_days_in_month[jm]
               jm++
           }
           val jd = j_day_no + 1
           return intArrayOf(jy, jm + 1, jd)
       }
   }
