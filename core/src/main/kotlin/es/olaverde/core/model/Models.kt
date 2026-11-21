package es.olaverde.core.model

import es.olaverde.core.geo.LatLon

/** Muestra GPS. [tMillis] en epoch ms, [speedMps] en m/s si el GPS la da. */
data class GpsPoint(val tMillis: Long, val pos: LatLon, val speedMps: Double? = null)

data class TrafficSignal(val id: String, val pos: LatLon)

/** Observaciones que se extraen de una traza al pasar por un semáforo. */
sealed interface SignalObservation {
    val signalId: String

    /** El coche arrancó tras estar parado: el verde empezó en [tMillis] (ya corregido por cola). */
    data class GreenStart(override val signalId: String, val tMillis: Long) : SignalObservation

    /** El coche pasó sin pararse: estaba en verde en [tMillis]. */
    data class GreenAt(override val signalId: String, val tMillis: Long) : SignalObservation

    /** El coche estuvo parado en rojo entre [fromMillis] y [toMillis]. */
    data class RedInterval(override val signalId: String, val fromMillis: Long, val toMillis: Long) : SignalObservation
}
