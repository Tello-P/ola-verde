package es.olaverde.core.routing

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

    init {
        require(edges.all { it.from in this.nodes && it.to in this.nodes }) { "Arista con nodo desconocido" }
    }
}
