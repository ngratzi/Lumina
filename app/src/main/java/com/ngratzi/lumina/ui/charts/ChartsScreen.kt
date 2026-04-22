package com.ngratzi.lumina.ui.charts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.theme.SkyPalette
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import java.io.File

@Composable
fun ChartsScreen(
    innerPadding: PaddingValues,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val palette  = LocalSkyTheme.current.palette
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    // ── Compass heading from rotation-vector sensor ───────────────────────────
    var compassBearing by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                compassBearing = (deg + 360f) % 360f
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    // Tracks the map's current rotation so the compass rose stays in sync.
    val mapRotationState = remember { mutableStateOf(0f) }

    // osmdroid config must run before MapView is constructed.
    // Capture location now (already loaded by splash) so we can center immediately.
    val initialLocation = uiState.location
    val mapView = remember(context) {
        Configuration.getInstance().apply {
            userAgentValue      = "Lumina/1.0"
            osmdroidTileCache   = File(context.cacheDir, "osmdroid")
            tileFileSystemCacheMaxBytes = 100L * 1024 * 1024  // 100 MB cap
            tileFileSystemCacheTrimBytes = 80L * 1024 * 1024  // trim to 80 MB when over cap
        }
        MapView(context).apply {
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(13.0)
            initialLocation?.let { loc ->
                controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
            }
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
            overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
            overlays.add(MapOrientationWatcher { mapRotationState.value = it })
        }
    }

    // All overlay instances are created once here and reused for every layer
    // switch. Creating/destroying MapTileProviderBasic on each switch caused
    // accumulated stale providers that corrupted tile loading across layers.
    val accentArgb = palette.accent.toArgb()
    val locationOverlay = remember(mapView) {
        DirectionOverlay(
            context     = context,
            arrowBitmap = locationArrowBitmap(context, accentArgb),
            dotBitmap   = locationDotBitmap(context, accentArgb),
        )
    }
    val openSeaMapOverlay = remember(context) { tilesOverlay(context, OpenSeaMapSource) }

    // Push compass bearing into the overlay every time it changes
    LaunchedEffect(compassBearing) {
        locationOverlay.compassBearing = compassBearing
        mapView.postInvalidate()
    }

    LaunchedEffect(uiState.selectedLayer) {
        applyLayer(mapView, uiState.selectedLayer, locationOverlay, openSeaMapOverlay)
    }

    LaunchedEffect(uiState.location, uiState.initialCenterDone) {
        if (!uiState.initialCenterDone) {
            uiState.location?.let { loc ->
                mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                viewModel.onInitialCenterDone()
            }
        }
    }

    MapViewLifecycle(mapView)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            MapPill("CHARTS")
            if (compassBearing != null) {
                MapPill(formatBearing(compassBearing!!))
            } else {
                MapPill(uiState.selectedLayer.description)
            }
        }

        // ── Compass rose ──────────────────────────────────────────────────────
        CompassRose(
            rotation = mapRotationState.value,
            palette  = palette,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 52.dp, end = 16.dp)
                .size(44.dp),
        )

        // ── Layer picker ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChartLayer.entries.forEach { layer ->
                val selected = layer == uiState.selectedLayer
                FilterChip(
                    selected = selected,
                    onClick  = { viewModel.setLayer(layer) },
                    label    = { Text(layer.displayName, style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = 0.85f),
                        selectedLabelColor     = palette.gradientTop,
                        containerColor         = palette.surfaceDim.copy(alpha = 0.85f),
                        labelColor             = palette.onSurface,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled             = true,
                        selected            = selected,
                        borderColor         = palette.outlineColor,
                        selectedBorderColor = palette.accent,
                    ),
                )
            }
        }

        // ── Map controls ──────────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapControlButton(
                icon    = Icons.Rounded.MyLocation,
                palette = palette,
                loading = uiState.isLocating,
                onClick = {
                    viewModel.locateMe()
                    uiState.location?.let {
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            MapControlButton(Icons.Rounded.Add,    palette) { mapView.controller.zoomIn() }
            MapControlButton(Icons.Rounded.Remove, palette) { mapView.controller.zoomOut() }
        }
    }
}

// ─── Layer application ────────────────────────────────────────────────────────

private fun applyLayer(
    map: MapView,
    layer: ChartLayer,
    locationOverlay: DirectionOverlay,
    openSeaMapOverlay: TilesOverlay,
) {
    // Only remove overlays we own — never wipe the list, which would remove
    // osmdroid's own base-tiles overlay and break tile rendering.
    map.overlays.remove(openSeaMapOverlay)
    map.overlays.remove(locationOverlay)

    when (layer) {
        ChartLayer.NAUTICAL -> {
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.overlays.add(openSeaMapOverlay)
        }
        ChartLayer.OCEAN -> {
            map.setTileSource(EsriOceanSource)
            map.overlays.add(openSeaMapOverlay)
        }
        ChartLayer.SATELLITE -> {
            map.setTileSource(EsriImagerySource)
            map.overlays.add(openSeaMapOverlay)
        }
    }

    map.overlays.add(locationOverlay)
    map.invalidate()
}

private fun tilesOverlay(
    context: android.content.Context,
    source: OnlineTileSourceBase,
): TilesOverlay = TilesOverlay(MapTileProviderBasic(context, source), context).apply {
    loadingBackgroundColor = android.graphics.Color.TRANSPARENT
    loadingLineColor       = android.graphics.Color.TRANSPARENT
}

// ─── Tile sources ─────────────────────────────────────────────────────────────

/**
 * OpenSeaMap seamark overlay — navigational marks: buoys, lights, beacons,
 * wrecks, rocks. Rendered over any base map. Standard {z}/{x}/{y} TMS format.
 */
private val OpenSeaMapSource = object : OnlineTileSourceBase(
    "OpenSeaMap", 2, 18, 256, ".png",
    arrayOf("https://tiles.openseamap.org/seamark/"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}" +
        "/${MapTileIndex.getX(pMapTileIndex)}" +
        "/${MapTileIndex.getY(pMapTileIndex)}.png"
}

/**
 * ESRI World Ocean Base — global bathymetric basemap.
 * Depth contours, depth labels, and soundings from GEBCO + NOAA sources.
 * Same ArcGIS tile format as World Imagery: {z}/{y}/{x} (y before x).
 */
private val EsriOceanSource = object : OnlineTileSourceBase(
    "ESRI Ocean", 1, 17, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Ocean/World_Ocean_Base/MapServer/tile"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}/${MapTileIndex.getZoom(pMapTileIndex)}" +
        "/${MapTileIndex.getY(pMapTileIndex)}" +
        "/${MapTileIndex.getX(pMapTileIndex)}"
}

/**
 * ESRI World Imagery — true-colour satellite tiles.
 * ArcGIS REST format: {z}/{y}/{x}.
 */
private val EsriImagerySource = object : OnlineTileSourceBase(
    "ESRI Imagery", 1, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}/${MapTileIndex.getZoom(pMapTileIndex)}" +
        "/${MapTileIndex.getY(pMapTileIndex)}" +
        "/${MapTileIndex.getX(pMapTileIndex)}"
}

// ─── Custom direction overlay ─────────────────────────────────────────────────

/**
 * Draws a GPS-positioned arrow that rotates to the compass bearing.
 * Unlike MyLocationNewOverlay, this always shows the arrow — it does not
 * fall back to a static dot when GPS bearing is unavailable.
 */
private class DirectionOverlay(
    context: Context,
    private val arrowBitmap: Bitmap,
    private val dotBitmap: Bitmap,
) : org.osmdroid.views.overlay.Overlay(),
    org.osmdroid.views.overlay.mylocation.IMyLocationConsumer {

    var compassBearing: Float? = null
    private var location: android.location.Location? = null
    private val provider = GpsMyLocationProvider(context).also { it.startLocationProvider(this) }

    fun destroy() { provider.stopLocationProvider() }

    override fun onLocationChanged(
        location: android.location.Location?,
        source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?,
    ) { this.location = location }

    override fun draw(
        canvas: android.graphics.Canvas,
        mapView: MapView,
        shadow: Boolean,
    ) {
        val loc = location ?: return
        val pt  = mapView.projection.toPixels(GeoPoint(loc.latitude, loc.longitude), null)
        val x   = pt.x.toFloat()
        val y   = pt.y.toFloat()
        val bearing = compassBearing
        val bmp = if (bearing != null) arrowBitmap else dotBitmap

        canvas.save()
        if (bearing != null) canvas.rotate(bearing, x, y)
        canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f, null)
        canvas.restore()
    }
}

// ─── Location icons ───────────────────────────────────────────────────────────

/** Static dot — shown when no heading is available. */
private fun locationDotBitmap(context: android.content.Context, accentArgb: Int): Bitmap {
    val dp   = context.resources.displayMetrics.density
    val size = (28 * dp).toInt()
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv   = android.graphics.Canvas(bmp)
    val cx   = size / 2f
    val cy   = size / 2f
    val r    = size * 0.32f

    // Halo
    cv.drawCircle(cx, cy, size * 0.46f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb; alpha = 50
        })
    // Fill
    cv.drawCircle(cx, cy, r,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
        })
    // White border
    cv.drawCircle(cx, cy, r,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE
            strokeWidth = size * 0.07f
        })
    return bmp
}

/**
 * Sharp navigation arrow — points up (north). osmdroid rotates this bitmap
 * automatically to match the GPS/compass heading.
 *
 * Shape: elongated teardrop with a deep rear notch, like a Google Maps
 * navigation cursor. The point is unmistakably directional.
 */
private fun locationArrowBitmap(context: android.content.Context, accentArgb: Int): Bitmap {
    val dp   = context.resources.displayMetrics.density
    val size = (52 * dp).toInt()
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv   = android.graphics.Canvas(bmp)
    val cx   = size / 2f
    val cy   = size / 2f

    // Dimensions
    val tip    = cy - size * 0.44f   // sharp tip (top)
    val base   = cy + size * 0.40f   // base corners Y
    val hw     = size * 0.28f        // half-width at base
    val notch  = cy + size * 0.14f   // rear notch Y (deep inward cut)

    // Drop shadow
    cv.drawPath(android.graphics.Path().apply {
        moveTo(cx, tip + size * 0.03f)
        lineTo(cx + hw, base + size * 0.03f)
        lineTo(cx, notch + size * 0.03f)
        lineTo(cx - hw, base + size * 0.03f)
        close()
    }, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; alpha = 55
        maskFilter = android.graphics.BlurMaskFilter(size * 0.08f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })

    // Accent fill
    val arrowPath = android.graphics.Path().apply {
        moveTo(cx, tip)
        lineTo(cx + hw, base)
        lineTo(cx, notch)
        lineTo(cx - hw, base)
        close()
    }
    cv.drawPath(arrowPath, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = accentArgb
        style = android.graphics.Paint.Style.FILL
    })

    // White outline
    cv.drawPath(arrowPath, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color       = android.graphics.Color.WHITE
        style       = android.graphics.Paint.Style.STROKE
        strokeWidth = size * 0.06f
        alpha       = 230
    })

    return bmp
}

// ─── Compass heading format ───────────────────────────────────────────────────

private fun formatBearing(deg: Float): String {
    val cardinal = when (((deg + 22.5f) / 45f).toInt() % 8) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }
    return "$cardinal  ${deg.toInt()}°"
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

@Composable
private fun MapPill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.45f),
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelSmall,
            color    = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    palette: SkyPalette,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick         = onClick,
        shape           = CircleShape,
        color           = palette.surfaceDim.copy(alpha = 0.92f),
        modifier        = Modifier.size(44.dp),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = palette.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = null, tint = palette.onSurface,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── Map orientation watcher ──────────────────────────────────────────────────

/**
 * Thin overlay that fires [onOrientation] with the map's current rotation angle
 * (degrees, clockwise) on every draw pass. Used to keep the compass rose in sync.
 */
private class MapOrientationWatcher(
    private val onOrientation: (Float) -> Unit,
) : org.osmdroid.views.overlay.Overlay() {
    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        onOrientation(mapView.mapOrientation)
    }
}

// ─── Compass rose ─────────────────────────────────────────────────────────────

@Composable
private fun CompassRose(rotation: Float, palette: SkyPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension / 2f

        // Background disc
        drawCircle(Color.Black.copy(alpha = 0.50f), r - 1.dp.toPx(), Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.18f), r - 1.dp.toPx(), Offset(cx, cy),
            style = Stroke(0.5.dp.toPx()))

        // Tick marks at N / E / S / W
        val tickOuter = r - 2.dp.toPx()
        val tickInner = r - 6.dp.toPx()
        rotate(-rotation, Offset(cx, cy)) {
            for (deg in listOf(0f, 90f, 180f, 270f)) {
                rotate(deg, Offset(cx, cy)) {
                    drawLine(
                        color       = Color.White.copy(alpha = 0.45f),
                        start       = Offset(cx, cy - tickOuter),
                        end         = Offset(cx, cy - tickInner),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            val needleR = r - 8.dp.toPx()

            // North needle (accent)
            drawPath(Path().apply {
                moveTo(cx, cy - needleR)
                lineTo(cx - needleR * 0.22f, cy + needleR * 0.12f)
                lineTo(cx, cy - needleR * 0.05f)
                lineTo(cx + needleR * 0.22f, cy + needleR * 0.12f)
                close()
            }, palette.accent)

            // South needle (dim white)
            drawPath(Path().apply {
                moveTo(cx, cy + needleR)
                lineTo(cx + needleR * 0.22f, cy - needleR * 0.12f)
                lineTo(cx, cy + needleR * 0.05f)
                lineTo(cx - needleR * 0.22f, cy - needleR * 0.12f)
                close()
            }, Color.White.copy(alpha = 0.35f))

            // Center pin
            drawCircle(Color.White, 2.5.dp.toPx(), Offset(cx, cy))

            // Cardinal labels — drawn on the rotated canvas so they stay with the rose
            val ts   = 7.dp.toPx()
            val tr   = r - 9.5.dp.toPx()   // distance from center to label center
            val base = ts * 0.35f           // baseline offset to visually center text
            val cardinalPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = ts
                textAlign = android.graphics.Paint.Align.CENTER
                typeface  = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.apply {
                    cardinalPaint.color = palette.accent.toArgb()
                    drawText("N", cx, cy - tr + base, cardinalPaint)
                    cardinalPaint.color = android.graphics.Color.argb(180, 255, 255, 255)
                    drawText("S", cx, cy + tr + base, cardinalPaint)
                    drawText("E", cx + tr, cy + base, cardinalPaint)
                    drawText("W", cx - tr, cy + base, cardinalPaint)
                }
            }
        }
    }
}

// ─── Lifecycle ────────────────────────────────────────────────────────────────

@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
}
