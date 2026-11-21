package es.olaverde.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import es.olaverde.core.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import es.olaverde.app.ui.AppScaffold
import es.olaverde.app.ui.DriveScreen
import es.olaverde.app.ui.OlaVerdeTheme
import es.olaverde.app.ui.SettingsScreen
import es.olaverde.app.ui.TripsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Settings.load(this)
        setContent { OlaVerdeTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { TripStore(context) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        hasPermission = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPermission) TripRecorderService.start(context)
    }

    // Posición inicial para el mapa y las rutas antes de empezar a grabar.
    LaunchedEffect(hasPermission) {
        if (hasPermission) lastKnownLocation(context)
    }
    val scope = rememberCoroutineScope()

    AppScaffold(tab = tab, onTab = { tab = it }) {
        when (tab) {
            0 -> DriveScreen(
                hasLocationPermission = hasPermission,
                onStart = {
                    if (hasPermission) TripRecorderService.start(context) else launcher.launch(permissions)
                },
                onStop = { TripRecorderService.stop(context) },
                onPickDestination = { dest -> scope.launch { requestRoute(dest) } },
                onClearRoute = {
                    RecordingState.route.value = null
                    RecordingState.destination.value = null
                    RecordingState.routeStatus.value = null
                },
            )
            1 -> TripsScreen(store)
            else -> SettingsScreen(onChanged = { Settings.save(context) })
        }
    }
}

@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context) {
    try {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
            if (loc != null && RecordingState.lastPosition.value == null) {
                RecordingState.lastPosition.value = LatLon(loc.latitude, loc.longitude)
            }
        }
    } catch (_: SecurityException) {
    }
}

private suspend fun requestRoute(dest: LatLon) {
    val state = RecordingState
    state.destination.value = dest
    state.route.value = null
    val url = Settings.serverUrl.value
    val from = state.lastPosition.value
    state.routeStatus.value = when {
        url.isBlank() -> "Configura el servidor en Ajustes para calcular rutas."
        from == null -> "Aún no sé dónde estás. Activa la ubicación."
        else -> "Calculando la ruta con menos esperas…"
    }
    if (url.isBlank() || from == null) return
    val preferred = Settings.preferred.toMap()
    val result = withContext(Dispatchers.IO) {
        runCatching { ApiClient(url).route(from, dest, System.currentTimeMillis(), preferred) }
    }
    result.onSuccess {
        state.route.value = it
        state.routeStatus.value = null
    }.onFailure {
        state.routeStatus.value = "No se pudo calcular la ruta: ${it.message}"
    }
}
