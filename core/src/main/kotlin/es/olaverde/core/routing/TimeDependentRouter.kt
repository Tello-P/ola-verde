package es.olaverde.core.routing

import es.olaverde.core.estimation.SignalModel
import java.util.PriorityQueue

/** Espera esperada (s) en un semáforo si se llega en [tSec]. */
fun interface WaitProvider {
    fun expectedWaitSec(signalId: String, tSec: Double): Double
}

/** Usa el modelo aprendido si existe y, si no, una espera media por defecto. */
class ModelWaitProvider(private val modelFor: (signalId: String, tSec: Double) -> SignalModel?) : WaitProvider {
    override fun expectedWaitSec(signalId: String, tSec: Double): Double =
        modelFor(signalId, tSec)?.expectedWaitSec(tSec) ?: SignalModel.DEFAULT_UNKNOWN_WAIT_SEC
}

data class Route(
    val nodeIds: List<String>,
    val departSec: Double,
    val arriveSec: Double,
    val distanceM: Double,
    val waitSec: Double,
    val signals: Int,
) {
    val durationSec: Double get() = arriveSec - departSec
}

/**
 * Ruta más rápida con coste dependiente de la hora: tiempo de recorrido + espera
 * en cada semáforo según la hora estimada de llegada. Una ruta con más km
 * puede ganar si espera menos.
 */
class TimeDependentRouter(
    private val graph: RoadGraph,
    private val speeds: SpeedPolicy,
    private val waits: WaitProvider,
) {
    private data class Label(val node: String, val t: Double)

    fun route(from: String, to: String, departSec: Double): Route? {
        val best = HashMap<String, Double>().apply { put(from, departSec) }
        val prev = HashMap<String, Edge>()
        val waitAt = HashMap<String, Double>()
        val queue = PriorityQueue<Label>(compareBy { it.t }).apply { add(Label(from, departSec)) }

        while (queue.isNotEmpty()) {
            val (node, t) = queue.poll()
            if (t > best.getValue(node)) continue
            if (node == to) break
            for (e in graph.outgoing[node].orEmpty()) {
                var arrive = t + speeds.travelSec(e)
                val signal = graph.nodes.getValue(e.to).signalId
                val w = if (signal != null && e.to != to) waits.expectedWaitSec(signal, arrive) else 0.0
                arrive += w
                if (arrive < (best[e.to] ?: Double.MAX_VALUE)) {
                    best[e.to] = arrive
                    prev[e.to] = e
                    waitAt[e.to] = w
                    queue.add(Label(e.to, arrive))
                }
            }
        }
        val arrive = best[to] ?: return null

        val path = ArrayDeque<String>().apply { addFirst(to) }
        var distance = 0.0
        var wait = 0.0
        var signals = 0
        var cur = to
        while (cur != from) {
            val e = prev.getValue(cur)
            distance += e.lengthM
            wait += waitAt.getValue(cur)
            if (graph.nodes.getValue(cur).signalId != null && cur != to) signals++
            cur = e.from
            path.addFirst(cur)
        }
        return Route(path.toList(), departSec, arrive, distance, wait, signals)
    }

    /** Tiempo esperado de un camino dado (p. ej. la ruta más corta) con las esperas aprendidas. */
    fun evaluate(nodeIds: List<String>, departSec: Double): Route? {
        if (nodeIds.size < 2) return null
        var t = departSec
        var distance = 0.0
        var wait = 0.0
        var signals = 0
        for (i in 0 until nodeIds.size - 1) {
            val e = graph.outgoing[nodeIds[i]].orEmpty().firstOrNull { it.to == nodeIds[i + 1] } ?: return null
            t += speeds.travelSec(e)
            distance += e.lengthM
            val last = i + 1 == nodeIds.size - 1
            val signal = graph.nodes.getValue(e.to).signalId
            if (signal != null && !last) {
                val w = waits.expectedWaitSec(signal, t)
                t += w; wait += w; signals++
            }
        }
        return Route(nodeIds, departSec, t, distance, wait, signals)
    }

    companion object {
        /** Ruta más corta en distancia, como haría un navegador que ignora los semáforos. */
        fun shortest(graph: RoadGraph, from: String, to: String): List<String>? {
            val router = TimeDependentRouter(graph, object : SpeedPolicy() {
                override fun travelSec(edge: Edge) = edge.lengthM
            }, WaitProvider { _, _ -> 0.0 })
            return router.route(from, to, 0.0)?.nodeIds
        }
    }
}
