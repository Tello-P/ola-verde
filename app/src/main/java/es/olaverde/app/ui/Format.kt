package es.olaverde.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")

/** 05:07 o 1:05:07 */
fun clock(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** 12 min o 1 h 05 min */
fun duration(sec: Long): String {
    val m = (sec + 30) / 60
    return if (m < 60) "$m min" else "${m / 60} h %02d min".format(m % 60)
}

fun km(meters: Double): String = String.format(ES, "%.1f", meters / 1000)

fun tripDate(millis: Long): String =
    SimpleDateFormat("EEEE d MMM · HH:mm", ES).format(Date(millis)).replaceFirstChar { it.uppercase(ES) }
