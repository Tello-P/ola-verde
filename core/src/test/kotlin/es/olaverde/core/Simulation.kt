package es.olaverde.core

import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon
import es.olaverde.core.model.GpsPoint

/** Simula un coche que va hacia el norte a [speedMps], cruza [signalPos] y respeta el semáforo [truth]. */
object Simulation {
    val VALLADOLID = LatLon(41.6523, -4.7245)

    fun trace(
        truth: SignalModel,
        startSec: Long,
        signalPos: LatLon = VALLADOLID,
        startDistanceM: Double = 200.0,
        speedMps: Double = 10.0,
        stopLineM: Double = 5.0,
    ): List<GpsPoint> {
        val points = mutableListOf<GpsPoint>()
        var t = startSec
        var d = -startDistanceM // posición relativa al semáforo (negativa = antes)
        val line = -stopLineM
        while (d < 100) {
            val waiting = d == line && truth.waitSec(t.toDouble()) > 0
            val v = if (waiting) 0.0 else speedMps
            points += GpsPoint(t * 1000, Geo.offsetNorth(signalPos, d), v)
            if (!waiting) {
                val next = d + v
                d = if (d < line && next > line) line else next // el coche siempre llega a la línea
            }
            t += 1
        }
        return points
    }
}
