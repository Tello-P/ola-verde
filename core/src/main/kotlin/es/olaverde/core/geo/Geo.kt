package es.olaverde.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distancia en metros (haversine). */
    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Punto desplazado [meters] hacia el norte (útil en tests y simulaciones). */
    fun offsetNorth(p: LatLon, meters: Double): LatLon =
        LatLon(p.lat + Math.toDegrees(meters / EARTH_RADIUS_M), p.lon)
}
