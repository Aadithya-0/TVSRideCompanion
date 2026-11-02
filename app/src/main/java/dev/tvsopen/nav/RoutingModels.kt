package dev.tvsopen.nav

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val maneuver: String? = null,
    val coordinate: RouteCoordinate? = null,
)

data class RoutePreview(
    val destination: SharedDestination,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val geometry: List<RouteCoordinate> = emptyList(),
    val steps: List<RouteStep> = emptyList(),
)

interface RoutingProvider {
    suspend fun route(
        origin: RouteCoordinate,
        destination: RouteCoordinate,
    ): Result<RoutePreview>
}
