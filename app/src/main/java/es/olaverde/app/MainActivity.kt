package es.olaverde.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { HomeScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val store = remember { TripStore(context) }
    val recording by TripRecorderService.recording
    val points by TripRecorderService.points
    var trips by remember { mutableStateOf(store.trips()) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) TripRecorderService.start(context)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ola Verde", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Graba tus viajes para aprender los semáforos. Inicia antes de arrancar y no toques el móvil en marcha.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (recording) {
                    TripRecorderService.stop(context)
                    trips = store.trips()
                } else {
                    launcher.launch(permissions)
                }
            },
        ) { Text(if (recording) "Detener viaje" else "Iniciar viaje") }
        if (recording) Text("Grabando · $points puntos GPS")

        Text("Viajes guardados: ${trips.size}", style = MaterialTheme.typography.titleMedium)
        val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(trips, key = { it.name }) { f ->
                val start = f.nameWithoutExtension.toLongOrNull() ?: 0L
                Text("${fmt.format(Date(start))} · ${f.length() / 1024} KB")
            }
        }
    }
}
