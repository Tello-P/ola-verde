package es.olaverde.core

import es.olaverde.core.geo.Geo
import es.olaverde.core.osm.OsmGraphBuilder
import es.olaverde.core.osm.OsmNode
import es.olaverde.core.osm.OsmWay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OsmGraphBuilderTest {
    private val o = Simulation.VALLADOLID
    private val n1 = OsmNode(1, o)
    private val n2 = OsmNode(2, Geo.offsetNorth(o, 100.0), mapOf("highway" to "traffic_signals"))
    private val n3 = OsmNode(3, Geo.offsetNorth(o, 200.0))
    private val cam = OsmNode(9, Geo.offsetNorth(o, 150.0), mapOf("highway" to "speed_camera", "maxspeed" to "40"))

    @Test
    fun `semaforos, sentido, limites y radares`() {
        val area = OsmGraphBuilder.build(
            listOf(n1, n2, n3, cam),
            listOf(
                OsmWay(10, listOf(1, 2), mapOf("highway" to "residential")),
                OsmWay(11, listOf(2, 3), mapOf("highway" to "primary", "maxspeed" to "50", "oneway" to "yes")),
            ),
        )
        assertEquals(listOf("osm:2"), area.signals.map { it.id })
        assertEquals("osm:2", area.graph.nodes.getValue("osm:2").signalId)
        // residencial sin límite → 30 en ambos sentidos
        assertEquals(30, area.graph.outgoing.getValue("osm:1").single().limitKmh)
        assertEquals(1, area.graph.outgoing.getValue("osm:2").count { it.to == "osm:1" })
        // tramo de sentido único con radar a 40
        val e = area.graph.outgoing.getValue("osm:2").single { it.to == "osm:3" }
        assertEquals(40, e.radarKmh)
        assertNull(area.graph.outgoing["osm:3"])
    }

    @Test
    fun `parsea limites`() {
        assertEquals(50, OsmGraphBuilder.parseSpeed("ES:urban"))
        assertEquals(30, OsmGraphBuilder.parseSpeed("30"))
        assertEquals(48, OsmGraphBuilder.parseSpeed("30 mph"))
        assertNull(OsmGraphBuilder.parseSpeed("none"))
    }
}
