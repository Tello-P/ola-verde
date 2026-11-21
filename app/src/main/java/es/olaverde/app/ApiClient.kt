package es.olaverde.app

import es.olaverde.core.advice.KnownSignal
import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class RouteResult(
    val points: List<LatLon>,
    val durationSec: Double,
    val distanceM: Double,
    val waitSec: Double,
    val signals: Int,
    val shortestDurationSec: Double,
    val shortestDistanceM: Double,
    val savedSec: Double,
)

/** Cliente mínimo de la API (sin dependencias). Todas las llamadas bloquean: usar fuera del hilo principal. */
class ApiClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    init {
        require(base.startsWith("http://") || base.startsWith("https://")) { "URL del servidor no válida" }
    }

    fun signals(center: LatLon, radiusM: Double, tMillis: Long): List<KnownSignal> {
        val sw = Geo.offsetEast(Geo.offsetNorth(center, -radiusM), -radiusM)
        val ne = Geo.offsetEast(Geo.offsetNorth(center, radiusM), radiusM)
        val arr = JSONArray(get("/v1/signals?bbox=${sw.lon},${sw.lat},${ne.lon},${ne.lat}&t=$tMillis"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val m = o.optJSONObject("model")
            KnownSignal(
                id = id,
                pos = LatLon(o.getDouble("lat"), o.getDouble("lon")),
                model = m?.let {
                    SignalModel(id, it.getDouble("cycleSec"), it.getDouble("greenStartPhaseSec"), it.getDouble("redSec"), it.getDouble("confidence"), it.getInt("samples"))
                },
                approachLimitKmh = if (o.has("limitKmh") && !o.isNull("limitKmh")) o.getInt("limitKmh") else null,
            )
        }
    }

    fun route(from: LatLon, to: LatLon, tMillis: Long, preferred: Map<Int, Int>): RouteResult {
        val speeds = preferred.entries.joinToString("") { "&v${it.key}=${it.value}" }
        val o = JSONObject(get("/v1/route?from=${from.lat},${from.lon}&to=${to.lat},${to.lon}&t=$tMillis$speeds"))
        val pts = o.getJSONArray("points")
        val r = o.getJSONObject("route")
        val s = o.getJSONObject("shortest")
        return RouteResult(
            points = (0 until pts.length()).map { i -> pts.getJSONArray(i).let { LatLon(it.getDouble(0), it.getDouble(1)) } },
            durationSec = r.getDouble("durationSec"),
            distanceM = r.getDouble("distanceM"),
            waitSec = r.getDouble("waitSec"),
            signals = r.getInt("signals"),
            shortestDurationSec = s.getDouble("durationSec"),
            shortestDistanceM = s.getDouble("distanceM"),
            savedSec = o.getDouble("savedSec"),
        )
    }

    fun uploadTrip(deviceId: String, csv: String) {
        val c = open("/v1/trips")
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/csv; charset=utf-8")
            c.setRequestProperty("X-Device-Id", deviceId)
            c.outputStream.use { it.write(csv.toByteArray()) }
            check(c)
        } finally {
            c.disconnect()
        }
    }

    private fun get(path: String): String {
        val c = open(path)
        try {
            check(c)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun open(path: String) = (URL(base + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 20_000
    }

    private fun check(c: HttpURLConnection) {
        val code = c.responseCode
        if (code !in 200..299) {
            val msg = c.errorStream?.bufferedReader()?.use { it.readText() }
                ?.let { runCatching { JSONObject(it).getString("error") }.getOrDefault(it) }
            throw IOException(msg ?: "Error $code del servidor")
        }
    }
}
