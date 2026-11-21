package es.olaverde.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0B7A4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F1D8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6356),
    error = Color(0xFFC62828),
    onError = Color.White,
    background = Color(0xFFF5F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EAE5),
    onSurfaceVariant = Color(0xFF414943),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EDB9A),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005232),
    onPrimaryContainer = Color(0xFFC4F1D8),
    secondary = Color(0xFFB5CCBC),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F1111),
    background = Color(0xFF0F1412),
    surface = Color(0xFF161C19),
    surfaceVariant = Color(0xFF26302B),
    onSurfaceVariant = Color(0xFFC0C9C2),
)

/** Colores fijos de semáforo para estados (no dependen del tema). */
object Signal {
    val green = Color(0xFF2EBD6B)
    val red = Color(0xFFE53935)
}

@Composable
fun OlaVerdeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
