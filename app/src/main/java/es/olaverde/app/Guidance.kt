package es.olaverde.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import es.olaverde.core.advice.AheadFinder
import es.olaverde.core.advice.GreenWaveAdvice
import es.olaverde.core.advice.GreenWaveAdvisor
import es.olaverde.core.advice.VoicePrompts
import es.olaverde.core.geo.Geo
import es.olaverde.core.geo.LatLon
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Durante el viaje: descarga los semáforos cercanos con sus modelos, calcula la velocidad de
 * ola verde (siempre ≤ límite) y la dice en voz alta cuando cambia. Todo en el hilo principal
 * salvo la descarga.
 */
class Guidance(context: Context) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val advisor = GreenWaveAdvisor()
    private val speaker = Speaker(context)

    private var fetchCenter: LatLon? = null
    private var fetchAt = 0L
    private var fetching = false

    fun onLocation(tMillis: Long, pos: LatLon, speedMps: Double?, bearingDeg: Double?) {
        refreshIfNeeded(pos, tMillis)
        val advice = if (bearingDeg != null && (speedMps ?: 0.0) > 2.0) {
            val (ahead, limit) = AheadFinder.find(pos, bearingDeg, RecordingState.signals.value)
            advisor.advise(tMillis / 1000.0, limit, ahead)
        } else null
        RecordingState.advice.value = advice
        if (advice != null && Settings.voice.value) speaker.maybeSay(advice)
    }

    private fun refreshIfNeeded(pos: LatLon, now: Long) {
        val url = Settings.serverUrl.value
        if (url.isBlank() || fetching) return
        val moved = fetchCenter?.let { Geo.distanceM(it, pos) } ?: Double.MAX_VALUE
        if (moved < 800 && now - fetchAt < 120_000) return
        fetching = true
        io.execute {
            val result = runCatching { ApiClient(url).signals(pos, 1_500.0, now) }
            main.post {
                fetching = false
                fetchAt = now
                result.onSuccess { RecordingState.signals.value = it; fetchCenter = pos }
            }
        }
    }

    fun close() {
        speaker.close()
        io.shutdown()
    }
}

/** Lee los consejos sin repetirse: solo cuando cambian y como mucho uno cada 20 s. */
private class Speaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.forLanguageTag("es-ES") }
    private var lastKey: Pair<Int, Int>? = null
    private var lastAt = 0L

    fun maybeSay(a: GreenWaveAdvice) {
        val key = VoicePrompts.easySpeed(a) to a.greensInRow
        val now = System.currentTimeMillis()
        if (!ready || key == lastKey || now - lastAt < 20_000) return
        lastKey = key
        lastAt = now
        tts.speak(VoicePrompts.greenWave(a), TextToSpeech.QUEUE_FLUSH, null, "ola-verde")
    }

    fun close() = tts.shutdown()
}
