package es.olaverde.core.detection

import es.olaverde.core.geo.Geo
import es.olaverde.core.model.GpsPoint
import es.olaverde.core.model.SignalObservation
import es.olaverde.core.model.TrafficSignal

/**
 * Convierte una traza GPS en observaciones de semáforo.
 *
 * Regla: parado (< [stopSpeedMps]) durante al menos [minStopMillis] en los [approachRadiusM]
 * anteriores al semáforo = rojo; pasar por su lado sin parar = verde.
 */
class SignalEventDetector(
    private val approachRadiusM: Double = 60.0,
    private val passRadiusM: Double = 20.0,
    private val stopSpeedMps: Double = 3.0 / 3.6,
    private val minStopMillis: Long = 3_000,
    /** Paradas más largas no se atribuyen al semáforo (atasco, aparcar...). */
    private val maxStopMillis: Long = 180_000,
    /** Separación media entre coches en cola (m) y retraso de arranque por coche (ms). */
    private val queueSpacingM: Double = 7.0,
    private val queueDelayPerCarMillis: Long = 2_000,
) {

    fun detect(trace: List<GpsPoint>, signals: List<TrafficSignal>): List<SignalObservation> {
        if (trace.size < 2) return emptyList()
        val speeds = speedsOf(trace)
        val out = mutableListOf<SignalObservation>()
        for (signal in signals) {
            val dist = trace.map { Geo.distanceM(it.pos, signal.pos) }
            var i = 0
            while (i < trace.size) {
                if (dist[i] > approachRadiusM) { i++; continue }
                // Ventana de aproximación: puntos seguidos dentro del radio.
                val start = i
                while (i < trace.size && dist[i] <= approachRadiusM) i++
                val end = i - 1
                val minDist = (start..end).minOf { dist[it] }
                if (minDist > passRadiusM) continue // pasa cerca pero no por el cruce
                // Último punto a distancia mínima: si estuvo parado ahí, es el momento justo antes de cruzar.
                val closest = (start..end).last { dist[it] <= minDist + 1.0 }
                out += analyseApproach(signal, trace, speeds, dist, start, end, closest)
            }
        }
        return out.sortedBy { obsTime(it) }
    }

    private fun analyseApproach(
        signal: TrafficSignal,
        trace: List<GpsPoint>,
        speeds: DoubleArray,
        dist: List<Double>,
        start: Int,
        end: Int,
        closest: Int,
    ): List<SignalObservation> {
        // Buscar la última parada que empieza antes de cruzar.
        var stopStart = -1
        var stopEnd = -1
        var k = start
        while (k <= closest) {
            if (speeds[k] < stopSpeedMps) {
                val s = k
                while (k <= end && speeds[k] < stopSpeedMps) k++
                stopStart = s
                stopEnd = k - 1
            } else k++
        }
        if (stopStart >= 0) {
            val from = trace[stopStart].tMillis
            val to = trace[minOf(stopEnd + 1, trace.size - 1)].tMillis
            val duration = to - from
            if (duration in minStopMillis..maxStopMillis) {
                val carsAhead = (dist[stopEnd] / queueSpacingM).toInt()
                val greenStart = to - carsAhead * queueDelayPerCarMillis
                return listOf(
                    SignalObservation.RedInterval(signal.id, from, greenStart),
                    SignalObservation.GreenStart(signal.id, greenStart),
                )
            }
            if (duration > maxStopMillis) return emptyList()
        }
        return listOf(SignalObservation.GreenAt(signal.id, trace[closest].tMillis))
    }

    private fun speedsOf(trace: List<GpsPoint>): DoubleArray = DoubleArray(trace.size) { idx ->
        trace[idx].speedMps ?: run {
            val a = trace[maxOf(idx - 1, 0)]
            val b = trace[minOf(idx + 1, trace.size - 1)]
            val dt = (b.tMillis - a.tMillis) / 1000.0
            if (dt <= 0) 0.0 else Geo.distanceM(a.pos, b.pos) / dt
        }
    }

    private fun obsTime(o: SignalObservation) = when (o) {
        is SignalObservation.GreenStart -> o.tMillis
        is SignalObservation.GreenAt -> o.tMillis
        is SignalObservation.RedInterval -> o.fromMillis
    }
}
