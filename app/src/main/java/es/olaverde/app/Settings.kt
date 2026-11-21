package es.olaverde.app

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import java.util.UUID

/** Ajustes del usuario (SharedPreferences) expuestos como estado de Compose. */
object Settings {
    private const val PREFS = "ajustes"
    val LIMITS = listOf(30, 50, 70, 90, 120)

    val serverUrl = mutableStateOf("")
    val voice = mutableStateOf(true)
    /** Velocidad de cálculo por límite (km/h), siempre ≤ límite. */
    val preferred = mutableStateMapOf<Int, Int>()
    val deviceId = mutableStateOf("")

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serverUrl.value = p.getString("server", "").orEmpty()
        voice.value = p.getBoolean("voice", true)
        LIMITS.forEach { preferred[it] = p.getInt("v$it", it).coerceIn(10, it) }
        deviceId.value = p.getString("device", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("device", it).apply()
        }
    }

    fun save(context: Context) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        e.putString("server", serverUrl.value.trim())
        e.putBoolean("voice", voice.value)
        LIMITS.forEach { e.putInt("v$it", (preferred[it] ?: it).coerceIn(10, it)) }
        e.apply()
    }
}
