package es.olaverde.core.io

import es.olaverde.core.geo.LatLon
import es.olaverde.core.model.GpsPoint

/** Formato de viaje compartido por app y backend: t_ms,lat,lon,speed_mps,accuracy_m */
object TripCsv {
    const val HEADER = "t_ms,lat,lon,speed_mps,accuracy_m"

    /** Descarta líneas mal formadas y puntos con precisión peor que [maxAccuracyM]. */
    fun parse(text: String, maxAccuracyM: Double = 25.0): List<GpsPoint> = text.lineSequence()
        .drop(1)
        .mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 4) return@mapNotNull null
            val t = c[0].toLongOrNull() ?: return@mapNotNull null
            val lat = c[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = c[2].toDoubleOrNull() ?: return@mapNotNull null
            val acc = c.getOrNull(4)?.toDoubleOrNull()
            if (acc != null && acc > maxAccuracyM) return@mapNotNull null
            GpsPoint(t, LatLon(lat, lon), c[3].toDoubleOrNull())
        }
        .sortedBy { it.tMillis }
        .toList()

    fun write(points: List<GpsPoint>): String = buildString {
        appendLine(HEADER)
        for (p in points) appendLine("${p.tMillis},${p.pos.lat},${p.pos.lon},${p.speedMps ?: ""},")
    }
}
