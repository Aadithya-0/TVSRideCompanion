// Ride history, computed locally on the phone.
//
// The official app's "My Rides" list, Total Rides and Last Ride all come from
// TVS's servers, tied to an account. This app has neither, so the same numbers
// are derived locally: a ride is a connected period, its distance is the
// odometer delta, its path is recorded from GPS points, and speed ranges are
// tracked for color-coded map display.

package dev.tvsopen.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One GPS track coordinate along the route. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val speedKmh: Int,
    val atMillis: Long,
)

/** One ride: a connected period, open or closed, with route and performance metrics. */
data class Ride(
    val startedAt: Long,
    val endedAt: Long?,
    val distanceKm: Double,
    val topSpeedKmh: Int = 0,
    val avgSpeedKmh: Int = 0,
    val topRpm: Int = 0,
    val points: List<TrackPoint> = emptyList(),
) {
    val inProgress: Boolean get() = endedAt == null

    fun durationMinutes(now: Long = System.currentTimeMillis()): Long {
        val end = endedAt ?: now
        return ((end - startedAt).coerceAtLeast(0L)) / 60_000L
    }
}

/**
 * Bounded local ride log with GPS breadcrumbs and speed analytics.
 */
class RideLog(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("tvsride", Context.MODE_PRIVATE)

    private val _rides = MutableStateFlow(load())

    /** Most recent first. */
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    /** Every ride ever recorded, including ones the bounded window has dropped. */
    val totalRides: Int get() = sp.getInt(KEY_TOTAL, 0)

    /** Running total across all rides, not just the retained window. */
    val totalDistanceKm: Double
        get() = sp.getString(KEY_DIST, null)?.toDoubleOrNull() ?: 0.0

    private fun load(): List<Ride> = runCatching {
        val arr = JSONArray(sp.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ptsArr = o.optJSONArray("pts") ?: JSONArray()
            val points = (0 until ptsArr.length()).mapNotNull { pIdx ->
                val p = ptsArr.optJSONArray(pIdx) ?: return@mapNotNull null
                if (p.length() >= 4) {
                    TrackPoint(
                        lat = p.optDouble(0),
                        lon = p.optDouble(1),
                        speedKmh = p.optInt(2),
                        atMillis = p.optLong(3),
                    )
                } else null
            }
            Ride(
                startedAt = o.optLong("s"),
                endedAt = if (o.isNull("e")) null else o.optLong("e"),
                distanceKm = o.optDouble("d", 0.0),
                topSpeedKmh = o.optInt("top_spd", 0),
                avgSpeedKmh = o.optInt("avg_spd", 0),
                topRpm = o.optInt("top_rpm", 0),
                points = points,
            )
        }
    }.getOrDefault(emptyList())

    private fun persist(list: List<Ride>) {
        val bounded = list.take(MAX_RIDES)
        val arr = JSONArray()
        bounded.forEach { r ->
            val ptsArr = JSONArray()
            // Sample points if too dense to keep storage light (max 1000 points per ride)
            val step = if (r.points.size > 1000) (r.points.size / 1000) + 1 else 1
            for (idx in r.points.indices step step) {
                val pt = r.points[idx]
                val p = JSONArray().put(pt.lat).put(pt.lon).put(pt.speedKmh).put(pt.atMillis)
                ptsArr.put(p)
            }
            arr.put(
                JSONObject()
                    .put("s", r.startedAt)
                    .put("e", r.endedAt ?: JSONObject.NULL)
                    .put("d", r.distanceKm)
                    .put("top_spd", r.topSpeedKmh)
                    .put("avg_spd", r.avgSpeedKmh)
                    .put("top_rpm", r.topRpm)
                    .put("pts", ptsArr),
            )
        }
        sp.edit().putString(KEY, arr.toString()).apply()
        _rides.value = bounded
    }

    /** Opens a ride. A ride already in progress is left alone. */
    fun start(now: Long = System.currentTimeMillis()) {
        val current = _rides.value
        if (current.firstOrNull()?.inProgress == true) return
        persist(listOf(Ride(now, null, 0.0)) + current)
    }

    /**
     * Records progress on the open ride.
     */
    fun update(distanceKm: Double) {
        val current = _rides.value
        val open = current.firstOrNull() ?: return
        if (!open.inProgress) return
        if (distanceKm < 0) return
        persist(listOf(open.copy(distanceKm = distanceKm)) + current.drop(1))
    }

    /** Records a GPS track point and live bike telemetry. */
    fun addPoint(lat: Double, lon: Double, speedKmh: Int, liveRpm: Int, now: Long = System.currentTimeMillis()) {
        val current = _rides.value
        val open = current.firstOrNull() ?: return
        if (!open.inProgress) return

        val newPoint = TrackPoint(lat, lon, speedKmh, now)
        val newTopSpeed = maxOf(open.topSpeedKmh, speedKmh)
        val newTopRpm = maxOf(open.topRpm, liveRpm)
        val updated = open.copy(
            topSpeedKmh = newTopSpeed,
            topRpm = newTopRpm,
            points = open.points + newPoint,
        )
        persist(listOf(updated) + current.drop(1))
    }

    /** Closes the open ride and calculates averages and totals. */
    fun finish(now: Long = System.currentTimeMillis()) {
        val current = _rides.value
        val open = current.firstOrNull() ?: return
        if (!open.inProgress) return

        val durationSec = ((now - open.startedAt) / 1000).coerceAtLeast(1)
        val movingPoints = open.points.filter { it.speedKmh > 0 }
        val avgSpeed = if (movingPoints.isNotEmpty()) {
            movingPoints.map { it.speedKmh }.average().toInt()
        } else {
            ((open.distanceKm / (durationSec / 3600.0))).toInt().coerceIn(0, 150)
        }

        val completed = open.copy(
            endedAt = now,
            avgSpeedKmh = avgSpeed,
        )

        sp.edit()
            .putInt(KEY_TOTAL, totalRides + 1)
            .putString(KEY_DIST, (totalDistanceKm + completed.distanceKm).toString())
            .apply()

        persist(listOf(completed) + current.drop(1))
    }

    companion object {
        private const val KEY = "ride_log"
        private const val KEY_TOTAL = "ride_total_count"
        private const val KEY_DIST = "ride_total_distance"
        private const val MAX_RIDES = 50
    }
}
