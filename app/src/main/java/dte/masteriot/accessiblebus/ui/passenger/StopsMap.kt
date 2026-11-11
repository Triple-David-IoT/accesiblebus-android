package dte.masteriot.accessiblebus.ui.passenger

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dte.masteriot.accessiblebus.core.data.Stop
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * The stops on an OpenStreetMap map (osmdroid), framed automatically. No location permission and
 * no own position. Tiles are cached in internal storage, so the area should be opened once with
 * network before the demo; without network and without tiles the map may be empty, and the list
 * is still there with the same information.
 */
@Composable
fun StopsMap(stops: List<Stop>, onStop: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = rememberMapView(context)

    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        // MapView can draw outside its own bounds; keep it inside its slot of the screen.
        modifier = modifier.clipToBounds().semantics {
            contentDescription = "Mapa con ${stops.size} paradas. La lista tiene la misma información."
        },
        update = { view -> showStops(view, stops, onStop) },
    )
}

@Composable
private fun rememberMapView(context: Context): MapView = remember {
    configureOsmdroid(context)
    MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        minZoomLevel = 12.0
        maxZoomLevel = 19.0
        // Madrid centre until the stops are framed.
        controller.setZoom(15.0)
        controller.setCenter(GeoPoint(40.414, -3.694))
    }
}

/** User agent required by the OSM tile policy, and tile cache inside the app's own files. */
private fun configureOsmdroid(context: Context) {
    val configuration = Configuration.getInstance()
    configuration.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    configuration.userAgentValue = context.packageName
    val base = File(context.filesDir, "osmdroid")
    configuration.osmdroidBasePath = base
    configuration.osmdroidTileCache = File(base, "tiles")
}

private fun showStops(map: MapView, stops: List<Stop>, onStop: (String) -> Unit) {
    map.overlays.removeAll { it is Marker }
    stops.forEach { stop ->
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "${stop.sequence}. ${stop.name}"
                snippet = "Parada ${stop.code}"
                // First tap shows the name; tapping the marker again opens the stop detail.
                setOnMarkerClickListener { marker, _ ->
                    if (marker.isInfoWindowShown) onStop(stop.code) else marker.showInfoWindow()
                    true
                }
            },
        )
    }
    // Frame once: later updates (a new request, a passed stop) must not move the map the user moved.
    if (stops.isNotEmpty() && map.tag == null) {
        map.tag = FRAMED
        val box = BoundingBox.fromGeoPoints(stops.map { GeoPoint(it.latitude, it.longitude) }).increaseByScale(1.4f)
        // Framing needs the view size: wait for the first layout if it has not happened yet.
        if (map.width > 0 && map.height > 0) map.zoomToBoundingBox(box, false) else map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false) }
    }
    map.invalidate()
}

private const val FRAMED = "framed"
