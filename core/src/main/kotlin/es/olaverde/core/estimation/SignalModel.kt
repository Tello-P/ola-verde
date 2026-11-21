package es.olaverde.core.estimation

/**
 * Modelo de un semáforo de tiempo fijo en una franja horaria.
 * [greenStartPhaseSec] = instante (mód. ciclo, sobre epoch) en que empieza el verde.
 * El rojo ocupa los [redSec] segundos anteriores a cada inicio de verde.
 */
data class SignalModel(
    val signalId: String,
    val cycleSec: Double,
    val greenStartPhaseSec: Double,
    val redSec: Double,
    /** 0..1: lo fiable que es la predicción. */
    val confidence: Double,
    val samples: Int,
) {
    val greenSec: Double get() = cycleSec - redSec

    /** Segundos hasta el próximo inicio de verde desde [tSec] (epoch s). */
    fun secondsToNextGreenStart(tSec: Double): Double {
        val d = (greenStartPhaseSec - tSec).mod(cycleSec)
        return if (d == 0.0) 0.0 else d
    }

    fun isGreen(tSec: Double): Boolean = secondsToNextGreenStart(tSec) !in 1e-9..redSec

    /** Espera si se llega en [tSec]. */
    fun waitSec(tSec: Double): Double {
        val d = secondsToNextGreenStart(tSec)
        return if (d in 1e-9..redSec) d else 0.0
    }

    /**
     * Espera esperada mezclando predicción y media según [confidence]:
     * con confianza baja se usa la espera media de llegada aleatoria (rojo² / 2·ciclo).
     */
    fun expectedWaitSec(tSec: Double): Double =
        confidence * waitSec(tSec) + (1 - confidence) * averageWaitSec(cycleSec, redSec)

    companion object {
        fun averageWaitSec(cycleSec: Double, redSec: Double) = redSec * redSec / (2 * cycleSec)

        /** Valor por defecto para semáforos sin datos: ciclo 90 s, rojo 45 s → ~11 s de espera media. */
        const val DEFAULT_UNKNOWN_WAIT_SEC = 45.0 * 45.0 / (2 * 90.0)
    }
}
