package com.ngratzi.lumina.ui.charts

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.Color
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

@Composable
fun ChartsScreen(
    innerPadding: PaddingValues,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val palette = LocalSkyTheme.current.palette
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // osmdroid config must run before MapView is constructed.
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
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
        }
    }

    // All overlay instances are created once here and reused for every layer
    // switch. Creating/destroying MapTileProviderBasic on each switch caused
    // accumulated stale providers that corrupted tile loading across layers.
    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            val bmp = vesselBitmap(context, palette.accent.toArgb())
            setPersonIcon(bmp)
            setDirectionIcon(bmp)
        }
    }
    val openSeaMapOverlay = remember(context) { tilesOverlay(context, OpenSeaMapSource) }

    LaunchedEffect(uiState.selectedLayer) {
        applyLayer(mapView, uiState.selectedLayer, locationOverlay, openSeaMapOverlay)
    }

    LaunchedEffect(uiState.location, uiState.initialCenterDone) {
        if (!uiState.initialCenterDone) {
            uiState.location?.let { loc ->
                mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
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
            MapPill(uiState.selectedLayer.description)
        }

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
    locationOverlay: MyLocationNewOverlay,
    openSeaMapOverlay: TilesOverlay,
) {
    // Only remove overlays we own — never wipe the list, which would remove
    // osmdroid's own base-tiles overlay and break tile rendering.
    map.overlays.remove(openSeaMapOverlay)
    map.overlays.remove(locationOverlay)

    when (layer) {
        ChartLayer.NAUTICAL -> {
            // Street map base + OpenSeaMap nav marks (buoys, lights, beacons).
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.overlays.add(openSeaMapOverlay)
        }
        ChartLayer.OCEAN -> {
            // ESRI World Ocean Base — global bathymetric chart derived from
            // GEBCO + NOAA data. Shows depth contours, depth labels, and
            // soundings at zoom 12+. OpenSeaMap adds nav marks on top.
            map.setTileSource(EsriOceanSource)
            map.overlays.add(openSeaMapOverlay)
        }
        ChartLayer.SATELLITE -> {
            // ESRI true-colour satellite imagery + OpenSeaMap nav marks.
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

// ─── Vessel icon ──────────────────────────────────────────────────────────────

/**
 * Top-down vessel silhouette — pointed bow at the top, rounded stern at the
 * bottom, drawn in the app's accent colour with a glow ring.
 * osmdroid rotates the direction icon to match GPS heading automatically.
 */
private fun vesselBitmap(context: android.content.Context, accentArgb: Int): Bitmap {
    val dp   = context.resources.displayMetrics.density
    val size = (40 * dp).toInt()
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv   = android.graphics.Canvas(bmp)
    val cx   = size / 2f
    val cy   = size / 2f
    val hw   = size * 0.26f
    val fwd  = size * 0.44f
    val aft  = size * 0.40f

    val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color      = accentArgb
        alpha      = 55
        maskFilter = BlurMaskFilter(size * 0.28f, BlurMaskFilter.Blur.NORMAL)
    }
    cv.drawCircle(cx, cy, size * 0.38f, glowPaint)

    val hullPath = android.graphics.Path().apply {
        moveTo(cx, cy - fwd)
        cubicTo(cx + hw, cy - fwd * 0.35f, cx + hw, cy + aft * 0.4f, cx, cy + aft)
        cubicTo(cx - hw, cy + aft * 0.4f, cx - hw, cy - fwd * 0.35f, cx, cy - fwd)
        close()
    }
    cv.drawPath(hullPath, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = accentArgb
        style = android.graphics.Paint.Style.FILL
    })
    cv.drawPath(hullPath, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color       = android.graphics.Color.WHITE
        style       = android.graphics.Paint.Style.STROKE
        strokeWidth = size * 0.055f
        alpha       = 210
    })
    cv.drawLine(cx, cy - fwd * 0.75f, cx, cy + aft * 0.6f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color       = android.graphics.Color.WHITE
            strokeWidth = size * 0.04f
            alpha       = 140
        })

    return bmp
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
            // Called when ChartsScreen leaves composition (navigation away).
            // Without this, every visit creates a new MapView whose tile-download
            // thread pool and GPU resources are never released, leaking memory
            // until the system kills other apps via the low-memory killer.
            mapView.onDetach()
        }
    }
}
