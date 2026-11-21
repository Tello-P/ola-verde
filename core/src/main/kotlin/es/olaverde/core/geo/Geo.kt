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

    /** Rumbo inicial de [a] a [b] en grados (0 = norte, 90 = este). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val la1 = Math.toRadians(a.lat); val la2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    /** Diferencia absoluta entre dos rumbos, 0..180. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = ((a - b) % 360 + 360) % 360
        return if (d > 180) 360 - d else d
    }

    /** Punto desplazado [meters] hacia el norte (útil en tests y simulaciones). */
    fun offsetNorth(p: LatLon, meters: Double): LatLon =
        LatLon(p.lat + Math.toDegrees(meters / EARTH_RADIUS_M), p.lon)

    /** Punto desplazado [meters] hacia el este. */
    fun offsetEast(p: LatLon, meters: Double): LatLon =
        LatLon(p.lat, p.lon + Math.toDegrees(meters / (EARTH_RADIUS_M * cos(Math.toRadians(p.lat)))))
}
