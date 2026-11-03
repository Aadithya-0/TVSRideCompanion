package dev.tvsopen.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uses the public OSRM demo endpoint when no ORS key is configured.
 *
 * This is a convenience fallback for personal use, not a guaranteed
 * production backend. The endpoint receives only the two route coordinates.
 */
class OsrmRoutingProvider(
    private val endpoint: String = "https://router.project-osrm.org",
) : RoutingProvider {

    override suspend fun route(
        origin: RouteCoordinate,
        destination: RouteCoordinate,
    ): Result<RoutePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/route/v1/driving/" +
                "${origin.longitude},${origin.latitude};" +
                "${destination.longitude},${destination.latitude}" +
                "?overview=full&steps=true&geometries=geojson"
            val connection = (URL(endpoint.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "TVSRideCompanion/1.0")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("routing request failed: HTTP ${connection.responseCode}")
                }
                parse(
                    connection.inputStream.bufferedReader().use { it.readText() },
                    destination,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun parse(payload: String, destination: RouteCoordinate): RoutePreview {
        val root = JSONObject(payload)
        if (root.optString("code") != "Ok") {
            error("routing response contained no route")
        }
        val route = root.optJSONArray("routes")?.optJSONObject(0)
            ?: error("routing response contained no route")
        val geometry = route.optJSONObject("geometry")
            ?.optJSONArray("coordinates")
            ?.let { coordinates ->
                (0 until coordinates.length()).mapNotNull { index ->
                    val pair = coordinates.optJSONArray(index) ?: return@mapNotNull null
                    val longitude = pair.optDouble(0, Double.NaN)
                    val latitude = pair.optDouble(1, Double.NaN)
                    if (longitude.isNaN() || latitude.isNaN()) null
                    else RouteCoordinate(latitude, longitude)
                }
            }
            ?: emptyList()
        val steps = mutableListOf<RouteStep>()
        val legs = route.optJSONArray("legs") ?: org.json.JSONArray()
        for (legIndex in 0 until legs.length()) {
            val legSteps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until legSteps.length()) {
                val step = legSteps.optJSONObject(stepIndex) ?: continue
                val maneuver = step.optJSONObject("maneuver")
                val type = maneuver?.optString("type").orEmpty()
                val modifier = maneuver?.optString("modifier").orEmpty()
                val name = step.optString("name").ifBlank { "road" }
                val instruction = listOf(type, modifier, name)
                    .filter { it.isNotBlank() && it != "depart" && it != "arrive" }
                    .joinToString(" ")
                    .ifBlank { "Continue on $name" }
                val location = maneuver?.optJSONArray("location")
                val coordinate = if (location != null && location.length() >= 2) {
                    RouteCoordinate(location.optDouble(1), location.optDouble(0))
                } else {
                    null
                }
                steps += RouteStep(
                    instruction = instruction,
                    distanceMeters = step.optDouble("distance", 0.0).toInt(),
                    maneuver = type,
                    coordinate = coordinate,
                )
            }
        }
        return RoutePreview(
            destination = SharedDestination(
                source = "osrm",
                latitude = destination.latitude,
                longitude = destination.longitude,
            ),
            distanceMeters = route.optDouble("distance", 0.0).toInt(),
            durationSeconds = route.optDouble("duration", 0.0).toInt(),
            geometry = geometry,
            steps = steps,
        )
    }
}
