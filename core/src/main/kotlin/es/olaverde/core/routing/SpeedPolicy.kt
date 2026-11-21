package es.olaverde.core.routing

/**
 * Velocidad de cálculo por tramo. Nunca supera el límite legal:
 * - tramo con radar → velocidad del radar (acotada al límite);
 * - si no → la velocidad que elige el usuario para ese límite, acotada al límite;
 * - si hay velocidad real observada menor (tráfico), se usa esa.
 */
class SpeedPolicy(preferredKmhByLimit: Map<Int, Double> = emptyMap()) {
    private val preferred: Map<Int, Double> =
        preferredKmhByLimit.mapValues { (limit, v) -> v.coerceIn(1.0, limit.toDouble()) }

    fun speedKmh(edge: Edge): Double {
        val limit = edge.limitKmh.toDouble()
        val base = edge.radarKmh?.toDouble()?.coerceAtMost(limit) ?: (preferred[edge.limitKmh] ?: limit)
        return edge.observedKmh?.let { minOf(it, base) } ?: base
    }

    fun travelSec(edge: Edge): Double = edge.lengthM / (speedKmh(edge) / 3.6)
}
