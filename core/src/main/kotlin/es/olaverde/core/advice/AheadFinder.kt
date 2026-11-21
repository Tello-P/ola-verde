package es.olaverde.core.advice

import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon

/** Semáforo conocido con su modelo y el límite de la calle que llega a él. */
data class KnownSignal(val id: String, val pos: LatLon, val model: SignalModel?, val approachLimitKmh: Int?)

/**
 * Sin ruta fijada, "por delante" = semáforos en un cono alrededor del rumbo actual.
 * Devuelve los que tienen modelo, ordenados por distancia, y el límite más bajo del tramo.
 */
object AheadFinder {
    fun find(
        pos: LatLon,
        bearingDeg: Double,
        signals: List<KnownSignal>,
        maxDistanceM: Double = 600.0,
        coneDeg: Double = 30.0,
        defaultLimitKmh: Int = 50,
    ): Pair<List<SignalAhead>, Int> {
        val ahead = signals.mapNotNull { s ->
            val model = s.model ?: return@mapNotNull null
            val d = Geo.distanceM(pos, s.pos)
            if (d < 10 || d > maxDistanceM) return@mapNotNull null
            if (Geo.angleDiff(Geo.bearingDeg(pos, s.pos), bearingDeg) > coneDeg) return@mapNotNull null
            Triple(SignalAhead(d, model), s, d)
        }.sortedBy { it.third }
        val limit = ahead.mapNotNull { it.second.approachLimitKmh }.minOrNull() ?: defaultLimitKmh
        return ahead.map { it.first } to limit
    }
}
