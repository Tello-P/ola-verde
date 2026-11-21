package es.olaverde.core.estimation

import es.olaverde.core.model.SignalObservation
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Estima ciclo, desfase y duración del rojo de un semáforo de tiempo fijo
 * a partir de observaciones de una misma franja horaria.
 *
 * Ciclo: se prueba cada candidato y se mide cuánto se agrupan las fases de los inicios
 * de verde (longitud del vector medio circular). Los divisores del ciclo real también
 * agrupan, así que se escoge el candidato más largo cuya puntuación está cerca del máximo.
 */
class CycleEstimator(
    private val minCycleSec: Int = 40,
    private val maxCycleSec: Int = 150,
    private val minGreenStarts: Int = 3,
    private val tolerance: Double = 0.03,
) {

    fun estimate(signalId: String, observations: List<SignalObservation>): SignalModel? {
        val obs = observations.filter { it.signalId == signalId }
        val starts = obs.filterIsInstance<SignalObservation.GreenStart>().map { it.tMillis / 1000.0 }
        if (starts.size < minGreenStarts) return null

        val scores = (minCycleSec..maxCycleSec).associateWith { c -> resultant(starts, c.toDouble()).first }
        val best = scores.values.max()
        val cycle = scores.filter { it.value >= best - tolerance }.keys.max().toDouble()
        val (r, meanPhase) = resultant(starts, cycle)

        val reds = obs.filterIsInstance<SignalObservation.RedInterval>()
            .map { (it.toMillis - it.fromMillis) / 1000.0 }
        val greensAt = obs.filterIsInstance<SignalObservation.GreenAt>().map { it.tMillis / 1000.0 }

        // El rojo observado es una cota inferior; el verde observado lo acota por arriba.
        var red = (reds.maxOrNull() ?: (cycle / 2)).coerceAtMost(cycle - 5)
        val probe = SignalModel(signalId, cycle, meanPhase, red, 1.0, 0)
        val greenLimits = greensAt.map { probe.secondsToNextGreenStart(it) }.filter { it < red }
        val contradictions = greenLimits.size
        if (greenLimits.isNotEmpty() && reds.isEmpty()) red = greenLimits.min()

        val total = starts.size + greensAt.size
        val consistency = 1.0 - contradictions.toDouble() / total
        val sampleFactor = (total / 10.0).coerceAtMost(1.0)
        val confidence = (r * consistency * sampleFactor).coerceIn(0.0, 1.0)

        return SignalModel(signalId, cycle, meanPhase, red, confidence, total)
    }

    /** (longitud del vector medio, fase media en s) de [times] módulo [cycle]. */
    private fun resultant(times: List<Double>, cycle: Double): Pair<Double, Double> {
        var x = 0.0
        var y = 0.0
        for (t in times) {
            val a = 2 * PI * (t.mod(cycle)) / cycle
            x += cos(a); y += sin(a)
        }
        val r = hypot(x, y) / times.size
        val phase = (atan2(y, x) / (2 * PI) * cycle).mod(cycle)
        return r to phase
    }
}
