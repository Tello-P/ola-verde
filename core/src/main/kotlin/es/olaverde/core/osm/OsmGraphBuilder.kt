package es.olaverde.core.osm

import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon
import es.olaverde.core.model.TrafficSignal
import es.olaverde.core.routing.Edge
import es.olaverde.core.routing.Node
import es.olaverde.core.routing.RoadGraph

data class OsmNode(val id: Long, val pos: LatLon, val tags: Map<String, String> = emptyMap())
data class OsmWay(val id: Long, val nodeIds: List<Long>, val tags: Map<String, String> = emptyMap())

data class SpeedCamera(val id: String, val pos: LatLon, val maxKmh: Int?)

/** Datos OSM de una zona ya convertidos a modelos del núcleo. */
data class OsmArea(
    val graph: RoadGraph,
    val signals: List<TrafficSignal>,
    val cameras: List<SpeedCamera>,
)

/**
 * Construye el grafo de calles con semáforos, límites y radares a partir de nodos y vías OSM.
 * Límites que falten: por tipo de vía según la normativa urbana española
 * (30 km/h en calles residenciales de un carril por sentido, 50 en las demás urbanas).
 */
object OsmGraphBuilder {
    private val DRIVABLE = setOf(
        "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
        "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
        "residential", "living_street",
    )

    /** Radio en metros para asignar un radar a los tramos cercanos. */
    private const val CAMERA_RADIUS_M = 30.0

    fun build(nodes: List<OsmNode>, ways: List<OsmWay>): OsmArea {
        val byId = nodes.associateBy { it.id }
        val signals = nodes.filter { it.tags["highway"] == "traffic_signals" }
            .map { TrafficSignal("osm:${it.id}", it.pos) }
        val signalIds = signals.map { it.id }.toSet()
        val cameras = nodes.filter { it.tags["highway"] == "speed_camera" }
            .map { SpeedCamera("osm:${it.id}", it.pos, parseSpeed(it.tags["maxspeed"])) }

        val used = mutableSetOf<Long>()
        val edges = mutableListOf<Edge>()
        for (w in ways) {
            val type = w.tags["highway"] ?: continue
            if (type !in DRIVABLE) continue
            val limit = parseSpeed(w.tags["maxspeed"]) ?: defaultLimit(type, w.tags)
            val oneway = isOneway(w.tags, type)
            val reversed = w.tags["oneway"] == "-1"
            val ids = w.nodeIds.filter { it in byId }
            for (i in 0 until ids.size - 1) {
                val a = byId.getValue(ids[i])
                val b = byId.getValue(ids[i + 1])
                val len = Geo.distanceM(a.pos, b.pos)
                if (len <= 0.0) continue
                val radar = cameras.filter { near(it.pos, a.pos, b.pos) }.mapNotNull { it.maxKmh }.minOrNull()
                used += a.id; used += b.id
                fun edge(from: OsmNode, to: OsmNode) =
                    Edge("osm:${from.id}", "osm:${to.id}", len, limit, radar?.coerceAtMost(limit))
                when {
                    !oneway -> { edges += edge(a, b); edges += edge(b, a) }
                    reversed -> edges += edge(b, a)
                    else -> edges += edge(a, b)
                }
            }
        }
        val graphNodes = used.map { id ->
            val n = byId.getValue(id)
            val key = "osm:$id"
            Node(key, n.pos, if (key in signalIds) key else null)
        }
        return OsmArea(RoadGraph(graphNodes, edges), signals, cameras)
    }

    fun parseSpeed(raw: String?): Int? {
        if (raw == null) return null
        val v = raw.trim().lowercase()
        return when {
            v.endsWith("mph") -> v.removeSuffix("mph").trim().toIntOrNull()?.let { (it * 1.609).toInt() }
            v == "es:urban" -> 50
            v == "es:rural" -> 90
            v == "es:living_street" || v == "walk" -> 20
            else -> v.substringBefore(';').trim().toIntOrNull()
        }
    }

    fun defaultLimit(type: String, tags: Map<String, String>): Int = when (type) {
        "motorway", "motorway_link" -> 120
        "trunk", "trunk_link" -> 100
        "living_street" -> 20
        "residential", "unclassified" -> if ((tags["lanes"]?.toIntOrNull() ?: 1) <= 2) 30 else 50
        else -> 50
    }

    private fun isOneway(tags: Map<String, String>, type: String): Boolean = when (tags["oneway"]) {
        "yes", "true", "1", "-1" -> true
        "no" -> false
        else -> type == "motorway" || type == "motorway_link" || tags["junction"] == "roundabout"
    }

    /** ¿Está [p] a menos de CAMERA_RADIUS_M del segmento a-b? (aprox. plana, válida a escala urbana) */
    private fun near(p: LatLon, a: LatLon, b: LatLon): Boolean {
        val kx = 111_320.0 * Math.cos(Math.toRadians(a.lat))
        val ky = 110_540.0
        val ax = 0.0; val ay = 0.0
        val bx = (b.lon - a.lon) * kx; val by = (b.lat - a.lat) * ky
        val px = (p.lon - a.lon) * kx; val py = (p.lat - a.lat) * ky
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - (ax + t * bx); val dy = py - (ay + t * by)
        return Math.hypot(dx, dy) <= CAMERA_RADIUS_M
    }
}
