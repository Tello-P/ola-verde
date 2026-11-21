package es.olaverde.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.olaverde.app.RecordingState
import es.olaverde.app.RouteResult
import es.olaverde.core.advice.GreenWaveAdvice
import es.olaverde.core.advice.VoicePrompts
import es.olaverde.core.geo.LatLon
import kotlinx.coroutines.delay

@Composable
fun DriveScreen(
    hasLocationPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    /** Sustituible en capturas y tests, donde el mapa nativo no puede dibujarse. */
    map: (@Composable (Modifier) -> Unit)? = null,
    liveClock: Boolean = true,
    onPickDestination: (LatLon) -> Unit = {},
    onClearRoute: () -> Unit = {},
) {
    val recording by RecordingState.recording
    val start by RecordingState.startMillis
    val distance by RecordingState.distanceM
    val speed by RecordingState.speedKmh
    val stops by RecordingState.stops
    val position by RecordingState.lastPosition
    val advice by RecordingState.advice
    val signals by RecordingState.signals
    val route by RecordingState.route
    val destination by RecordingState.destination
    val routeStatus by RecordingState.routeStatus

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording, liveClock) {
        while (recording && liveClock) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val mapModifier = Modifier.fillMaxSize()
        if (map != null) map(mapModifier)
        else TripMap(
            trace = RecordingState.trace,
            position = position,
            follow = recording,
            modifier = mapModifier,
            signals = signals,
            route = route?.points,
            destination = destination,
            onLongPress = onPickDestination,
        )

        val currentAdvice = advice
        if (recording && currentAdvice != null) {
            AdviceCard(currentAdvice, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp))
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusLine(recording)

                if (recording) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Tiempo", clock(((now - start) / 1000).coerceAtLeast(0)))
                        Stat("Distancia", "${km(distance)} km")
                        Stat("Velocidad", "${speed.toInt()} km/h")
                        Stat("Paradas", "$stops")
                    }
                } else {
                    Text(
                        if (hasLocationPermission) {
                            "Pulsa antes de arrancar. Mantén pulsado el mapa para elegir destino y te propongo la ruta con menos esperas."
                        } else {
                            "Necesitamos tu ubicación para grabar el viaje. Solo se usa mientras grabas."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val currentRoute = route
                if (currentRoute != null) RouteSummary(currentRoute, onClearRoute)
                else if (routeStatus != null) {
                    Text(routeStatus.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                BigButton(recording, onClick = if (recording) onStop else onStart)

                if (recording) {
                    Text(
                        "No toques el móvil mientras conduces. Puedes detener el viaje desde la notificación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdviceCard(advice: GreenWaveAdvice, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), shadowElevation = 8.dp, tonalElevation = 2.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).background(Signal.green, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${VoicePrompts.easySpeed(advice)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("km/h", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Ola verde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (advice.greensInRow == 1) "A este ritmo pillas el próximo semáforo en verde"
                    else "A este ritmo pillas ${advice.greensInRow} semáforos seguidos en verde",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Vale entre ${advice.minKmh} y ${advice.maxKmh} km/h",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RouteSummary(route: RouteResult, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${duration(route.durationSec.toLong())} · ${km(route.distanceM)} km · ${route.signals} semáforos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val saved = (route.savedSec / 60).toInt()
                Text(
                    if (saved >= 1) "Ahorras $saved min frente a la más corta (${km(route.shortestDistanceM)} km)"
                    else "Igual de rápida que la ruta más corta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onClear) { Text("Quitar") }
        }
    }
}

@Composable
private fun StatusLine(recording: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).background(if (recording) Signal.red else Signal.green, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (recording) "Grabando viaje" else "Listo para grabar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigButton(recording: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(if (recording) Icons.Filled.Close else Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            if (recording) "Terminar viaje" else "Empezar viaje",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Unspecified,
        )
    }
}
