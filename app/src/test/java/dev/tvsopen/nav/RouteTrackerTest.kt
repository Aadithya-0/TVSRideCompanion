package dev.tvsopen.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {

    private val origin = RouteCoordinate(11.2588, 75.7804)
    private val turn = RouteCoordinate(11.2598, 75.7804)
    private val destination = RouteCoordinate(11.2608, 75.7804)

    private val route = RoutePreview(
        destination = SharedDestination("test", destination.latitude, destination.longitude),
        distanceMeters = 220,
        durationSeconds = 120,
        geometry = listOf(origin, turn, destination),
        steps = listOf(
            RouteStep("Turn left", 110, coordinate = turn),
            RouteStep("Arrive", 110, coordinate = destination),
        ),
    )

    @Test
    fun `advances only after reaching the maneuver`() {
        val tracker = RouteTracker(route).also { it.start() }
        assertEquals(0, tracker.update(origin).stepIndex)
        assertEquals(1, tracker.update(turn).stepIndex)
        assertEquals("Arrive", tracker.update(turn).currentStep?.instruction)
    }

    @Test
    fun `flags a position far from the route`() {
        val tracker = RouteTracker(route).also { it.start() }
        val progress = tracker.update(RouteCoordinate(11.2700, 75.7804))

        assertEquals(RouteStatus.OFF_ROUTE, progress.status)
    }

    @Test
    fun `distance helper is stable for nearby points`() {
        val distance = RouteTracker.distanceMeters(origin, turn)
        assertTrue(distance in 100.0..120.0)
    }
}
