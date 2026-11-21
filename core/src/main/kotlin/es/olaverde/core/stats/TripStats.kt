package es.olaverde.core.stats

import es.olaverde.core.geo.Geo
import es.olaverde.core.model.GpsPoint

/** Resumen de un viaje para mostrarlo en la app. */
data class TripStats(
    val startMillis: Long,
    val durationSec: Long,
    val distanceM: Double,
    /** Paradas de al menos [MIN_STOP_SEC] segundos. */
    val stops: Int,
    val stoppedSec: Long,
) {
    val avgSpeedKmh: Double get() = if (durationSec == 0L) 0.0 else distanceM / durationSec * 3.6

    companion object {
        private const val STOP_SPEED_MPS = 1.0
        const val MIN_STOP_SEC = 5L

        fun of(points: List<GpsPoint>): TripStats? {
            if (points.size < 2) return null
            var distance = 0.0
            var stops = 0
            var stopped = 0L
            var stopStart: Long? = null
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                distance += Geo.distanceM(a.pos, b.pos)
                val dt = (b.tMillis - a.tMillis) / 1000.0
                val v = b.speedMps ?: if (dt > 0) Geo.distanceM(a.pos, b.pos) / dt else 0.0
                if (v < STOP_SPEED_MPS) {
                    if (stopStart == null) stopStart = a.tMillis
                } else if (stopStart != null) {
                    val len = (b.tMillis - stopStart) / 1000
                    if (len >= MIN_STOP_SEC) { stops++; stopped += len }
                    stopStart = null
                }
            }
            stopStart?.let {
                val len = (points.last().tMillis - it) / 1000
                if (len >= MIN_STOP_SEC) { stops++; stopped += len }
            }
            val duration = (points.last().tMillis - points.first().tMillis) / 1000
            return TripStats(points.first().tMillis, duration, distance, stops, stopped)
        }
    }
}
