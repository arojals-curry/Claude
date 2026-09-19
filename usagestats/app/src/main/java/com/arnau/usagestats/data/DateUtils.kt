package com.arnau.usagestats.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Utilidades de fecha compartidas por el cálculo de stats y el motor de mood. */
object DateUtils {

    // SimpleDateFormat no es thread-safe: se crea una instancia por llamada.
    fun dateKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { timeInMillis = millis }.time)

    fun dateKey(cal: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    /** N días antes de ahora. */
    fun daysAgo(days: Int): Calendar =
        (Calendar.getInstance().clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -days) }

    /** N días antes del instante dado (para comparar contra la fecha propia de un snapshot, no contra "ahora"). */
    fun daysAgoFrom(millis: Long, days: Int): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, -days)
        }

    fun startOfDay(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    /** Redondea hacia abajo al múltiplo de 30 minutos anterior o igual. */
    fun floorToHalfHour(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.MINUTE, if (cal.get(Calendar.MINUTE) < 30) 0 else 30)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Franja de 30 min (0..47) a la que pertenece el instante, para comparar "misma hora" entre días distintos. */
    fun halfHourSlot(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 2 + if (cal.get(Calendar.MINUTE) < 30) 0 else 1
    }
}
