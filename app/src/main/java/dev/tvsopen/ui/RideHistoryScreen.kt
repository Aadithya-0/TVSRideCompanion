package dev.tvsopen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tvsopen.session.Ride
import dev.tvsopen.session.Session
import dev.tvsopen.ui.theme.NumberStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Lists all recorded rides with a month filter.
 *
 * Tapping a ride opens [RideDetailScreen] with the full route map and stats.
 * Rides are stored locally by [RideLog] — no cloud, no account.
 */
@Composable
fun RideHistoryScreen(
    onRideClick: (Ride) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rides by Session.rides.collectAsStateWithLifecycle()
    val totalRides by Session.totalRides.collectAsStateWithLifecycle()
    val totalDistKm by Session.totalRideDistanceKm.collectAsStateWithLifecycle()

    // Month filter state: null = show all
    var selectedMonth by remember { mutableStateOf<String?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    // Build the list of available months from all rides
    val monthFormatter = remember { SimpleDateFormat("MMM yyyy", Locale.getDefault()) }
    val availableMonths = remember(rides) {
        rides.map { monthFormatter.format(Date(it.startedAt)) }.distinct().sorted().reversed()
    }
    val filtered = remember(rides, selectedMonth) {
        if (selectedMonth == null) rides
        else rides.filter { monthFormatter.format(Date(it.startedAt)) == selectedMonth }
    }
    Column(modifier.fillMaxSize()) {
        // ─── Header row ───────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ride History", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "$totalRides rides · ${"%.1f".format(totalDistKm)} km total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Month filter button
            Box {
                IconButton(onClick = { filterMenuOpen = true }) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filter by month",
                        tint = if (selectedMonth != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All rides") },
                        onClick = {
                            selectedMonth = null
                            filterMenuOpen = false
                        },
                    )
                    availableMonths.forEach { month ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    month,
                                    fontWeight = if (month == selectedMonth) FontWeight.Bold
                                                 else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                selectedMonth = month
                                filterMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (selectedMonth != null) {
            Text(
                "Showing: $selectedMonth",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (filtered.isEmpty()) {
            // Empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (rides.isEmpty()) "No rides recorded yet"
                        else "No rides in ${selectedMonth ?: "selected period"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (rides.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Connect to your scooter to start recording",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.startedAt }) { ride ->
                    RideRow(ride = ride, onClick = { onRideClick(ride) })
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun RideRow(
    ride: Ride,
    onClick: () -> Unit,
) {
    val dateFormatter = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val startLabel = remember(ride.startedAt) { dateFormatter.format(Date(ride.startedAt)) }
    val durationMin = remember(ride.startedAt, ride.endedAt) { ride.durationMinutes() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            Surface(
                Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Text block
            Column(Modifier.weight(1f)) {
                Text(
                    startLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("${"%.1f".format(ride.distanceKm)} km")
                        append(" · ${durationMin}m")
                        if (ride.avgSpeedKmh > 0) append(" · avg ${ride.avgSpeedKmh} km/h")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Top speed chip
            if (ride.topSpeedKmh > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        ride.topSpeedKmh.toString(),
                        style = NumberStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "top km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
