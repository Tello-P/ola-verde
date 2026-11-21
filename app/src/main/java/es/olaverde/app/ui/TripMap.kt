package es.olaverde.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
fun TripMap(trace: List<LatLon>, position: LatLon?, follow: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                    s.addSource(GeoJsonSource("trace", FeatureCollection.fromFeatures(emptyList())))
                    s.addSource(GeoJsonSource("me", FeatureCollection.fromFeatures(emptyList())))
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
        if (follow && position != null) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.lat, position.lon), 16.0), 600)
        }
    }
}
