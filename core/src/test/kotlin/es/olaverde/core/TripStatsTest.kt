package es.olaverde.core

import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.stats.TripStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripStatsTest {
    @Test
    fun `cuenta distancia, duracion y paradas`() {
        // Semáforo en rojo 30 s al llegar: una parada.
        val truth = SignalModel("s", 90.0, 50.0, 40.0, 1.0, 0)
        val trace = Simulation.trace(truth, startSec = 0)
        val stats = assertNotNull(TripStats.of(trace))
        assertEquals(1, stats.stops)
        assertTrue(stats.distanceM in 290.0..310.0, "distancia ${stats.distanceM}")
        assertTrue(stats.stoppedSec in 20L..40L, "parado ${stats.stoppedSec}")
        assertEquals(trace.size - 1L, stats.durationSec)
    }
}
