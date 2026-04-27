package com.ngratzi.lumina.ui.charts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint as AndroidPaint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.data.model.Track
import com.ngratzi.lumina.data.model.TrackColors
import com.ngratzi.lumina.data.model.TrackPoint
import com.ngratzi.lumina.data.model.Waypoint
import com.ngratzi.lumina.data.model.WaypointIcon
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
import kotlin.math.*

private enum class MapOrientationMode { NORTH_UP, HEADING_UP, FREE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    innerPadding: PaddingValues,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val palette  = LocalSkyTheme.current.palette
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    // ── Magnetic declination ──────────────────────────────────────────────────
    val declinationState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(uiState.location) {
        uiState.location?.let { loc ->
            val geo = android.hardware.GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(),
                loc.altitude.toFloat(), System.currentTimeMillis(),
            )
            declinationState.floatValue = geo.declination
        }
    }

    // ── Compass heading ───────────────────────────────────────────────────────
    var compassBearing by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotMatrix   = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val mag = Math.toDegrees(orientation[0].toDouble()).toFloat()
                compassBearing = (mag + declinationState.floatValue + 360f) % 360f
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    var orientationMode by remember { mutableStateOf(MapOrientationMode.FREE) }
    val mapRotationState = remember { mutableStateOf(0f) }
    val accentArgb = palette.accent.toArgb()

    val initialLocation = uiState.location
    val mapView = remember(context) {
        Configuration.getInstance().apply {
            userAgentValue = "Lumina/1.0"
            osmdroidTileCache = File(context.cacheDir, "osmdroid")
            tileFileSystemCacheMaxBytes  = 100L * 1024 * 1024
            tileFileSystemCacheTrimBytes =  80L * 1024 * 1024
        }
        MapView(context).apply {
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(13.0)
            initialLocation?.let { controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
            overlays.add(MapOrientationWatcher { mapRotationState.value = it })
        }
    }

    val locationOverlay = remember(mapView) {
        DirectionOverlay(context, locationArrowBitmap(context, accentArgb), locationDotBitmap(context, accentArgb))
    }
    val waypointOverlay = remember(mapView) {
        WaypointOverlay(context, accentArgb) { wp -> viewModel.selectWaypoint(wp) }
    }
    val trackOverlay = remember(mapView) { TrackOverlay(context) }
    val openSeaMapOverlay = remember(context) { tilesOverlay(context, OpenSeaMapSource) }

    // Sync waypoints + active waypoint + device location into overlay
    LaunchedEffect(uiState.waypoints, uiState.activeWaypoint) {
        waypointOverlay.waypoints      = uiState.waypoints
        waypointOverlay.activeWaypoint = uiState.activeWaypoint
        mapView.postInvalidate()
    }
    // Sync track data into track overlay
    LaunchedEffect(uiState.tracks, uiState.trackPoints) {
        trackOverlay.trackData = uiState.tracks
            .filter { it.isVisible }
            .mapNotNull { t -> uiState.trackPoints[t.id]?.let { pts -> t to pts } }
        mapView.postInvalidate()
    }
    LaunchedEffect(compassBearing) {
        locationOverlay.compassBearing = compassBearing
        mapView.postInvalidate()
    }
    LaunchedEffect(uiState.location) {
        uiState.location?.let {
            waypointOverlay.deviceLocation = GeoPoint(it.latitude, it.longitude)
            mapView.postInvalidate()
        }
    }
    LaunchedEffect(compassBearing, orientationMode) {
        val rotOverlay = mapView.overlays.filterIsInstance<RotationGestureOverlay>().firstOrNull()
        when (orientationMode) {
            MapOrientationMode.NORTH_UP   -> { mapView.mapOrientation = 0f;                       rotOverlay?.isEnabled = false }
            MapOrientationMode.HEADING_UP -> { mapView.mapOrientation = -(compassBearing ?: 0f);  rotOverlay?.isEnabled = false }
            MapOrientationMode.FREE       -> { rotOverlay?.isEnabled = true }
        }
        mapView.postInvalidate()
    }
    LaunchedEffect(uiState.selectedLayer) {
        applyLayer(mapView, uiState.selectedLayer, locationOverlay, waypointOverlay, trackOverlay, openSeaMapOverlay)
    }
    LaunchedEffect(uiState.location, uiState.initialCenterDone) {
        if (!uiState.initialCenterDone) {
            uiState.location?.let {
                mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                viewModel.onInitialCenterDone()
            }
        }
    }

    MapViewLifecycle(mapView)

    // ── Navigation distance + bearing (computed live) ─────────────────────────
    val navInfo = remember(uiState.activeWaypoint, uiState.location) {
        val wp  = uiState.activeWaypoint ?: return@remember null
        val loc = uiState.location       ?: return@remember null
        val distNm  = haversineNm(loc.latitude, loc.longitude, wp.lat, wp.lon)
        val bearing = bearingDeg(loc.latitude, loc.longitude, wp.lat, wp.lon)
        NavInfo(distNm, bearing)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
    ) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ── Crosshair overlay ─────────────────────────────────────────────────
        if (uiState.isPlacingWaypoint) {
            CrosshairOverlay(palette)
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        val activeWaypoint = uiState.activeWaypoint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (uiState.isPlacingWaypoint) {
                MapPill("Drop waypoint · pan to position")
            } else {
                MapPill("CHARTS")
            }

            // Nav tile sits between the two pills when navigating
            if (activeWaypoint != null && navInfo != null && !uiState.isPlacingWaypoint) {
                NavTile(
                    waypoint = activeWaypoint,
                    navInfo  = navInfo,
                    palette  = palette,
                    onStop   = { viewModel.stopNavigation() },
                )
            }

            if (compassBearing != null && !uiState.isPlacingWaypoint) {
                MapPill(formatBearing(compassBearing!!))
            } else if (!uiState.isPlacingWaypoint) {
                MapPill(uiState.selectedLayer.description)
            }
        }

        // ── Compass rose ──────────────────────────────────────────────────────
        if (!uiState.isPlacingWaypoint) {
            CompassRose(
                rotation = mapRotationState.value,
                palette  = palette,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 52.dp, end = 16.dp)
                    .size(44.dp),
            )
        }

        // ── Crosshair confirm / cancel ────────────────────────────────────────
        if (uiState.isPlacingWaypoint) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cancel
                Surface(
                    onClick = { viewModel.cancelPlacingWaypoint() },
                    shape   = CircleShape,
                    color   = palette.surfaceDim.copy(alpha = 0.92f),
                    modifier = Modifier.size(56.dp),
                    shadowElevation = 4.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = palette.onSurface, modifier = Modifier.size(24.dp))
                    }
                }
                // Confirm
                Surface(
                    onClick = {
                        val center = mapView.mapCenter
                        viewModel.confirmCrosshairLocation(center.latitude, center.longitude)
                    },
                    shape   = CircleShape,
                    color   = palette.accent,
                    modifier = Modifier.size(56.dp),
                    shadowElevation = 4.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription = "Confirm", tint = palette.gradientTop, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // ── Layer picker (hidden in placing mode) ─────────────────────────────
        if (!uiState.isPlacingWaypoint) {
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
                            enabled = true, selected = selected,
                            borderColor = palette.outlineColor, selectedBorderColor = palette.accent,
                        ),
                    )
                }
            }
        }

        // ── Map controls (hidden in placing mode) ─────────────────────────────
        if (!uiState.isPlacingWaypoint) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
            ) {
                MapOrientationButton(mode = orientationMode, palette = palette, onClick = {
                    orientationMode = when (orientationMode) {
                        MapOrientationMode.NORTH_UP   -> MapOrientationMode.HEADING_UP
                        MapOrientationMode.HEADING_UP -> MapOrientationMode.FREE
                        MapOrientationMode.FREE       -> MapOrientationMode.NORTH_UP
                    }
                })
                MapControlButton(Icons.Rounded.MyLocation, palette, loading = uiState.isLocating) {
                    viewModel.locateMe()
                    uiState.location?.let { mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
                }
                // Add waypoint
                MapControlButton(Icons.Rounded.AddLocation, palette) { viewModel.startPlacingWaypoint() }
                // Waypoint list
                MapControlButton(Icons.Rounded.List, palette) { viewModel.showWaypointList() }
                // Track list
                MapControlButton(Icons.Rounded.Timeline, palette) { viewModel.showTrackList() }
                // Record toggle
                RecordButton(isRecording = uiState.isRecording, palette = palette) { viewModel.toggleRecording() }
                Spacer(Modifier.height(4.dp))
                MapControlButton(Icons.Rounded.Add, palette) { mapView.controller.zoomIn() }
                MapControlButton(Icons.Rounded.Remove, palette) { mapView.controller.zoomOut() }
            }
        }
    }

    // ── Waypoint editor (name + icon) ─────────────────────────────────────────
    if (uiState.showWaypointEditor) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissWaypointEditor() },
            containerColor   = palette.surfaceContainer,
        ) {
            WaypointEditorSheet(
                palette    = palette,
                title      = "New Waypoint",
                initialName = "",
                initialIcon = WaypointIcon.CROSSHAIR,
                onSave     = { name, icon -> viewModel.saveNewWaypoint(name, icon) },
                onDismiss  = { viewModel.dismissWaypointEditor() },
            )
        }
    }

    // ── Waypoint list sheet ───────────────────────────────────────────────────
    if (uiState.showWaypointList) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideWaypointList() },
            containerColor   = palette.surfaceContainer,
        ) {
            WaypointListSheet(
                palette    = palette,
                waypoints  = uiState.waypoints,
                activeId   = uiState.activeWaypoint?.id,
                deviceLat  = uiState.location?.latitude,
                deviceLon  = uiState.location?.longitude,
                onNavigate = { wp ->
                    viewModel.navigateTo(wp)
                    viewModel.hideWaypointList()
                    mapView.controller.animateTo(GeoPoint(wp.lat, wp.lon))
                },
                onCenter   = { wp ->
                    mapView.controller.animateTo(GeoPoint(wp.lat, wp.lon))
                },
                onDelete   = { viewModel.deleteWaypoint(it) },
            )
        }
    }

    // ── Track list sheet ──────────────────────────────────────────────────────
    if (uiState.showTrackList) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTrackList() },
            containerColor   = palette.surfaceContainer,
        ) {
            TrackListSheet(
                palette         = palette,
                tracks          = uiState.tracks,
                activeTrackId   = uiState.activeTrackId,
                isRecording     = uiState.isRecording,
                recordingDistNm = uiState.recordingDistanceNm,
                onToggleVisible = { viewModel.toggleTrackVisibility(it) },
                onRename        = { t, n -> viewModel.renameTrack(t, n) },
                onSetColor      = { t, c -> viewModel.setTrackColor(t, c) },
                onDelete        = { viewModel.deleteTrack(it) },
            )
        }
    }

    // ── Selected waypoint detail sheet ────────────────────────────────────────
    uiState.selectedWaypoint?.let { wp ->
        val loc = uiState.location
        val dist = if (loc != null) haversineNm(loc.latitude, loc.longitude, wp.lat, wp.lon) else null
        val brg  = if (loc != null) bearingDeg(loc.latitude, loc.longitude, wp.lat, wp.lon) else null

        var showEditSheet by remember(wp.id) { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedWaypoint() },
            containerColor   = palette.surfaceContainer,
        ) {
            WaypointDetailSheet(
                palette    = palette,
                waypoint   = wp,
                distNm     = dist,
                bearingDeg = brg,
                isActive   = uiState.activeWaypoint?.id == wp.id,
                onNavigate = { viewModel.navigateTo(wp) },
                onEdit     = { showEditSheet = true },
                onDelete   = { viewModel.deleteWaypoint(wp.id) },
                onCenter   = { mapView.controller.animateTo(GeoPoint(wp.lat, wp.lon)) },
            )
        }

        if (showEditSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEditSheet = false },
                containerColor   = palette.surfaceContainer,
            ) {
                WaypointEditorSheet(
                    palette      = palette,
                    title        = "Edit Waypoint",
                    initialName  = wp.name,
                    initialIcon  = wp.icon,
                    onSave       = { name, icon ->
                        viewModel.updateWaypoint(wp.copy(name = name.ifBlank { wp.name }, icon = icon))
                        showEditSheet = false
                    },
                    onDismiss    = { showEditSheet = false },
                )
            }
        }
    }
}

// ─── Crosshair overlay ────────────────────────────────────────────────────────

@Composable
private fun CrosshairOverlay(palette: SkyPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = 24.dp.toPx()
        val gap = 6.dp.toPx()
        val sw  = 2.dp.toPx()
        val col = Color.White

        // Circle
        drawCircle(col.copy(alpha = 0.9f), 6.dp.toPx(), Offset(cx, cy), style = Stroke(sw))

        // Four arms
        drawLine(col.copy(alpha = 0.9f), Offset(cx, cy - gap - arm), Offset(cx, cy - gap), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(col.copy(alpha = 0.9f), Offset(cx, cy + gap),       Offset(cx, cy + gap + arm), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(col.copy(alpha = 0.9f), Offset(cx - gap - arm, cy), Offset(cx - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(col.copy(alpha = 0.9f), Offset(cx + gap, cy),       Offset(cx + gap + arm, cy), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

// ─── Navigation card ──────────────────────────────────────────────────────────

private data class NavInfo(val distNm: Double, val bearingDeg: Double)

@Composable
private fun NavTile(
    waypoint: Waypoint,
    navInfo:  NavInfo,
    palette:  SkyPalette,
    onStop:   () -> Unit,
    modifier: Modifier = Modifier,
) {
    val distStr = when {
        navInfo.distNm < 0.1  -> "${"%.0f".format(navInfo.distNm * 6076)} ft"
        navInfo.distNm < 10.0 -> "${"%.1f".format(navInfo.distNm)} nm"
        else                  -> "${"%.0f".format(navInfo.distNm)} nm"
    }
    val brgStr = "${cardinalFromDeg(navInfo.bearingDeg)}  ${"%.0f".format(navInfo.bearingDeg)}°"

    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier.clip(shape).border(0.5.dp, palette.outlineColor, shape),
        color    = Color.Black.copy(alpha = 0.60f),
        shape    = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(waypoint.icon.symbol, fontSize = 16.sp)
            Column {
                Text(
                    waypoint.name,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(distStr, style = MaterialTheme.typography.labelSmall, color = palette.accent)
                    Text(brgStr,  style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
            IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Stop", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─── Waypoint editor sheet ────────────────────────────────────────────────────

@Composable
private fun WaypointEditorSheet(
    palette:     SkyPalette,
    title:       String,
    initialName: String,
    initialIcon: WaypointIcon,
    onSave:      (String, WaypointIcon) -> Unit,
    onDismiss:   () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface,
            modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value         = name,
            onValueChange = { name = it },
            label         = { Text("Name") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = palette.accent,
                unfocusedBorderColor    = palette.outlineColor,
                focusedLabelColor       = palette.accent,
                unfocusedLabelColor     = palette.onSurfaceVariant,
                focusedTextColor        = palette.onSurface,
                unfocusedTextColor      = palette.onSurface,
                cursorColor             = palette.accent,
                focusedContainerColor   = palette.surfaceDim,
                unfocusedContainerColor = palette.surfaceDim,
            ),
        )

        Spacer(Modifier.height(16.dp))
        Text("Icon", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // 3-column icon grid
        val icons = WaypointIcon.entries
        for (row in icons.chunked(3)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ic ->
                    val selected = ic == icon
                    Surface(
                        onClick  = { icon = ic },
                        shape    = RoundedCornerShape(12.dp),
                        color    = if (selected) palette.accent.copy(alpha = 0.2f) else palette.surfaceDim,
                        modifier = Modifier.weight(1f).aspectRatio(1.6f)
                            .border(
                                if (selected) 1.5.dp else 0.5.dp,
                                if (selected) palette.accent else palette.outlineColor,
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Column(
                            modifier            = Modifier.fillMaxSize().padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(ic.symbol, fontSize = 18.sp)
                            Text(ic.label, style = MaterialTheme.typography.labelSmall,
                                color = if (selected) palette.accent else palette.onSurfaceVariant,
                                maxLines = 1)
                        }
                    }
                }
                // pad incomplete rows
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = palette.onSurface),
                border   = androidx.compose.foundation.BorderStroke(0.5.dp, palette.outlineColor),
            ) { Text("Cancel") }
            Button(
                onClick  = { onSave(name, icon) },
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) { Text("Save", color = palette.gradientTop) }
        }
    }
}

// ─── Waypoint list sheet ──────────────────────────────────────────────────────

@Composable
private fun WaypointListSheet(
    palette:   SkyPalette,
    waypoints: List<Waypoint>,
    activeId:  Long?,
    deviceLat: Double?,
    deviceLon: Double?,
    onNavigate:(Waypoint) -> Unit,
    onCenter:  (Waypoint) -> Unit,
    onDelete:  (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp)) {
        Text("Waypoints", style = MaterialTheme.typography.titleMedium, color = palette.onSurface,
            modifier = Modifier.padding(bottom = 12.dp))

        if (waypoints.isEmpty()) {
            Text("No waypoints yet. Tap + to add one.",
                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(waypoints, key = { it.id }) { wp ->
                    val dist = if (deviceLat != null && deviceLon != null)
                        haversineNm(deviceLat, deviceLon, wp.lat, wp.lon) else null
                    val distStr = dist?.let {
                        if (it < 10) "${"%.1f".format(it)} nm" else "${"%.0f".format(it)} nm"
                    }
                    val isActive = wp.id == activeId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onCenter(wp) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(wp.icon.symbol, fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                wp.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) palette.accent else palette.onSurface,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (distStr != null) {
                                Text(distStr, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                            }
                        }
                        TextButton(
                            onClick = { onNavigate(wp) },
                            colors  = ButtonDefaults.textButtonColors(contentColor = palette.accent),
                        ) { Text(if (isActive) "Active" else "Go", style = MaterialTheme.typography.labelSmall) }
                        IconButton(onClick = { onDelete(wp.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = palette.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Waypoint detail sheet ────────────────────────────────────────────────────

@Composable
private fun WaypointDetailSheet(
    palette:    SkyPalette,
    waypoint:   Waypoint,
    distNm:     Double?,
    bearingDeg: Double?,
    isActive:   Boolean,
    onNavigate: () -> Unit,
    onEdit:     () -> Unit,
    onDelete:   () -> Unit,
    onCenter:   () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(waypoint.icon.symbol, fontSize = 28.sp)
            Text(waypoint.name, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
        }
        Spacer(Modifier.height(12.dp))

        if (distNm != null && bearingDeg != null) {
            val distStr = if (distNm < 0.1) "${"%.0f".format(distNm * 6076)} ft"
                          else if (distNm < 10) "${"%.1f".format(distNm)} nm"
                          else "${"%.0f".format(distNm)} nm"
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    Text(distStr, style = MaterialTheme.typography.bodyLarge, color = palette.onSurface)
                }
                Column {
                    Text("BEARING", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    Text("${cardinalFromDeg(bearingDeg)}  ${"%.0f".format(bearingDeg)}°",
                        style = MaterialTheme.typography.bodyLarge, color = palette.onSurface)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isActive) {
                Button(
                    onClick  = onNavigate,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) { Text("Navigate", color = palette.gradientTop) }
            } else {
                OutlinedButton(
                    onClick  = onNavigate,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, palette.accent),
                ) { Text("Navigating ✓") }
            }
            OutlinedButton(
                onClick  = onCenter,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = palette.onSurface),
                border   = androidx.compose.foundation.BorderStroke(0.5.dp, palette.outlineColor),
            ) { Text("Center") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = onEdit,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = palette.onSurface),
                border   = androidx.compose.foundation.BorderStroke(0.5.dp, palette.outlineColor),
            ) { Text("Edit") }
            OutlinedButton(
                onClick  = onDelete,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCC4444)),
                border   = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCC4444).copy(alpha = 0.5f)),
            ) { Text("Delete") }
        }
    }
}

// ─── Waypoint map overlay ─────────────────────────────────────────────────────

private class WaypointOverlay(
    private val context: Context,
    private val accentArgb: Int,
    private val onTap: (Waypoint) -> Unit,
) : org.osmdroid.views.overlay.Overlay() {

    var waypoints:      List<Waypoint> = emptyList()
    var activeWaypoint: Waypoint?      = null
    var deviceLocation: GeoPoint?      = null

    private val pinPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = accentArgb; style = AndroidPaint.Style.FILL
    }
    private val pinBorderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = AndroidPaint.Style.STROKE; strokeWidth = 4f
    }
    private val activePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 255, 200, 50); style = AndroidPaint.Style.FILL
    }
    private val linePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = accentArgb; style = AndroidPaint.Style.STROKE; strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = AndroidPaint.Align.CENTER
        textSize  = context.resources.displayMetrics.density * 13f
        typeface  = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = AndroidPaint.Align.CENTER
        textSize  = context.resources.displayMetrics.density * 10f
        setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        // Dashed bearing line to active waypoint
        activeWaypoint?.let { active ->
            deviceLocation?.let { from ->
                val fromPt = mapView.projection.toPixels(from, null)
                val toPt   = mapView.projection.toPixels(GeoPoint(active.lat, active.lon), null)
                canvas.drawLine(fromPt.x.toFloat(), fromPt.y.toFloat(),
                                toPt.x.toFloat(),   toPt.y.toFloat(), linePaint)
            }
        }

        val dp = context.resources.displayMetrics.density
        val r  = 18f * dp

        waypoints.forEach { wp ->
            val pt   = mapView.projection.toPixels(GeoPoint(wp.lat, wp.lon), null)
            val px   = pt.x.toFloat()
            val py   = pt.y.toFloat()
            val fill = if (wp.id == activeWaypoint?.id) activePaint else pinPaint

            // Pin circle
            canvas.drawCircle(px, py, r, fill)
            canvas.drawCircle(px, py, r, pinBorderPaint)

            // Icon symbol
            canvas.drawText(wp.icon.symbol, px, py + textPaint.textSize * 0.35f, textPaint)

            // Label below
            canvas.drawText(wp.name, px, py + r + labelPaint.textSize + 2f * dp, labelPaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val dp     = context.resources.displayMetrics.density
        val hitR   = 26f * dp
        waypoints.forEach { wp ->
            val pt = mapView.projection.toPixels(GeoPoint(wp.lat, wp.lon), null)
            val dx = e.x - pt.x
            val dy = e.y - pt.y
            if (dx * dx + dy * dy <= hitR * hitR) {
                onTap(wp)
                return true
            }
        }
        return false
    }
}

// ─── Record button ────────────────────────────────────────────────────────────

@Composable
private fun RecordButton(isRecording: Boolean, palette: SkyPalette, onClick: () -> Unit) {
    val bg  = if (isRecording) Color(0xFFCC2222) else palette.surfaceDim.copy(alpha = 0.92f)
    val fg  = if (isRecording) Color.White else palette.onSurface
    val icon = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord
    Surface(onClick = onClick, shape = CircleShape, color = bg,
        modifier = Modifier.size(44.dp), shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = if (isRecording) "Stop recording" else "Start recording",
                tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Track list sheet ─────────────────────────────────────────────────────────

@Composable
private fun TrackListSheet(
    palette:         SkyPalette,
    tracks:          List<Track>,
    activeTrackId:   Long?,
    isRecording:     Boolean,
    recordingDistNm: Double,
    onToggleVisible: (Track) -> Unit,
    onRename:        (Track, String) -> Unit,
    onSetColor:      (Track, Int) -> Unit,
    onDelete:        (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tracks", style = MaterialTheme.typography.titleMedium, color = palette.onSurface,
                modifier = Modifier.weight(1f))
            if (isRecording) {
                Text(
                    "● REC  ${"%.2f".format(recordingDistNm)} nm",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEF5350),
                )
            }
        }

        if (tracks.isEmpty()) {
            Text("No tracks yet. Tap the record button to start.",
                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        palette         = palette,
                        track           = track,
                        isActiveTrack   = track.id == activeTrackId,
                        onToggleVisible = { onToggleVisible(track) },
                        onRename        = { onRename(track, it) },
                        onSetColor      = { onSetColor(track, it) },
                        onDelete        = { onDelete(track.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    palette:         SkyPalette,
    track:           Track,
    isActiveTrack:   Boolean,
    onToggleVisible: () -> Unit,
    onRename:        (String) -> Unit,
    onSetColor:      (Int) -> Unit,
    onDelete:        () -> Unit,
) {
    var showRename by remember(track.id) { mutableStateOf(false) }
    var showColorPicker by remember(track.id) { mutableStateOf(false) }
    var nameInput by remember(track.id) { mutableStateOf(track.name) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { showRename = !showRename }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Color swatch / visibility toggle
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(track.color).copy(alpha = if (track.isVisible) 1f else 0.35f))
                    .clickable { showColorPicker = !showColorPicker }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActiveTrack) palette.accent else palette.onSurface,
                    fontWeight = if (isActiveTrack) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                val durStr = trackDurationStr(track)
                val distStr = "${"%.2f".format(track.totalDistanceNm)} nm"
                Text("$distStr · $durStr",
                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }

            // Show/hide toggle
            IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (track.isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = "Toggle visibility",
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete",
                    tint = palette.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }

        // Inline rename field
        if (showRename) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = palette.accent,
                        unfocusedBorderColor    = palette.outlineColor,
                        focusedLabelColor       = palette.accent,
                        unfocusedLabelColor     = palette.onSurfaceVariant,
                        focusedTextColor        = palette.onSurface,
                        unfocusedTextColor      = palette.onSurface,
                        cursorColor             = palette.accent,
                        focusedContainerColor   = palette.surfaceDim,
                        unfocusedContainerColor = palette.surfaceDim,
                    ),
                )
                TextButton(onClick = {
                    onRename(nameInput)
                    showRename = false
                }) { Text("Save", color = palette.accent) }
            }
        }

        // Inline color picker
        if (showColorPicker) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackColors.forEach { color ->
                    val selected = color == track.color
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(if (selected) 2.dp else 0.dp, Color.White, CircleShape)
                            .clickable {
                                onSetColor(color)
                                showColorPicker = false
                            }
                    )
                }
            }
        }
    }
}

private fun trackDurationStr(track: Track): String {
    val end   = track.endTime ?: System.currentTimeMillis()
    val millis = end - track.startTime
    val h = millis / 3_600_000
    val m = (millis % 3_600_000) / 60_000
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// ─── Track map overlay ────────────────────────────────────────────────────────

private class TrackOverlay(private val context: Context) : org.osmdroid.views.overlay.Overlay() {

    var trackData: List<Pair<Track, List<TrackPoint>>> = emptyList()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val dp = context.resources.displayMetrics.density

        trackData.forEach { (track, points) ->
            if (points.size < 2) return@forEach
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color       = track.color
                style       = AndroidPaint.Style.STROKE
                strokeWidth = 3f * dp
                strokeJoin  = AndroidPaint.Join.ROUND
                strokeCap   = AndroidPaint.Cap.ROUND
            }
            val path = android.graphics.Path()
            var first = true
            for (pt in points) {
                val pixel = mapView.projection.toPixels(GeoPoint(pt.lat, pt.lon), null)
                if (first) {
                    path.moveTo(pixel.x.toFloat(), pixel.y.toFloat())
                    first = false
                } else {
                    path.lineTo(pixel.x.toFloat(), pixel.y.toFloat())
                }
            }
            canvas.drawPath(path, paint)
        }
    }
}

// ─── Navigation math ──────────────────────────────────────────────────────────

private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R    = 3440.065
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = sin(dLat / 2).pow(2) +
               cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val y    = sin(dLon) * cos(Math.toRadians(lat2))
    val x    = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
               sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

private fun cardinalFromDeg(deg: Double): String {
    val i = ((deg + 22.5) / 45).toInt() % 8
    return listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[i]
}

// ─── Layer application ────────────────────────────────────────────────────────

private fun applyLayer(
    map: MapView,
    layer: ChartLayer,
    locationOverlay: DirectionOverlay,
    waypointOverlay: WaypointOverlay,
    trackOverlay: TrackOverlay,
    openSeaMapOverlay: TilesOverlay,
) {
    map.overlays.remove(openSeaMapOverlay)
    map.overlays.remove(trackOverlay)
    map.overlays.remove(locationOverlay)
    map.overlays.remove(waypointOverlay)

    when (layer) {
        ChartLayer.NAUTICAL  -> map.setTileSource(TileSourceFactory.MAPNIK)
        ChartLayer.OCEAN     -> map.setTileSource(EsriOceanSource)
        ChartLayer.SATELLITE -> map.setTileSource(EsriImagerySource)
    }

    map.overlays.add(openSeaMapOverlay)
    map.overlays.add(trackOverlay)
    map.overlays.add(locationOverlay)
    map.overlays.add(waypointOverlay)
    map.invalidate()
}

private fun tilesOverlay(context: android.content.Context, source: OnlineTileSourceBase): TilesOverlay =
    TilesOverlay(MapTileProviderBasic(context, source), context).apply {
        loadingBackgroundColor = android.graphics.Color.TRANSPARENT
        loadingLineColor       = android.graphics.Color.TRANSPARENT
    }

// ─── Tile sources ─────────────────────────────────────────────────────────────

private val OpenSeaMapSource = object : OnlineTileSourceBase(
    "OpenSeaMap", 2, 18, 256, ".png",
    arrayOf("https://tiles.openseamap.org/seamark/"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
}

private val EsriOceanSource = object : OnlineTileSourceBase(
    "ESRI Ocean", 1, 17, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Ocean/World_Ocean_Base/MapServer/tile"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}/${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
}

private val EsriImagerySource = object : OnlineTileSourceBase(
    "ESRI Imagery", 1, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}/${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
}

// ─── Direction overlay ────────────────────────────────────────────────────────

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

    override fun onLocationChanged(location: android.location.Location?, source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?) {
        this.location = location
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        val loc = location ?: return
        val pt  = mapView.projection.toPixels(GeoPoint(loc.latitude, loc.longitude), null)
        val x   = pt.x.toFloat(); val y = pt.y.toFloat()
        val bmp = if (compassBearing != null) arrowBitmap else dotBitmap
        canvas.save()
        if (compassBearing != null) canvas.rotate(compassBearing!!, x, y)
        canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f, null)
        canvas.restore()
    }
}

// ─── Location icons ───────────────────────────────────────────────────────────

private fun locationDotBitmap(context: android.content.Context, accentArgb: Int): Bitmap {
    val dp = context.resources.displayMetrics.density; val size = (28 * dp).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val cv = android.graphics.Canvas(bmp)
    val cx = size / 2f; val cy = size / 2f; val r = size * 0.32f
    cv.drawCircle(cx, cy, size * 0.46f, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = accentArgb; alpha = 50 })
    cv.drawCircle(cx, cy, r, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = accentArgb })
    cv.drawCircle(cx, cy, r, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = AndroidPaint.Style.STROKE; strokeWidth = size * 0.07f })
    return bmp
}

private fun locationArrowBitmap(context: android.content.Context, accentArgb: Int): Bitmap {
    val dp = context.resources.displayMetrics.density; val size = (52 * dp).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val cv = android.graphics.Canvas(bmp)
    val cx = size / 2f; val cy = size / 2f
    val tip = cy - size * 0.44f; val base = cy + size * 0.40f; val hw = size * 0.28f; val notch = cy + size * 0.14f
    cv.drawPath(android.graphics.Path().apply { moveTo(cx, tip + size * 0.03f); lineTo(cx + hw, base + size * 0.03f); lineTo(cx, notch + size * 0.03f); lineTo(cx - hw, base + size * 0.03f); close() },
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; alpha = 55; maskFilter = android.graphics.BlurMaskFilter(size * 0.08f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
    val arrowPath = android.graphics.Path().apply { moveTo(cx, tip); lineTo(cx + hw, base); lineTo(cx, notch); lineTo(cx - hw, base); close() }
    cv.drawPath(arrowPath, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = accentArgb; style = AndroidPaint.Style.FILL })
    cv.drawPath(arrowPath, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = AndroidPaint.Style.STROKE; strokeWidth = size * 0.06f; alpha = 230 })
    return bmp
}

// ─── Compass heading format ───────────────────────────────────────────────────

private fun formatBearing(deg: Float): String {
    val cardinal = when (((deg + 22.5f) / 45f).toInt() % 8) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"; 4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }
    return "$cardinal  ${deg.toInt()}°"
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

@Composable
private fun MapPill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.45f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun MapControlButton(
    icon:    ImageVector,
    palette: SkyPalette,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = CircleShape, color = palette.surfaceDim.copy(alpha = 0.92f),
        modifier = Modifier.size(44.dp), shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, tint = palette.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MapOrientationButton(mode: MapOrientationMode, palette: SkyPalette, onClick: () -> Unit) {
    val (label, bg, fg) = when (mode) {
        MapOrientationMode.NORTH_UP   -> Triple("N ↑",  palette.surfaceDim.copy(alpha = 0.92f), palette.onSurface)
        MapOrientationMode.HEADING_UP -> Triple("HDG",  palette.accent.copy(alpha = 0.90f),     palette.gradientTop)
        MapOrientationMode.FREE       -> Triple("FREE", palette.surfaceDim.copy(alpha = 0.92f), palette.onSurface)
    }
    Surface(onClick = onClick, shape = CircleShape, color = bg, modifier = Modifier.size(44.dp), shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

// ─── Map orientation watcher ──────────────────────────────────────────────────

private class MapOrientationWatcher(private val onOrientation: (Float) -> Unit) : org.osmdroid.views.overlay.Overlay() {
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        onOrientation(mapView.mapOrientation)
    }
}

// ─── Compass rose ─────────────────────────────────────────────────────────────

@Composable
private fun CompassRose(rotation: Float, palette: SkyPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.50f), r - 1.dp.toPx(), Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.18f), r - 1.dp.toPx(), Offset(cx, cy), style = Stroke(0.5.dp.toPx()))
        val tickOuter = r - 2.dp.toPx(); val tickInner = r - 7.dp.toPx()
        val ts = 7.5.dp.toPx(); val tr = r - 10.dp.toPx(); val base = ts * 0.35f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = ts; textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rotate(rotation, Offset(cx, cy)) {
            for (deg in listOf(0f, 90f, 180f, 270f)) {
                rotate(deg, Offset(cx, cy)) {
                    drawLine(Color.White.copy(alpha = 0.50f), Offset(cx, cy - tickOuter), Offset(cx, cy - tickInner), strokeWidth = 1.dp.toPx())
                }
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.apply {
                    paint.color = palette.accent.toArgb()
                    drawText("N", cx, cy - tr + base, paint)
                    paint.color = android.graphics.Color.argb(200, 255, 255, 255)
                    drawText("S", cx, cy + tr + base, paint)
                    drawText("E", cx + tr, cy + base, paint)
                    drawText("W", cx - tr, cy + base, paint)
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
        onDispose { lifecycle.removeObserver(observer); mapView.onDetach() }
    }
}
