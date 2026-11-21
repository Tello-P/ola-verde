package es.olaverde.core

import es.olaverde.core.advice.AheadFinder
import es.olaverde.core.advice.GreenWaveAdvice
import es.olaverde.core.advice.KnownSignal
import es.olaverde.core.advice.VoicePrompts
import es.olaverde.core.estimation.SignalModel
import es.olaverde.core.geo.Geo
import es.olaverde.core.routing.Edge
import es.olaverde.core.routing.ModelWaitProvider
import es.olaverde.core.routing.Node
import es.olaverde.core.routing.RoadGraph
import es.olaverde.core.routing.SpeedPolicy
import es.olaverde.core.routing.TimeDependentRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Fase2Test {
    private val o = Simulation.VALLADOLID
    private val model = SignalModel("x", 90.0, 0.0, 40.0, 1.0, 10)

    @Test
    fun `solo cuenta los semaforos que tiene delante`() {
        val signals = listOf(
            KnownSignal("norte", Geo.offsetNorth(o, 300.0), model, 50),
            KnownSignal("norte-lejos", Geo.offsetNorth(o, 900.0), model, 50),
            KnownSignal("sur", Geo.offsetNorth(o, -200.0), model, 50),
            KnownSignal("este", Geo.offsetEast(o, 200.0), model, 30),
            KnownSignal("sin-modelo", Geo.offsetNorth(o, 150.0), null, 30),
        )
        val (ahead, limit) = AheadFinder.find(o, bearingDeg = 0.0, signals = signals)
        assertEquals(1, ahead.size)
        assertEquals(300.0, ahead.single().distanceM, 1.0)
        assertEquals(50, limit)
    }

    @Test
    fun `compara la ruta mas corta con la propuesta`() {
        fun n(id: String, m: Double, sig: String? = null) = Node(id, Geo.offsetNorth(o, m), sig)
        val graph = RoadGraph(
            listOf(n("A", 0.0), n("S", 500.0, "s"), n("B", 1000.0), n("L", 600.0)),
            listOf(
                Edge("A", "S", 500.0, 50), Edge("S", "B", 500.0, 50),
                Edge("A", "L", 650.0, 50), Edge("L", "B", 650.0, 50),
            ),
        )
        val shortest = TimeDependentRouter.shortest(graph, "A", "B")!!
        assertEquals(listOf("A", "S", "B"), shortest)
        val waits = ModelWaitProvider { id, t -> SignalModel(id, 90.0, (t + 60).mod(90.0), 60.0, 1.0, 10) }
        val router = TimeDependentRouter(graph, SpeedPolicy(), waits)
        val best = router.route("A", "B", 0.0)!!
        val base = router.evaluate(shortest, 0.0)!!
        assertEquals(listOf("A", "L", "B"), best.nodeIds)
        assertTrue(base.durationSec > best.durationSec)
        assertEquals(1, base.signals)
    }

    @Test
    fun `frases de voz`() {
        assertEquals(
            "Mantén 45 kilómetros por hora para pillar los próximos 3 semáforos en verde.",
            VoicePrompts.greenWave(GreenWaveAdvice(43, 35, 50, 3, 3)),
        )
        // El redondeo no se sale del rango válido.
        assertEquals(37, VoicePrompts.easySpeed(GreenWaveAdvice(37, 36, 39, 2, 2)))
        assertTrue(VoicePrompts.route(2, 0.4).contains("0,4 kilómetros"))
    }
}
