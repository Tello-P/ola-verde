package es.olaverde.core.advice

import es.olaverde.core.estimation.SignalModel

/** Semáforo por delante a [distanceM] metros. */
data class SignalAhead(val distanceM: Double, val model: SignalModel)

data class GreenWaveAdvice(
    /** Velocidad recomendada (centro del rango válido). */
    val speedKmh: Int,
    val minKmh: Int,
    val maxKmh: Int,
    /** Semáforos seguidos que se pillan en verde a esa velocidad. */
    val greensInRow: Int,
    val signalsAhead: Int,
)

/**
 * Busca una velocidad constante entre [minKmh] y el límite con la que se encadenan
 * más semáforos en verde. Nunca recomienda superar el límite.
 */
class GreenWaveAdvisor(private val minKmh: Int = 15, private val minConfidence: Double = 0.6) {

    fun advise(nowSec: Double, limitKmh: Int, ahead: List<SignalAhead>): GreenWaveAdvice? {
        val signals = ahead.sortedBy { it.distanceM }.takeWhile { it.model.confidence >= minConfidence }
        if (signals.isEmpty() || limitKmh < minKmh) return null

        val counts = (minKmh..limitKmh).associateWith { v ->
            signals.takeWhile { it.model.isGreen(nowSec + it.distanceM / (v / 3.6)) }.size
        }
        val bestCount = counts.values.max()
        if (bestCount == 0) return null

        // Rango contiguo más ancho de velocidades con el mejor resultado; se prefiere el más rápido.
        val good = counts.filter { it.value == bestCount }.keys.sorted()
        val ranges = mutableListOf<IntRange>()
        var s = good.first()
        var p = s
        for (v in good.drop(1)) {
            if (v != p + 1) { ranges += s..p; s = v }
            p = v
        }
        ranges += s..p
        val range = ranges.maxWith(compareBy<IntRange> { it.last - it.first }.thenBy { it.last })
        return GreenWaveAdvice((range.first + range.last) / 2, range.first, range.last, bestCount, signals.size)
    }
}
