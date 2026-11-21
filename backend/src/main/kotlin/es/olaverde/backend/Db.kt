package es.olaverde.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import es.olaverde.core.geo.LatLon
import es.olaverde.core.model.SignalObservation
import es.olaverde.core.model.TrafficSignal
import es.olaverde.core.osm.SpeedCamera
import es.olaverde.core.stats.TripStats
import java.sql.Connection

data class BBox(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    fun expand(meters: Double): BBox {
        val dLat = meters / 110_540.0
        val dLon = meters / (111_320.0 * Math.cos(Math.toRadians((minLat + maxLat) / 2)))
        return BBox(minLon - dLon, minLat - dLat, maxLon + dLon, maxLat + dLat)
    }

    companion object {
        fun parse(raw: String): BBox {
            val v = raw.split(',').map { it.trim().toDouble() }
            require(v.size == 4) { "bbox = minLon,minLat,maxLon,maxLat" }
            return BBox(v[0], v[1], v[2], v[3])
        }

        fun of(points: List<LatLon>) = BBox(
            points.minOf { it.lon }, points.minOf { it.lat }, points.maxOf { it.lon }, points.maxOf { it.lat },
        )
    }
}

/**
 * Acceso a datos con SQL estándar: funciona con PostgreSQL (producción) y H2 en modo PostgreSQL (desarrollo y tests).
 * No se guardan trazas GPS crudas: solo el resumen del viaje y las observaciones de semáforo.
 */
class Db(url: String, user: String? = null, password: String? = null) : AutoCloseable {
    private val ds = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            user?.let { username = it }
            password?.let { this.password = it }
            maximumPoolSize = 5
        },
    )

    init {
        tx { c ->
            c.createStatement().use { st ->
                SCHEMA.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { st.execute(it) }
            }
        }
    }

    fun <T> tx(block: (Connection) -> T): T = ds.connection.use { c ->
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Exception) {
            c.rollback(); throw e
        }
    }

    /** Sustituye los semáforos y radares importados de OSM. */
    fun replaceOsm(signals: List<TrafficSignal>, cameras: List<SpeedCamera>) = tx { c ->
        c.prepareStatement("DELETE FROM signals WHERE source = 'osm'").use { it.executeUpdate() }
        c.prepareStatement("DELETE FROM cameras WHERE source = 'osm'").use { it.executeUpdate() }
        c.prepareStatement("INSERT INTO signals (id, lat, lon, source) VALUES (?, ?, ?, 'osm')").use { ps ->
            for (s in signals) {
                ps.setString(1, s.id); ps.setDouble(2, s.pos.lat); ps.setDouble(3, s.pos.lon); ps.addBatch()
            }
            ps.executeBatch()
        }
        c.prepareStatement("INSERT INTO cameras (id, lat, lon, max_kmh, source) VALUES (?, ?, ?, ?, 'osm')").use { ps ->
            for (cam in cameras) {
                ps.setString(1, cam.id); ps.setDouble(2, cam.pos.lat); ps.setDouble(3, cam.pos.lon)
                val max = cam.maxKmh
                if (max != null) ps.setInt(4, max) else ps.setNull(4, java.sql.Types.INTEGER)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    fun signalsIn(b: BBox): List<TrafficSignal> = tx { c ->
        c.prepareStatement("SELECT id, lat, lon FROM signals WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?").use { ps ->
            ps.setDouble(1, b.minLat); ps.setDouble(2, b.maxLat); ps.setDouble(3, b.minLon); ps.setDouble(4, b.maxLon)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(TrafficSignal(rs.getString(1), LatLon(rs.getDouble(2), rs.getDouble(3)))) }
            }
        }
    }

    fun camerasIn(b: BBox): List<SpeedCamera> = tx { c ->
        c.prepareStatement("SELECT id, lat, lon, max_kmh FROM cameras WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?").use { ps ->
            ps.setDouble(1, b.minLat); ps.setDouble(2, b.maxLat); ps.setDouble(3, b.minLon); ps.setDouble(4, b.maxLon)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val max = rs.getInt(4).takeUnless { rs.wasNull() }
                        add(SpeedCamera(rs.getString(1), LatLon(rs.getDouble(2), rs.getDouble(3)), max))
                    }
                }
            }
        }
    }

    fun saveTrip(deviceId: String, stats: TripStats, obs: List<SignalObservation>, nowMillis: Long): Long = tx { c ->
        val id = c.prepareStatement(
            "INSERT INTO trips (device_id, start_ms, duration_s, distance_m, stops, created_ms) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("id"),
        ).use { ps ->
            ps.setString(1, deviceId); ps.setLong(2, stats.startMillis); ps.setLong(3, stats.durationSec)
            ps.setDouble(4, stats.distanceM); ps.setInt(5, stats.stops); ps.setLong(6, nowMillis)
            ps.executeUpdate()
            ps.generatedKeys.use { it.next(); it.getLong(1) }
        }
        c.prepareStatement("INSERT INTO observations (trip_id, signal_id, kind, t_ms, to_ms) VALUES (?, ?, ?, ?, ?)").use { ps ->
            for (o in obs) {
                ps.setLong(1, id); ps.setString(2, o.signalId)
                when (o) {
                    is SignalObservation.GreenStart -> { ps.setString(3, "green_start"); ps.setLong(4, o.tMillis); ps.setNull(5, java.sql.Types.BIGINT) }
                    is SignalObservation.GreenAt -> { ps.setString(3, "green_at"); ps.setLong(4, o.tMillis); ps.setNull(5, java.sql.Types.BIGINT) }
                    is SignalObservation.RedInterval -> { ps.setString(3, "red"); ps.setLong(4, o.fromMillis); ps.setLong(5, o.toMillis) }
                }
                ps.addBatch()
            }
            ps.executeBatch()
        }
        id
    }

    fun observations(signalIds: Collection<String>): List<SignalObservation> {
        if (signalIds.isEmpty()) return emptyList()
        return tx { c ->
            val marks = signalIds.joinToString(",") { "?" }
            c.prepareStatement("SELECT signal_id, kind, t_ms, to_ms FROM observations WHERE signal_id IN ($marks)").use { ps ->
                signalIds.forEachIndexed { i, id -> ps.setString(i + 1, id) }
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val sid = rs.getString(1)
                            val t = rs.getLong(3)
                            when (rs.getString(2)) {
                                "green_start" -> add(SignalObservation.GreenStart(sid, t))
                                "green_at" -> add(SignalObservation.GreenAt(sid, t))
                                "red" -> add(SignalObservation.RedInterval(sid, t, rs.getLong(4)))
                            }
                        }
                    }
                }
            }
        }
    }

    /** Solo para tests contra una base real. */
    internal fun clearTrips() = tx { c -> c.prepareStatement("DELETE FROM trips").use { it.executeUpdate() } }

    override fun close() = ds.close()

    companion object {
        private val SCHEMA = """
            CREATE TABLE IF NOT EXISTS signals (
                id VARCHAR(64) PRIMARY KEY,
                lat DOUBLE PRECISION NOT NULL,
                lon DOUBLE PRECISION NOT NULL,
                source VARCHAR(16) NOT NULL
            );
            CREATE INDEX IF NOT EXISTS signals_lat_lon ON signals (lat, lon);
            CREATE TABLE IF NOT EXISTS cameras (
                id VARCHAR(64) PRIMARY KEY,
                lat DOUBLE PRECISION NOT NULL,
                lon DOUBLE PRECISION NOT NULL,
                max_kmh INTEGER,
                source VARCHAR(16) NOT NULL
            );
            CREATE TABLE IF NOT EXISTS trips (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                device_id VARCHAR(64) NOT NULL,
                start_ms BIGINT NOT NULL,
                duration_s BIGINT NOT NULL,
                distance_m DOUBLE PRECISION NOT NULL,
                stops INTEGER NOT NULL,
                created_ms BIGINT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS observations (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                trip_id BIGINT NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
                signal_id VARCHAR(64) NOT NULL,
                kind VARCHAR(16) NOT NULL,
                t_ms BIGINT NOT NULL,
                to_ms BIGINT
            );
            CREATE INDEX IF NOT EXISTS observations_signal ON observations (signal_id)
        """.trimIndent()
    }
}
