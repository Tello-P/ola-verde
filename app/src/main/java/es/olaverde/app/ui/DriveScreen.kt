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
import kotlinx.coroutines.delay

@Composable
fun DriveScreen(
    hasLocationPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    /** Sustituible en capturas y tests, donde el mapa nativo no puede dibujarse. */
    map: (@Composable (Modifier) -> Unit)? = null,
    liveClock: Boolean = true,
) {
    val recording by RecordingState.recording
    val start by RecordingState.startMillis
    val distance by RecordingState.distanceM
    val speed by RecordingState.speedKmh
    val stops by RecordingState.stops
    val position by RecordingState.lastPosition

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
        else TripMap(trace = RecordingState.trace, position = position, follow = recording, modifier = mapModifier)

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
                            "Pulsa antes de arrancar. Cada viaje enseña a la app cómo cambian los semáforos de tu ruta."
                        } else {
                            "Necesitamos tu ubicación para grabar el viaje. Solo se usa mientras grabas."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
