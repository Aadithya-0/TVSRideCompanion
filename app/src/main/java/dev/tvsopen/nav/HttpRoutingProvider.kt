package dev.tvsopen.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RoutingProviderConfig(
    val endpoint: String,
    val apiKey: String? = null,
    val timeoutMillis: Int = 10_000,
)

/**
 * Parses the common OpenRouteService/Valhalla-style JSON boundary.
 *
 * The endpoint is injected rather than hardcoded so the app can use a hosted
 * provider today or a self-hosted endpoint later without changing guidance.
 */
class HttpRoutingProvider(
    private val config: RoutingProviderConfig,
) : RoutingProvider {

    override suspend fun route(
        origin: RouteCoordinate,
        destination: RouteCoordinate,
    ): Result<RoutePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(config.endpoint)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = config.timeoutMillis
                connection.readTimeout = config.timeoutMillis
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                config.apiKey?.takeIf { it.isNotBlank() }?.let {
                    connection.setRequestProperty("Authorization", it)
                }
                val body = JSONObject()
                    .put(
                        "coordinates",
                        org.json.JSONArray()
                            .put(org.json.JSONArray().put(origin.longitude).put(origin.latitude))
                            .put(org.json.JSONArray().put(destination.longitude).put(destination.latitude)),
                    )
                    .put("instructions", true)
                    .put("geometry", true)
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) {
                    error("routing request failed: HTTP ${connection.responseCode}")
                }
                parse(connection.inputStream.bufferedReader().use { it.readText() }, destination)
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun parse(payload: String, destination: RouteCoordinate): RoutePreview {
        val root = JSONObject(payload)
        val feature = root.optJSONArray("features")?.optJSONObject(0)
        val route = feature?.optJSONObject("properties")
            ?: root.optJSONArray("routes")?.optJSONObject(0)
            ?: root.optJSONArray("trip")?.optJSONObject(0)
            ?: error("routing response contained no route")
        val geometry = feature?.optJSONObject("geometry")
            ?.optJSONArray("coordinates")
            ?.let(::parseGeometry)
            ?: emptyList()
        val summary = route.optJSONObject("summary")
        val distance = summary?.optDouble("distance", 0.0)?.toInt()
            ?: route.optDouble("distance", 0.0).toInt()
        val duration = summary?.optDouble("duration", 0.0)?.toInt()
            ?: route.optDouble("time", 0.0).div(1000).toInt()
        val steps = mutableListOf<RouteStep>()
        val segments = route.optJSONArray("segments")
        if (segments != null) {
            for (i in 0 until segments.length()) {
                val segmentSteps = segments.optJSONObject(i)?.optJSONArray("steps") ?: continue
                for (j in 0 until segmentSteps.length()) {
                    val step = segmentSteps.optJSONObject(j) ?: continue
                    val wayPoints = step.optJSONArray("way_points")
                    val endPoint = wayPoints?.optInt(1, -1)?.let { geometry.getOrNull(it) }
                    steps += RouteStep(
                        instruction = step.optString("instruction")
                            .ifBlank { step.optString("name", "Continue") },
                        distanceMeters = step.optDouble("distance", 0.0).toInt(),
                        maneuver = step.optJSONObject("maneuver")?.optString("type"),
                        coordinate = endPoint,
                    )
                }
            }
        }
        return RoutePreview(
            destination = SharedDestination(
                source = "routing",
                latitude = destination.latitude,
                longitude = destination.longitude,
            ),
            distanceMeters = distance,
            durationSeconds = duration,
            geometry = geometry,
            steps = steps,
        )
    }

    private fun parseGeometry(coordinates: org.json.JSONArray): List<RouteCoordinate> =
        (0 until coordinates.length()).mapNotNull { index ->
            val pair = coordinates.optJSONArray(index) ?: return@mapNotNull null
            val longitude = pair.optDouble(0, Double.NaN)
            val latitude = pair.optDouble(1, Double.NaN)
            if (longitude.isNaN() || latitude.isNaN()) null
            else RouteCoordinate(latitude, longitude)
        }
}
