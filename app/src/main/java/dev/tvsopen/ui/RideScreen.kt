package dev.tvsopen.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvsopen.session.Telemetry
import dev.tvsopen.session.Session
import dev.tvsopen.transport.ConnectionState
import dev.tvsopen.transport.RideService
import dev.tvsopen.ui.theme.NumberStyle

/**
 * Compact ride-mode overlay shown when a trip is active.
 *
 * Displays live telemetry (speed, RPM, fuel, DTE) and engine-synchronized
 * trip controls. Sends intents to [RideService] — no state
 * is owned here, only observed from Session via [telemetry].
 */
@Composable
fun RideScreen(
    telemetry: Telemetry,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rides by Session.rides.collectAsState()
    val tripRunning = rides.firstOrNull()?.inProgress == true
    val connection by Session.connection.collectAsState()
    val engineRunning = telemetry.isFresh() && (telemetry.engineRpm ?: 0) >= 1_500
    val canStart = connection.state == ConnectionState.CONNECTED && !tripRunning
    val startedAt = rides.firstOrNull()?.takeIf { it.inProgress }?.startedAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    fun send(action: String) {
        runCatching {
            context.startForegroundService(
                Intent(context, RideService::class.java).setAction(action)
            )
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ─── Header ───────────────────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ride Mode",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenHistory) {
                Text("History")
            }
        }
        Text(
            when {
                tripRunning -> "Recording ride · continues in background"
                connection.state != ConnectionState.CONNECTED -> "Connect to the scooter first"
                !engineRunning -> "Waiting for engine start"
                else -> "Ready to record"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ─── Live telemetry grid ───────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TelemetryTile(
                modifier = Modifier.weight(1f),
                label = "Speed",
                value = (telemetry.gpsSpeedKmh ?: telemetry.speedKmh)?.toString() ?: "—",
                unit = "km/h",
            )
            TelemetryTile(
                modifier = Modifier.weight(1f),
                label = "RPM",
                value = telemetry.engineRpm?.toString() ?: "—",
                unit = "rpm",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TelemetryTile(
                modifier = Modifier.weight(1f),
                label = "Fuel",
                value = telemetry.fuelBars?.let { "$it/6" } ?: "—",
                unit = "bars",
            )
            TelemetryTile(
                modifier = Modifier.weight(1f),
                label = "DTE",
                value = telemetry.distanceToEmptyKm?.toString() ?: "—",
                unit = "km",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TelemetryTile(
                modifier = Modifier.weight(1f),
                label = "Avg Speed",
                value = telemetry.averageSpeedKmh?.toString() ?: "—",
                unit = "km/h",
            )
            TelemetryTile(
                modifier = Modifier.weight(1f),
                label = "Top Speed",
                value = telemetry.topSpeedKmh?.toString() ?: "—",
                unit = "km/h",
            )
        }

        // ─── Trip controls ─────────────────────────────────────────────────
        Spacer(Modifier.height(28.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Start / Resume
                FilledIconButton(
                    onClick = {
                        send(RideService.ACTION_START_TRIP)
                    },
                    modifier = Modifier.size(56.dp),
                    enabled = canStart,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start / Resume trip")
                }

                // Stop
                FilledIconButton(
                    onClick = {
                        if (tripRunning) {
                            send(RideService.ACTION_END_TRIP)
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    enabled = tripRunning,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop trip")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            formatElapsed(startedAt?.let { now - it } ?: 0L),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            when {
                tripRunning -> "Elapsed ride time"
                connection.state != ConnectionState.CONNECTED -> "Connect to arm recording"
                engineRunning -> "Ready — tap Start to arm"
                else -> "Start is armed before the engine runs",
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (tripRunning) "Stop recording"
            else "Arm recording; engine start begins the ride",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(28.dp))
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(
        totalSeconds / 3600L,
        (totalSeconds / 60L) % 60L,
        totalSeconds % 60L,
    )
}

@Composable
private fun TelemetryTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
) {
    Surface(
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = NumberStyle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}
