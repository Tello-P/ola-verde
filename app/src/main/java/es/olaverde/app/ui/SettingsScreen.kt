package es.olaverde.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.olaverde.app.Settings

/** [onChanged] se llama tras cada cambio para guardar. */
@Composable
fun SettingsScreen(onChanged: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Section("Servidor") {
            OutlinedTextField(
                value = Settings.serverUrl.value,
                onValueChange = { Settings.serverUrl.value = it; onChanged() },
                label = { Text("URL del servidor") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Al terminar cada viaje se envía para aprender los semáforos. No se guarda la traza, solo las paradas en semáforos.")
        }

        Section("Voz") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Decir la velocidad de ola verde", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = Settings.voice.value, onCheckedChange = { Settings.voice.value = it; onChanged() })
            }
        }

        Section("Velocidad para calcular rutas") {
            Hint("La app calcula los tiempos con la velocidad que elijas. Nunca por encima del límite; en tramos con radar usa la del radar.")
            for (limit in Settings.LIMITS) {
                val v = Settings.preferred[limit] ?: limit
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Calles de $limit", Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = v.toFloat(),
                        onValueChange = { Settings.preferred[limit] = (Math.round(it / 5f) * 5).coerceIn(10, limit); onChanged() },
                        valueRange = 10f..limit.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text("$v km/h", Modifier.width(72.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
