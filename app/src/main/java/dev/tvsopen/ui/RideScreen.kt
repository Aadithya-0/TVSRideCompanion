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
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvsopen.session.Telemetry
import dev.tvsopen.transport.RideService
import dev.tvsopen.ui.theme.NumberStyle

/**
 * Compact ride-mode overlay shown when a trip is active.
 *
 * Displays live telemetry (speed, RPM, fuel, DTE) and three trip-control
 * buttons (start/pause/stop). Sends intents to [RideService] — no state
 * is owned here, only observed from Session via [telemetry].
 */
@Composable
fun RideScreen(
    telemetry: Telemetry,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    fun send(action: String) {
        runCatching {
            context.startForegroundService(
                Intent(context, RideService::class.java).setAction(action)
            )
        }
    }

    var tripRunning by rememberSaveable { mutableStateOf(false) }
    var tripPaused by rememberSaveable { mutableStateOf(false) }

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
            if (!tripRunning) "Tap Start Trip to begin recording"
            else if (tripPaused) "Paused — GPS recording suspended"
            else "Recording trip…",
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
                        if (!tripRunning || tripPaused) {
                            tripRunning = true
                            tripPaused = false
                            send(RideService.ACTION_START_TRIP)
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start / Resume trip")
                }

                // Pause
                FilledIconButton(
                    onClick = {
                        if (tripRunning && !tripPaused) {
                            tripPaused = true
                            send(RideService.ACTION_PAUSE_TRIP)
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    enabled = tripRunning && !tripPaused,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pause trip")
                }

                // Stop
                FilledIconButton(
                    onClick = {
                        if (tripRunning) {
                            tripRunning = false
                            tripPaused = false
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
            if (!tripRunning) "▶ Start   ⏸ Pause   ■ Stop"
            else if (tripPaused) "▶ Resume   ■ Stop"
            else "⏸ Pause   ■ Stop",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(28.dp))
    }
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
