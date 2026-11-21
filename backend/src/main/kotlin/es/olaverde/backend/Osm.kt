package es.olaverde.backend

import es.olaverde.core.geo.LatLon
import es.olaverde.core.osm.OsmNode
import es.olaverde.core.osm.OsmWay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.double
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Descarga y lectura de datos OSM (Overpass API). */
object Osm {
    /** Valladolid y alrededores. */
    val VALLADOLID = BBox(minLon = -4.80, minLat = 41.58, maxLon = -4.66, maxLat = 41.70)

    private const val OVERPASS = "https://overpass-api.de/api/interpreter"

    fun query(b: BBox): String {
        val box = "${b.minLat},${b.minLon},${b.maxLat},${b.maxLon}"
        val roads = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|" +
            "tertiary|tertiary_link|unclassified|residential|living_street"
        return """
            [out:json][timeout:180];
            (
              node["highway"="traffic_signals"]($box);
              node["highway"="speed_camera"]($box);
              way["highway"~"^($roads)$"]($box);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()
    }

    fun fetch(b: BBox): String {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
        val body = "data=" + URLEncoder.encode(query(b), Charsets.UTF_8)
        val req = HttpRequest.newBuilder(URI(OVERPASS))
            .timeout(Duration.ofMinutes(5))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "ola-verde/0.1")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        check(res.statusCode() == 200) { "Overpass respondió ${res.statusCode()}" }
        return res.body()
    }

    /** Convierte la respuesta JSON de Overpass en nodos y vías. Los nodos repetidos (body + skel) se fusionan. */
    fun parse(json: String): Pair<List<OsmNode>, List<OsmWay>> {
        val elements = Json.parseToJsonElement(json).jsonObject.getValue("elements").jsonArray
        val nodes = LinkedHashMap<Long, OsmNode>()
        val ways = mutableListOf<OsmWay>()
        for (el in elements) {
            val o = el.jsonObject
            val id = o.getValue("id").jsonPrimitive.long
            val tags = (o["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            when (o.getValue("type").jsonPrimitive.content) {
                "node" -> {
                    val pos = LatLon(o.getValue("lat").jsonPrimitive.double, o.getValue("lon").jsonPrimitive.double)
                    val prev = nodes[id]
                    nodes[id] = OsmNode(id, pos, if (prev != null && prev.tags.isNotEmpty()) prev.tags else tags)
                }
                "way" -> ways += OsmWay(id, o.getValue("nodes").jsonArray.map { it.jsonPrimitive.long }, tags)
            }
        }
        return nodes.values.toList() to ways
    }
}
