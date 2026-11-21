package es.olaverde.app

import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon

/** Estado del viaje en curso, compartido entre el servicio y la interfaz (hilo principal). */
object RecordingState {
    val recording = mutableStateOf(false)
    val startMillis = mutableLongStateOf(0L)
    val distanceM = mutableDoubleStateOf(0.0)
    val speedKmh = mutableDoubleStateOf(0.0)
    val stops = mutableStateOf(0)
    val trace = mutableStateListOf<LatLon>()
    /** Última posición conocida, aunque no se esté grabando. */
    val lastPosition = mutableStateOf<LatLon?>(null)

    private var stoppedSince: Long? = null

    fun begin(now: Long) {
        trace.clear()
        distanceM.doubleValue = 0.0
        speedKmh.doubleValue = 0.0
        stops.value = 0
        stoppedSince = null
        startMillis.longValue = now
        recording.value = true
    }

    fun add(t: Long, pos: LatLon, speedMps: Double?) {
        trace.lastOrNull()?.let { distanceM.doubleValue += Geo.distanceM(it, pos) }
        trace += pos
        lastPosition.value = pos
        val v = speedMps ?: 0.0
        speedKmh.doubleValue = v * 3.6
        if (v < 1.0) {
            if (stoppedSince == null) stoppedSince = t
        } else {
            stoppedSince?.let { if (t - it >= 5_000) stops.value++ }
            stoppedSince = null
        }
    }

    fun end() {
        recording.value = false
        speedKmh.doubleValue = 0.0
    }
}
