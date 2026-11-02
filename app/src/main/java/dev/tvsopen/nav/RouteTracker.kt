package dev.tvsopen.nav

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class RouteStatus {
    PREVIEW,
    GUIDING,
    OFF_ROUTE,
    ARRIVED,
}

data class RouteProgress(
    val status: RouteStatus,
    val stepIndex: Int,
    val currentStep: RouteStep?,
    val distanceToStepMeters: Int,
    val remainingDistanceMeters: Int,
    val remainingDurationSeconds: Int,
)

/**
 * Small, deterministic route-following state machine.
 *
 * It deliberately uses conservative thresholds: GPS jitter should not advance
 * a maneuver early, while a sustained position far from the route should
 * request a reroute rather than sending stale instructions to the cluster.
 */
class RouteTracker(
    private val route: RoutePreview,
    private val arrivalRadiusMeters: Double = 35.0,
    private val offRouteRadiusMeters: Double = 80.0,
) {
    private var stepIndex = 0
    private var status = RouteStatus.PREVIEW
    private var lastProgress = initialProgress()

    fun start(): RouteProgress {
        status = RouteStatus.GUIDING
        lastProgress = lastProgress.copy(status = status)
        return lastProgress
    }

    fun update(location: RouteCoordinate): RouteProgress {
        if (status == RouteStatus.ARRIVED) return lastProgress
        if (status == RouteStatus.PREVIEW) start()

        val routeDistance = nearestRouteDistance(location)
        if (route.geometry.isNotEmpty() && routeDistance > offRouteRadiusMeters) {
            status = RouteStatus.OFF_ROUTE
        } else if (status == RouteStatus.OFF_ROUTE) {
            status = RouteStatus.GUIDING
        }

        val step = route.steps.getOrNull(stepIndex)
        val distanceToStep = step?.coordinate?.let { distanceMeters(location, it) }
            ?: step?.distanceMeters?.toDouble()
            ?: 0.0
        if (status == RouteStatus.GUIDING && distanceToStep <= arrivalRadiusMeters) {
            stepIndex++
            if (stepIndex >= route.steps.size) status = RouteStatus.ARRIVED
        }

        val remaining = route.steps.drop(stepIndex).sumOf { it.distanceMeters }
        val duration = if (route.distanceMeters > 0) {
            (route.durationSeconds.toDouble() * remaining / route.distanceMeters).toInt()
        } else {
            0
        }
        lastProgress = RouteProgress(
            status = status,
            stepIndex = stepIndex,
            currentStep = route.steps.getOrNull(stepIndex),
            distanceToStepMeters = distanceToStep.toInt().coerceAtLeast(0),
            remainingDistanceMeters = remaining,
            remainingDurationSeconds = duration,
        )
        return lastProgress
    }

    private fun initialProgress() = RouteProgress(
        status = status,
        stepIndex = 0,
        currentStep = route.steps.firstOrNull(),
        distanceToStepMeters = route.steps.firstOrNull()?.distanceMeters ?: 0,
        remainingDistanceMeters = route.distanceMeters,
        remainingDurationSeconds = route.durationSeconds,
    )

    private fun nearestRouteDistance(location: RouteCoordinate): Double =
        route.geometry.minOfOrNull { distanceMeters(location, it) } ?: 0.0

    companion object {
        fun distanceMeters(a: RouteCoordinate, b: RouteCoordinate): Double {
            val earthRadius = 6_371_000.0
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = lat2 - lat1
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return earthRadius * 2 * atan2(sqrt(h), sqrt(1 - h))
        }
    }
}
