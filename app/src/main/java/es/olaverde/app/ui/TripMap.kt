package es.olaverde.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.olaverde.core.advice.KnownSignal
import es.olaverde.core.geo.LatLon
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Mapa libre (OpenStreetMap, estilo OpenFreeMap): no necesita clave ni tiene coste por uso. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val VALLADOLID = LatLng(41.6523, -4.7245)

/**
 * Mapa con el recorrido grabado y la posición actual.
 * [follow] centra la cámara en la posición en cada actualización.
 */
@Composable
fun TripMap(
    trace: List<LatLon>,
    position: LatLon?,
    follow: Boolean,
    modifier: Modifier = Modifier,
    signals: List<KnownSignal> = emptyList(),
    route: List<LatLon>? = null,
    destination: LatLon? = null,
    onLongPress: ((LatLon) -> Unit)? = null,
) {
    val context = LocalContext.current
    val longPress by rememberUpdatedState(onLongPress)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.setCompassEnabled(false)
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(VALLADOLID, 13.0))
                m.addOnMapLongClickListener { ll ->
                    longPress?.invoke(LatLon(ll.latitude, ll.longitude))
                    longPress != null
                }
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                    for (id in listOf("route", "trace", "me", "dest", "sig-green", "sig-red", "sig-unknown")) {
                        s.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(emptyList())))
                    }
                    s.addLayer(
                        LineLayer("route-line", "route").withProperties(
                            PropertyFactory.lineColor("#1E6FD9"),
                            PropertyFactory.lineWidth(7f),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineJoin("round"),
                        ),
                    )
                    for ((id, color) in listOf("sig-unknown" to "#9AA39D", "sig-red" to "#E53935", "sig-green" to "#2EBD6B")) {
                        s.addLayer(
                            CircleLayer("$id-dot", id).withProperties(
                                PropertyFactory.circleRadius(6f),
                                PropertyFactory.circleColor(color),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleStrokeWidth(2f),
                            ),
                        )
                    }
                    s.addLayer(
                        CircleLayer("dest-dot", "dest").withProperties(
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleColor("#1B1F1D"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(3f),
                        ),
                    )
                    s.addLayer(
                        LineLayer("trace-line", "trace").withProperties(
                            PropertyFactory.lineColor("#0B7A4B"),
                            PropertyFactory.lineWidth(6f),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineJoin("round"),
                        ),
                    )
                    s.addLayer(
                        CircleLayer("me-dot", "me").withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor("#2EBD6B"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(3f),
                        ),
                    )
                    style = s
                }
                map = m
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier) {
        val s = style ?: return@AndroidView
        val points = trace.map { Point.fromLngLat(it.lon, it.lat) }
        s.getSourceAs<GeoJsonSource>("trace")?.let { src ->
            if (points.size >= 2) src.setGeoJson(LineString.fromLngLats(points))
            else src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
        s.getSourceAs<GeoJsonSource>("me")?.let { src ->
            if (position != null) src.setGeoJson(Point.fromLngLat(position.lon, position.lat))
        }
        s.getSourceAs<GeoJsonSource>("route")?.let { src ->
            val r = route?.map { Point.fromLngLat(it.lon, it.lat) }.orEmpty()
            if (r.size >= 2) src.setGeoJson(LineString.fromLngLats(r)) else src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
        s.getSourceAs<GeoJsonSource>("dest")?.let { src ->
            if (destination != null) src.setGeoJson(Point.fromLngLat(destination.lon, destination.lat))
            else src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
        // Color de cada semáforo según la predicción para este momento.
        val nowSec = System.currentTimeMillis() / 1000.0
        val groups = signals.groupBy { sig ->
            val m = sig.model
            when {
                m == null || m.confidence < 0.6 -> "sig-unknown"
                m.isGreen(nowSec) -> "sig-green"
                else -> "sig-red"
            }
        }
        for (id in listOf("sig-green", "sig-red", "sig-unknown")) {
            val feats = groups[id].orEmpty().map { Feature.fromGeometry(Point.fromLngLat(it.pos.lon, it.pos.lat)) }
            s.getSourceAs<GeoJsonSource>(id)?.setGeoJson(FeatureCollection.fromFeatures(feats))
        }
        if (follow && position != null) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.lat, position.lon), 16.0), 600)
        }
    }
}
