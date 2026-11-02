package dev.tvsopen.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteCodecTest {
    @Test
    fun preservesRouteGuidancePayload() {
        val route = RoutePreview(
            destination = SharedDestination("test", 11.0, 75.0, "Example Road"),
            distanceMeters = 1200,
            durationSeconds = 300,
            geometry = listOf(RouteCoordinate(11.0, 75.0)),
            steps = listOf(
                RouteStep("Turn left", 100, "turn-left", RouteCoordinate(11.0, 75.0)),
            ),
        )

        val decoded = RouteCodec.decode(RouteCodec.encode(route))

        assertEquals(route, decoded)
    }
}
