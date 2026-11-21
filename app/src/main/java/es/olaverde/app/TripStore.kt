package es.olaverde.app

import android.content.Context
import es.olaverde.core.io.TripCsv
import es.olaverde.core.model.GpsPoint
import es.olaverde.core.stats.TripStats
import java.io.File

/** Viajes guardados como CSV (t_ms,lat,lon,velocidad_mps,precision_m) en el almacenamiento privado de la app. */
class TripStore(context: Context) {
    private val dir = File(context.filesDir, "trips").apply { mkdirs() }

    fun newTrip(startMillis: Long): File =
        File(dir, "$startMillis.csv").apply { writeText(TripCsv.HEADER + "\n") }

    fun trips(): List<File> = dir.listFiles { f -> f.extension == "csv" }.orEmpty().sortedByDescending { it.name }

    fun load(file: File): List<GpsPoint> = TripCsv.parse(file.readText())

    fun delete(file: File) = file.delete()

    /** Resumen de cada viaje guardado (los vacíos se omiten). */
    fun summaries(): List<Pair<File, TripStats>> =
        trips().mapNotNull { f -> runCatching { TripStats.of(load(f)) }.getOrNull()?.let { f to it } }
}
