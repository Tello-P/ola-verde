package es.olaverde.core.estimation

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Franjas en las que los planes semafóricos suelen cambiar. */
enum class TimeBand {
    WEEKDAY_MORNING_PEAK, WEEKDAY_DAY, WEEKDAY_EVENING_PEAK, WEEKEND_DAY, NIGHT;

    companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

        fun of(tMillis: Long, zone: ZoneId = MADRID): TimeBand {
            val dt = Instant.ofEpochMilli(tMillis).atZone(zone)
            val h = dt.hour
            val weekend = dt.dayOfWeek == DayOfWeek.SATURDAY || dt.dayOfWeek == DayOfWeek.SUNDAY
            return when {
                h < 7 || h >= 22 -> NIGHT
                weekend -> WEEKEND_DAY
                h < 10 -> WEEKDAY_MORNING_PEAK
                h in 17..20 -> WEEKDAY_EVENING_PEAK
                else -> WEEKDAY_DAY
            }
        }
    }
}
