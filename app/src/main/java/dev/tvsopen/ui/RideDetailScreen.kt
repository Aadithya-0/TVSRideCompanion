package dev.tvsopen.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.tvsopen.session.Ride
import dev.tvsopen.session.TrackPoint
import dev.tvsopen.ui.theme.NumberStyle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full ride detail: summary stats card + OSMDroid map with speed-band coloured polylines.
 *
 * Speed bands:
 *   0–30 km/h → blue
 *  30–60 km/h → green (Material tertiary)
 *      >60 km/h → red (error)
 *
 * No cloud, no API key — tiles come from OpenStreetMap.
 */
@Composable
fun RideDetailScreen(
    ride: Ride,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }
    val startLabel = remember(ride.startedAt) { dateFormatter.format(Date(ride.startedAt)) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ─── Back button + title ───────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column {
                Text(
                    "Ride $startLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${"%.1f".format(ride.distanceKm)} km · ${ride.durationMinutes()} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ─── Map ───────────────────────────────────────────────────────────
        if (ride.points.isNotEmpty()) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                AndroidView(
                    factory = { ctx -> buildMapView(ctx, ride.points) },
                    update = { /* static snapshot, no update needed */ },
                )
            }
        } else {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No GPS track recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Grant location permission to record routes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ─── Speed legend ──────────────────────────────────────────────────
        if (ride.points.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SpeedBand("0-30", "km/h", AndroidColor.BLUE)
                SpeedBand("30-60", "km/h", AndroidColor.rgb(76, 175, 80))
                SpeedBand("60+", "km/h", AndroidColor.RED)
            }
        }

        // ─── Stats grid ────────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "STATS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(Modifier.weight(1f), "Distance", "${"%.1f".format(ride.distanceKm)} km")
                    StatCell(Modifier.weight(1f), "Duration", "${ride.durationMinutes()} min")
                    StatCell(Modifier.weight(1f), "Started", startLabel.substringAfter(", ").ifBlank { startLabel })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(Modifier.weight(1f), "Top Speed", "${ride.topSpeedKmh} km/h")
                    StatCell(Modifier.weight(1f), "Avg Speed", "${ride.avgSpeedKmh} km/h")
                    StatCell(Modifier.weight(1f), "Top RPM", "${ride.topRpm}")
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SpeedBand(range: String, unit: String, @Suppress("UNUSED_PARAMETER") androidColor: Int) {
    // Use Material colors mapped to speed bands for dark/light correctness
    val (color, label) = when (range) {
        "0-30" -> Pair(androidx.compose.ui.graphics.Color(0xFF2196F3), "0-30 km/h slow")
        "30-60" -> Pair(androidx.compose.ui.graphics.Color(0xFF4CAF50), "30-60 km/h moderate")
        else -> Pair(androidx.compose.ui.graphics.Color(0xFFF44336), "60+ km/h fast")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            Modifier.size(width = 24.dp, height = 4.dp),
            shape = RoundedCornerShape(2.dp),
            color = color,
        ) {}
        Spacer(Modifier.size(6.dp))
        Text(
            "$range $unit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCell(modifier: Modifier, label: String, value: String) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = NumberStyle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Builds and configures the OSMDroid [MapView] with speed-band polylines.
 * Called once inside [AndroidView]; the result is static for this ride.
 */
private fun buildMapView(context: Context, points: List<TrackPoint>): MapView {
    Configuration.getInstance().userAgentValue = context.packageName

    val map = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        isTilesScaledToDpi = true
    }

    if (points.size < 2) {
        val center = points.firstOrNull()
        if (center != null) {
            map.controller.setZoom(15.0)
            map.controller.setCenter(GeoPoint(center.lat, center.lon))
        }
        return map
    }

    // Split into speed-band segments and add three polylines.
    fun color(kmh: Int): Int = when {
        kmh < 30 -> AndroidColor.BLUE
        kmh < 60 -> AndroidColor.rgb(76, 175, 80)
        else -> AndroidColor.RED
    }

    // Build contiguous segments where speed band is consistent.
    // Each segment is a list of consecutive GeoPoints with the same band.
    data class Seg(val band: Int, val pts: MutableList<GeoPoint> = mutableListOf())

    val segments = mutableListOf<Seg>()
    for (i in points.indices) {
        val pt = points[i]
        val band = when {
            pt.speedKmh < 30 -> 0
            pt.speedKmh < 60 -> 1
            else -> 2
        }
        val geo = GeoPoint(pt.lat, pt.lon)
        if (segments.isEmpty() || segments.last().band != band) {
            // Carry last point of previous segment into new one for continuity
            val prev = segments.lastOrNull()?.pts?.lastOrNull()
            segments += Seg(band).also { s ->
                if (prev != null) s.pts += prev
                s.pts += geo
            }
        } else {
            segments.last().pts += geo
        }
    }

    segments.forEach { seg ->
        if (seg.pts.size < 2) return@forEach
        val line = Polyline().apply {
            setPoints(seg.pts)
            outlinePaint.color = when (seg.band) {
                0 -> AndroidColor.BLUE
                1 -> AndroidColor.rgb(76, 175, 80)
                else -> AndroidColor.RED
            }
            outlinePaint.strokeWidth = 8f
        }
        map.overlays += line
    }

    // Fit the map to the route bounding box
    val lats = points.map { it.lat }
    val lons = points.map { it.lon }
    val north = lats.max()
    val south = lats.min()
    val east = lons.max()
    val west = lons.min()

    val centerLat = (north + south) / 2
    val centerLon = (east + west) / 2
    map.controller.setCenter(GeoPoint(centerLat, centerLon))

    // Estimate zoom from span
    val latSpan = north - south
    val lonSpan = east - west
    val span = maxOf(latSpan, lonSpan)
    val zoom = when {
        span < 0.005 -> 16.0
        span < 0.02 -> 14.0
        span < 0.1 -> 12.0
        span < 0.5 -> 10.0
        else -> 8.0
    }
    map.controller.setZoom(zoom)
    return map
}
