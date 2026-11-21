package es.olaverde.app

import android.content.Context
import es.olaverde.core.geo.LatLon
import es.olaverde.core.model.GpsPoint
import es.olaverde.core.stats.TripStats
import java.io.File

/** Viajes guardados como CSV (t_ms,lat,lon,velocidad_mps,precision_m) en el almacenamiento privado de la app. */
class TripStore(context: Context) {
    private val dir = File(context.filesDir, "trips").apply { mkdirs() }

    fun newTrip(startMillis: Long): File =
        File(dir, "$startMillis.csv").apply { writeText("t_ms,lat,lon,speed_mps,accuracy_m\n") }

    fun trips(): List<File> = dir.listFiles { f -> f.extension == "csv" }.orEmpty().sortedByDescending { it.name }

    fun load(file: File): List<GpsPoint> = file.readLines().drop(1).mapNotNull { line ->
        val c = line.split(',')
        if (c.size < 4) return@mapNotNull null
        GpsPoint(c[0].toLong(), LatLon(c[1].toDouble(), c[2].toDouble()), c[3].toDoubleOrNull())
    }

    fun delete(file: File) = file.delete()

    /** Resumen de cada viaje guardado (los vacíos se omiten). */
    fun summaries(): List<Pair<File, TripStats>> =
        trips().mapNotNull { f -> runCatching { TripStats.of(load(f)) }.getOrNull()?.let { f to it } }
}
