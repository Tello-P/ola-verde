package es.olaverde.backend

import es.olaverde.core.detection.SignalEventDetector
import es.olaverde.core.estimation.CycleEstimator
import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.estimation.TimeBand
import es.olaverde.core.geo.LatLon
import es.olaverde.core.io.TripCsv
import es.olaverde.core.routing.ModelWaitProvider
import es.olaverde.core.routing.Route
import es.olaverde.core.routing.SpeedPolicy
import es.olaverde.core.routing.TimeDependentRouter
import es.olaverde.core.model.SignalObservation
import es.olaverde.core.osm.OsmArea
import es.olaverde.core.stats.TripStats
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable data class ModelDto(val cycleSec: Double, val greenStartPhaseSec: Double, val redSec: Double, val confidence: Double, val samples: Int)
@Serializable data class SignalDto(val id: String, val lat: Double, val lon: Double, val model: ModelDto? = null, val limitKmh: Int? = null)
@Serializable data class RouteSummaryDto(val durationSec: Double, val distanceM: Double, val waitSec: Double, val signals: Int)
@Serializable data class RouteDto(
    /** [[lat, lon], ...] */
    val points: List<List<Double>>,
    val route: RouteSummaryDto,
    /** La ruta más corta en km, con las esperas aprendidas, para comparar. */
    val shortest: RouteSummaryDto,
    val savedSec: Double,
)
@Serializable data class CameraDto(val id: String, val lat: Double, val lon: Double, val maxKmh: Int? = null)
@Serializable data class TripResultDto(val tripId: Long, val durationSec: Long, val distanceM: Double, val stops: Int, val observations: Int)
@Serializable data class ErrorDto(val error: String)

fun SignalModel.toDto() = ModelDto(cycleSec, greenStartPhaseSec, redSec, confidence, samples)

/** Lógica de la API, separada de Ktor para poder probarla. */
class Services(
    val db: Db,
    /** Grafo OSM cargado en memoria (para rutas); null si no se ha importado. */
    @Volatile var area: OsmArea? = null,
    private val detector: SignalEventDetector = SignalEventDetector(),
    private val estimator: CycleEstimator = CycleEstimator(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun uploadTrip(deviceId: String, csv: String): TripResultDto {
        require(deviceId.isNotBlank() && deviceId.length <= 64) { "X-Device-Id obligatorio (máx. 64 caracteres)" }
        val points = TripCsv.parse(csv)
        val stats = requireNotNull(TripStats.of(points)) { "El viaje necesita al menos 2 puntos GPS válidos" }
        val signals = db.signalsIn(BBox.of(points.map { it.pos }).expand(100.0))
        val obs = detector.detect(points, signals)
        val id = db.saveTrip(deviceId, stats, obs, clock())
        return TripResultDto(id, stats.durationSec, stats.distanceM, stats.stops, obs.size)
    }

    /** Semáforos de la zona con su modelo para la franja de [tMillis], si hay datos suficientes. */
    fun signals(b: BBox, tMillis: Long): List<SignalDto> {
        val signals = db.signalsIn(b)
        val models = models(signals.map { it.id }, tMillis)
        val graph = area?.graph
        return signals.map { SignalDto(it.id, it.pos.lat, it.pos.lon, models[it.id]?.toDto(), graph?.approachLimitKmh(it.id)) }
    }

    /**
     * Ruta más rápida contando las esperas aprendidas en cada semáforo, comparada con la más corta.
     * [preferredKmh] = velocidad del usuario por límite; siempre se acota al límite legal.
     */
    fun route(from: LatLon, to: LatLon, tMillis: Long, preferredKmh: Map<Int, Double>): RouteDto {
        val graph = requireNotNull(area?.graph) { "No hay mapa cargado en el servidor" }
        val a = requireNotNull(graph.nearestNode(from)) { "Origen fuera del mapa" }
        val b = requireNotNull(graph.nearestNode(to)) { "Destino fuera del mapa" }
        val ids = graph.nodes.values.mapNotNull { it.signalId }
        val models = models(ids, tMillis)
        val router = TimeDependentRouter(graph, SpeedPolicy(preferredKmh), ModelWaitProvider { id, _ -> models[id] })
        val depart = tMillis / 1000.0
        val best = requireNotNull(router.route(a.id, b.id, depart)) { "No hay ruta entre esos puntos" }
        val shortestPath = requireNotNull(TimeDependentRouter.shortest(graph, a.id, b.id))
        val shortest = requireNotNull(router.evaluate(shortestPath, depart))
        return RouteDto(
            points = best.nodeIds.map { graph.nodes.getValue(it).pos.let { p -> listOf(p.lat, p.lon) } },
            route = best.toSummary(),
            shortest = shortest.toSummary(),
            savedSec = (shortest.durationSec - best.durationSec).coerceAtLeast(0.0),
        )
    }

    private fun Route.toSummary() = RouteSummaryDto(durationSec, distanceM, waitSec, signals)

    fun models(signalIds: Collection<String>, tMillis: Long): Map<String, SignalModel> {
        val band = TimeBand.of(tMillis)
        return db.observations(signalIds)
            .filter { TimeBand.of(timeOf(it)) == band }
            .groupBy { it.signalId }
            .mapNotNull { (id, obs) -> estimator.estimate(id, obs)?.let { id to it } }
            .toMap()
    }

    fun cameras(b: BBox) = db.camerasIn(b).map { CameraDto(it.id, it.pos.lat, it.pos.lon, it.maxKmh) }

    private fun timeOf(o: SignalObservation) = when (o) {
        is SignalObservation.GreenStart -> o.tMillis
        is SignalObservation.GreenAt -> o.tMillis
        is SignalObservation.RedInterval -> o.fromMillis
    }
}

private const val MAX_TRIP_BYTES = 5 * 1024 * 1024

fun Application.olaVerde(services: Services) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Petición no válida")) }
    }
    routing {
        get("/health") { call.respondText("ok") }

        post("/v1/trips") {
            val device = call.request.headers["X-Device-Id"].orEmpty()
            val body = call.receiveText()
            require(body.length <= MAX_TRIP_BYTES) { "Viaje demasiado grande" }
            call.respond(HttpStatusCode.Created, services.uploadTrip(device, body))
        }

        get("/v1/signals") {
            val b = BBox.parse(requireNotNull(call.request.queryParameters["bbox"]) { "Falta bbox" })
            val t = call.request.queryParameters["t"]?.toLongOrNull() ?: System.currentTimeMillis()
            call.respond(services.signals(b, t))
        }

        get("/v1/route") {
            val q = call.request.queryParameters
            fun point(name: String): LatLon {
                val v = requireNotNull(q[name]) { "Falta $name=lat,lon" }.split(',').map { it.trim().toDouble() }
                require(v.size == 2) { "$name = lat,lon" }
                return LatLon(v[0], v[1])
            }
            // v30=28&v50=45 … velocidad preferida por límite
            val preferred = q.names().filter { it.matches(Regex("v\\d+")) }
                .associate { it.drop(1).toInt() to q[it]!!.toDouble() }
            val t = q["t"]?.toLongOrNull() ?: System.currentTimeMillis()
            call.respond(services.route(point("from"), point("to"), t, preferred))
        }

        get("/v1/cameras") {
            val b = BBox.parse(requireNotNull(call.request.queryParameters["bbox"]) { "Falta bbox" })
            call.respond(services.cameras(b))
        }
    }
}
