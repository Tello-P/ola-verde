package es.olaverde.backend

import es.olaverde.core.Simulation
import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.geo.LatLon
import es.olaverde.core.io.TripCsv
import es.olaverde.core.osm.OsmGraphBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = Instant.parse("2026-11-23T09:30:00Z").epochSecond

    private fun services(): Services {
        // TEST_DATABASE_URL = PostgreSQL real (CI); si no, H2 en memoria.
        val pg = System.getenv("TEST_DATABASE_URL")
        val db = if (pg != null) {
            Db(pg, System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")).also { it.clearTrips() }
        } else {
            Db("jdbc:h2:mem:t${Random.nextInt()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
        }
        val (nodes, ways) = Osm.parse(javaClass.getResource("/overpass-mini.json")!!.readText())
        val area = OsmGraphBuilder.build(nodes, ways)
        db.replaceOsm(area.signals, area.cameras)
        return Services(db, area)
    }

    @Test
    fun `importa OSM y lo sirve por bbox`() = testApplication {
        val s = services()
        application { olaVerde(s) }
        val signals = json.decodeFromString<List<SignalDto>>(client.get("/v1/signals?bbox=-4.73,41.65,-4.72,41.66").bodyAsText())
        assertEquals(listOf("osm:2"), signals.map { it.id })
        val cams = json.decodeFromString<List<CameraDto>>(client.get("/v1/cameras?bbox=-4.73,41.65,-4.72,41.66").bodyAsText())
        assertEquals(40, cams.single().maxKmh)
        assertEquals(40, s.area!!.graph.outgoing.getValue("osm:2").first { it.to == "osm:3" }.radarKmh)
    }

    @Test
    fun `aprende el semaforo con viajes subidos`() = testApplication {
        val s = services()
        application { olaVerde(s) }
        val signalPos = LatLon(41.6532, -4.7245)
        val truth = SignalModel("osm:2", 90.0, (base + 17).mod(90).toDouble(), 40.0, 1.0, 0)
        val rnd = Random(7)
        repeat(15) { i ->
            val trace = Simulation.trace(truth, base + i * 400L + rnd.nextLong(0, 90), signalPos)
            val res = client.post("/v1/trips") {
                header("X-Device-Id", "test-device")
                setBody(TripCsv.write(trace))
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
        val signals = json.decodeFromString<List<SignalDto>>(
            client.get("/v1/signals?bbox=-4.73,41.65,-4.72,41.66&t=${base * 1000}").bodyAsText(),
        )
        val model = assertNotNull(signals.single().model)
        assertEquals(90.0, model.cycleSec, 1.0)
        assertTrue(model.confidence > 0.6, "confianza ${model.confidence}")
    }

    @Test
    fun `rechaza viajes sin dispositivo`() = testApplication {
        val s = services()
        application { olaVerde(s) }
        val res = client.post("/v1/trips") { setBody(TripCsv.HEADER + "\n") }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }
}
