package dev.tvsopen.nav

import org.json.JSONArray
import org.json.JSONObject

object RouteCodec {
    fun encode(route: RoutePreview): String = JSONObject()
        .put("destination", JSONObject()
            .put("source", route.destination.source)
            .put("latitude", route.destination.latitude)
            .put("longitude", route.destination.longitude)
            .put("label", route.destination.label))
        .put("distance", route.distanceMeters)
        .put("duration", route.durationSeconds)
        .put("geometry", JSONArray().apply {
            route.geometry.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
        })
        .put("steps", JSONArray().apply {
            route.steps.forEach {
                put(JSONObject()
                    .put("instruction", it.instruction)
                    .put("distance", it.distanceMeters)
                    .put("maneuver", it.maneuver)
                    .put("latitude", it.coordinate?.latitude)
                    .put("longitude", it.coordinate?.longitude))
            }
        })
        .toString()

    fun decode(value: String): RoutePreview {
        val root = JSONObject(value)
        val destination = root.optJSONObject("destination") ?: error("route has no destination")
        val geometry = root.optJSONArray("geometry").toCoordinates()
        val stepsJson = root.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsJson.length()).mapNotNull { index ->
            val step = stepsJson.optJSONObject(index) ?: return@mapNotNull null
            val lat = step.optDouble("latitude", Double.NaN)
            val lon = step.optDouble("longitude", Double.NaN)
            RouteStep(
                instruction = step.optString("instruction", "Continue"),
                distanceMeters = step.optInt("distance", 0),
                maneuver = step.optString("maneuver").takeIf { it.isNotBlank() },
                coordinate = if (lat.isNaN() || lon.isNaN()) null else RouteCoordinate(lat, lon),
            )
        }
        return RoutePreview(
            destination = SharedDestination(
                source = destination.optString("source", "route"),
                latitude = destination.optDouble("latitude", Double.NaN)
                    .takeUnless { it.isNaN() },
                longitude = destination.optDouble("longitude", Double.NaN)
                    .takeUnless { it.isNaN() },
                label = destination.optString("label").takeIf { it.isNotBlank() },
            ),
            distanceMeters = root.optInt("distance", 0),
            durationSeconds = root.optInt("duration", 0),
            geometry = geometry,
            steps = steps,
        )
    }

    private fun JSONArray?.toCoordinates(): List<RouteCoordinate> =
        this?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val pair = array.optJSONArray(index) ?: return@mapNotNull null
                val latitude = pair.optDouble(0, Double.NaN)
                val longitude = pair.optDouble(1, Double.NaN)
                if (latitude.isNaN() || longitude.isNaN()) null
                else RouteCoordinate(latitude, longitude)
            }
        } ?: emptyList()
}
