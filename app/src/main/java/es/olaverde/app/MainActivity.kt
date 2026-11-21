package es.olaverde.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import es.olaverde.app.ui.AppScaffold
import es.olaverde.app.ui.DriveScreen
import es.olaverde.app.ui.OlaVerdeTheme
import es.olaverde.app.ui.TripsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    AppScaffold(tab = tab, onTab = { tab = it }) {
        when (tab) {
            0 -> DriveScreen(
                hasLocationPermission = hasPermission,
                onStart = {
                    if (hasPermission) TripRecorderService.start(context) else launcher.launch(permissions)
                },
                onStop = { TripRecorderService.stop(context) },
            )
            else -> TripsScreen(store)
        }
    }
}
