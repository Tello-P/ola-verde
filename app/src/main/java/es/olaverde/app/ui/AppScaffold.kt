package es.olaverde.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Barra inferior Conducir / Viajes. La pestaña 0 (mapa) llega hasta arriba; las demás respetan la barra de estado. */
@Composable
fun AppScaffold(tab: Int, onTab: (Int) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { onTab(0) },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    label = { Text("Conducir") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTab(1) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Viajes") },
                )
            }
        },
    ) { inner ->
        val base = Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())
        Box(if (tab == 0) base else base.statusBarsPadding()) { content() }
    }
}
