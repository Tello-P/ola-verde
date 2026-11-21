package es.olaverde.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.olaverde.app.ui.AppScaffold
import es.olaverde.app.ui.DriveScreen
import es.olaverde.app.ui.OlaVerdeTheme
import es.olaverde.app.ui.TripsContent
import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon
import es.olaverde.core.stats.TripStats
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Genera capturas de las pantallas en app/build/capturas/.
 * El mapa real (MapLibre, nativo) no se puede dibujar aquí: se sustituye por un plano simulado.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CapturasTest {
    private val now = 1_795_300_000_000L // sábado 21/11/2026 ~19:00

    @Before
    fun reset() {
        RecordingState.end()
        RecordingState.trace.clear()
        RecordingState.lastPosition.value = null
    }

    @Test fun conducir_listo() = shot("conducir_listo", dark = false) { drive() }

    @Test fun conducir_grabando() {
        fakeTrip()
        shot("conducir_grabando", dark = false) { drive() }
    }

    @Test fun conducir_grabando_oscuro() {
        fakeTrip()
        shot("conducir_grabando_oscuro", dark = true) { drive() }
    }

    @Test fun viajes() = shot("viajes", dark = false, tab = 1) {
        TripsContent(
            listOf(
                trip(now - 3_600_000, 21 * 60 + 40, 7_850.0, 6, 210),
                trip(now - 26 * 3_600_000, 24 * 60 + 5, 7_420.0, 9, 330),
                trip(now - 50 * 3_600_000, 19 * 60 + 12, 8_310.0, 4, 120),
            ),
            onDelete = {},
        )
    }

    @Test fun viajes_vacio() = shot("viajes_vacio", dark = false, tab = 1) { TripsContent(emptyList(), onDelete = {}) }

    @Composable
    private fun drive() = DriveScreen(
        hasLocationPermission = true,
        onStart = {},
        onStop = {},
        map = { FakeMap(it) },
        liveClock = false,
    )

    private fun shot(name: String, dark: Boolean, tab: Int = 0, content: @Composable () -> Unit) =
        captureRoboImage("build/capturas/$name.png") {
            OlaVerdeTheme(darkTheme = dark) { AppScaffold(tab = tab, onTab = {}) { content() } }
        }

    private fun trip(start: Long, durSec: Long, meters: Double, stops: Int, stoppedSec: Long) =
        File("$start.csv") to TripStats(start, durSec, meters, stops, stoppedSec)

    /** 12 min 34 s de viaje, 4,2 km, 38 km/h, 3 paradas. */
    private fun fakeTrip() {
        RecordingState.begin(System.currentTimeMillis() - 754_000)
        var p = LatLon(41.6440, -4.7300)
        for (i in 0 until 40) {
            p = if (i < 20) Geo.offsetNorth(p, 15.0) else LatLon(p.lat, p.lon + 0.00018)
            RecordingState.add(i * 1000L, p, 10.0)
        }
        RecordingState.distanceM.doubleValue = 4_200.0
        RecordingState.speedKmh.doubleValue = 38.0
        RecordingState.stops.value = 3
    }
}

/** Plano urbano simulado con el recorrido en verde y la posición. */
@Composable
private fun FakeMap(modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFEDEAE3))
            val block = size.width / 5
            for (i in 0..12) {
                val y = i * block * 0.9f
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y + 40f), strokeWidth = 18f)
            }
            for (i in 0..6) {
                val x = i * block
                drawLine(Color.White, Offset(x, 0f), Offset(x - 30f, size.height), strokeWidth = 18f)
            }
            drawLine(Color(0xFFFFE9A8), Offset(0f, size.height * 0.22f), Offset(size.width, size.height * 0.30f), strokeWidth = 34f)
            drawRect(Color(0xFFC9E7C3), topLeft = Offset(block * 3.2f, block * 1.1f), size = androidx.compose.ui.geometry.Size(block * 1.4f, block * 1.6f))
            val path = Path().apply {
                moveTo(block * 1f, size.height * 0.62f)
                lineTo(block * 1.02f, size.height * 0.40f)
                lineTo(block * 2.98f, size.height * 0.42f)
                lineTo(block * 3f, size.height * 0.30f)
            }
            drawPath(path, Color(0xFF0B7A4B), style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val me = Offset(block * 3f, size.height * 0.30f)
            drawCircle(Color.White, radius = 26f, center = me)
            drawCircle(Color(0xFF2EBD6B), radius = 18f, center = me)
        }
    }
}
