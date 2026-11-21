package es.olaverde.core

import es.olaverde.core.advice.GreenWaveAdvisor
import es.olaverde.core.advice.SignalAhead
import es.olaverde.core.detection.SignalEventDetector
import es.olaverde.core.estimation.CycleEstimator
import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.estimation.SignalModelStore
import es.olaverde.core.geo.Geo
import es.olaverde.core.model.SignalObservation
import es.olaverde.core.model.TrafficSignal
import es.olaverde.core.routing.Edge
import es.olaverde.core.routing.ModelWaitProvider
import es.olaverde.core.routing.Node
import es.olaverde.core.routing.RoadGraph
import es.olaverde.core.routing.SpeedPolicy
import es.olaverde.core.routing.TimeDependentRouter
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreTest {
    // Lunes 23/11/2026 10:30 en Madrid (franja de día laborable).
    private val base = Instant.parse("2026-11-23T09:30:00Z").epochSecond
    private val truth = SignalModel("s1", cycleSec = 90.0, greenStartPhaseSec = (base + 17).mod(90).toDouble(), redSec = 40.0, confidence = 1.0, samples = 0)
    private val signal = TrafficSignal("s1", Simulation.VALLADOLID)
    private val detector = SignalEventDetector()

    private fun circDiff(a: Double, b: Double, c: Double) = abs(((a - b).mod(c) + c / 2).mod(c) - c / 2)

    @Test
    fun `parar en rojo da inicio de verde y pasar sin parar da verde`() {
        // Llegada (~19,5 s después) en rojo: el verde empieza en base+17+90.
        val redTrip = Simulation.trace(truth, startSec = base + 17 + 90 - 30)
        val obs = detector.detect(redTrip, listOf(signal))
        val start = obs.filterIsInstance<SignalObservation.GreenStart>().single()
        assertTrue(abs(start.tMillis / 1000 - (base + 17 + 90)) <= 1, "inicio de verde ${start.tMillis / 1000 - base}")
        assertEquals(1, obs.filterIsInstance<SignalObservation.RedInterval>().size)

        val greenTrip = Simulation.trace(truth, startSec = base + 17 + 5)
        val greenObs = detector.detect(greenTrip, listOf(signal))
        assertTrue(greenObs.single() is SignalObservation.GreenAt)
    }

    @Test
    fun `estima el ciclo y el desfase con varias pasadas`() {
        val rnd = Random(42)
        val obs = (0 until 15).flatMap { i ->
            val start = base + i * 400L + rnd.nextLong(0, 90)
            detector.detect(Simulation.trace(truth, start), listOf(signal))
        }
        val model = assertNotNull(CycleEstimator().estimate("s1", obs))
        assertEquals(90.0, model.cycleSec, 1.0)
        assertTrue(circDiff(model.greenStartPhaseSec, truth.greenStartPhaseSec, 90.0) <= 3, "desfase ${model.greenStartPhaseSec}")
        assertTrue(model.redSec in 25.0..41.0, "rojo ${model.redSec}")
        assertTrue(model.confidence > 0.6, "confianza ${model.confidence}")

        val store = SignalModelStore().apply { add(obs) }
        assertNotNull(store.model("s1", base * 1000))
    }

    @Test
    fun `sin suficientes datos no hay modelo`() {
        val obs = detector.detect(Simulation.trace(truth, base + 17 + 60), listOf(signal))
        assertEquals(null, CycleEstimator().estimate("s1", obs))
    }

    @Test
    fun `la velocidad de calculo nunca supera el limite y el radar manda`() {
        val policy = SpeedPolicy(mapOf(30 to 50.0, 50 to 60.0, 90 to 80.0))
        assertEquals(30.0, policy.speedKmh(Edge("a", "b", 100.0, 30)))
        assertEquals(50.0, policy.speedKmh(Edge("a", "b", 100.0, 50)))
        assertEquals(80.0, policy.speedKmh(Edge("a", "b", 100.0, 90)))
        assertEquals(70.0, policy.speedKmh(Edge("a", "b", 100.0, 90, radarKmh = 70)))
        assertEquals(20.0, policy.speedKmh(Edge("a", "b", 100.0, 50, observedKmh = 20.0)))
    }

    @Test
    fun `elige una ruta mas larga si espera menos en semaforos`() {
        val p = Simulation.VALLADOLID
        fun n(id: String, m: Double, sig: String? = null) = Node(id, Geo.offsetNorth(p, m), sig)
        val nodes = listOf(
            n("A", 0.0), n("S1", 300.0, "x1"), n("S2", 600.0, "x2"), n("S3", 900.0, "x3"), n("B", 1000.0),
            n("L", 700.0),
        )
        val edges = listOf(
            Edge("A", "S1", 300.0, 50), Edge("S1", "S2", 300.0, 50), Edge("S2", "S3", 300.0, 50), Edge("S3", "B", 100.0, 50),
            Edge("A", "L", 700.0, 50), Edge("L", "B", 700.0, 50),
        )
        val graph = RoadGraph(nodes, edges)
        // Semáforos siempre en rojo justo al llegar: 40 s de espera cada uno.
        val alwaysRedOnArrival = ModelWaitProvider { id, t ->
            SignalModel(id, 90.0, (t + 40).mod(90.0), 45.0, 1.0, 10)
        }
        val route = assertNotNull(TimeDependentRouter(graph, SpeedPolicy(), alwaysRedOnArrival).route("A", "B", base.toDouble()))
        assertEquals(listOf("A", "L", "B"), route.nodeIds)
        assertEquals(1400.0, route.distanceM)

        // Con semáforos en verde gana la corta.
        val green = ModelWaitProvider { id, t -> SignalModel(id, 90.0, (t - 1).mod(90.0), 30.0, 1.0, 10) }
        val short = assertNotNull(TimeDependentRouter(graph, SpeedPolicy(), green).route("A", "B", base.toDouble()))
        assertEquals(listOf("A", "S1", "S2", "S3", "B"), short.nodeIds)
        assertEquals(3, short.signals)
    }

    @Test
    fun `ola verde recomienda una velocidad dentro del limite`() {
        val now = base.toDouble()
        // Dos semáforos a 300 m y 600 m, con verde al llegar a 36 km/h (10 m/s): t+30 y t+60.
        fun greenAround(at: Double) = SignalModel("g", 90.0, (at - 5).mod(90.0), 50.0, 1.0, 10)
        val ahead = listOf(SignalAhead(300.0, greenAround(now + 30)), SignalAhead(600.0, greenAround(now + 60)))
        val advice = assertNotNull(GreenWaveAdvisor().advise(now, 50, ahead))
        assertEquals(2, advice.greensInRow)
        assertTrue(advice.maxKmh <= 50)
        assertTrue(36 in advice.minKmh..advice.maxKmh, "rango ${advice.minKmh}-${advice.maxKmh}")
    }
}
