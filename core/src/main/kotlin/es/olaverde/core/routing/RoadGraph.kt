package es.olaverde.core.routing

import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon

/** Nodo de la red; [signalId] si hay semáforo al llegar a él. */
data class Node(val id: String, val pos: LatLon, val signalId: String? = null)

data class Edge(
    val from: String,
    val to: String,
    val lengthM: Double,
    val limitKmh: Int,
    val radarKmh: Int? = null,
    /** Velocidad media real observada en el tramo (km/h), si se conoce. */
    val observedKmh: Double? = null,
)

class RoadGraph(nodes: Collection<Node>, edges: Collection<Edge>) {
    val nodes: Map<String, Node> = nodes.associateBy { it.id }
    val outgoing: Map<String, List<Edge>> = edges.groupBy { it.from }
    val incoming: Map<String, List<Edge>> = edges.groupBy { it.to }

    init {
        require(edges.all { it.from in this.nodes && it.to in this.nodes }) { "Arista con nodo desconocido" }
    }

    /** Nodo con salida más cercano a [pos] (búsqueda lineal; suficiente para una ciudad). */
    fun nearestNode(pos: LatLon): Node? = nodes.values
        .filter { outgoing.containsKey(it.id) }
        .minByOrNull { Geo.distanceM(it.pos, pos) }

    /** Límite más bajo de las calles que llegan a [nodeId] (para el tramo de aproximación a un semáforo). */
    fun approachLimitKmh(nodeId: String): Int? = incoming[nodeId]?.minOfOrNull { it.limitKmh }
}
