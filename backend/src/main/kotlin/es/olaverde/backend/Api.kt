package es.olaverde.backend

import es.olaverde.core.detection.SignalEventDetector
import es.olaverde.core.estimation.CycleEstimator
import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.estimation.TimeBand
import es.olaverde.core.io.TripCsv
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
@Serializable data class SignalDto(val id: String, val lat: Double, val lon: Double, val model: ModelDto? = null)
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
        return signals.map { SignalDto(it.id, it.pos.lat, it.pos.lon, models[it.id]?.toDto()) }
    }

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

        get("/v1/cameras") {
            val b = BBox.parse(requireNotNull(call.request.queryParameters["bbox"]) { "Falta bbox" })
            call.respond(services.cameras(b))
        }
    }
}
