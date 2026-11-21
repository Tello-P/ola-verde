package es.olaverde.core.advice

/** Frases cortas para leer en voz alta. Nunca piden superar el límite. */
object VoicePrompts {
    fun greenWave(a: GreenWaveAdvice): String {
        val n = a.greensInRow
        val sem = if (n == 1) "el próximo semáforo" else "los próximos $n semáforos"
        return "Mantén ${easySpeed(a)} kilómetros por hora para pillar $sem en verde."
    }

    fun noWave(): String = "Ningún ritmo dentro del límite pilla el próximo semáforo en verde. Conduce con calma."

    fun route(minutesSaved: Int, extraKm: Double): String = when {
        minutesSaved <= 0 -> "La ruta más corta es también la más rápida."
        extraKm > 0.05 -> "Ruta con menos semáforos: ahorras unos $minutesSaved minutos aunque sean ${"%.1f".format(extraKm).replace('.', ',')} kilómetros más."
        else -> "Ruta con menos esperas: ahorras unos $minutesSaved minutos."
    }

    /** Múltiplo de 5 más cercano a la recomendación sin salirse del rango válido (fácil de seguir). */
    fun easySpeed(a: GreenWaveAdvice): Int =
        (a.minKmh..a.maxKmh).filter { it % 5 == 0 }.minByOrNull { kotlin.math.abs(it - a.speedKmh) } ?: a.speedKmh
}
