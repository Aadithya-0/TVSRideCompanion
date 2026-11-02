package dev.tvsopen.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RideLogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tvsride", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `ride lifecycle persists points and aggregates`() {
        val first = RideLog(context)
        first.start(now = 1_000L)
        first.update(1.2)
        first.addPoint(12.0, 77.0, 30, 2_000, now = 2_000L)
        first.addPoint(12.1, 77.1, 50, 3_000, now = 3_000L)
        first.finish(now = 61_000L)

        val restored = RideLog(context)
        val ride = restored.rides.value.single()
        assertFalse(ride.inProgress)
        assertEquals(1.2, ride.distanceKm, 0.001)
        assertEquals(50, ride.topSpeedKmh)
        assertEquals(3_000, ride.topRpm)
        assertEquals(40, ride.avgSpeedKmh)
        assertEquals(2, ride.points.size)
        assertEquals(1, restored.totalRides)
        assertEquals(1.2, restored.totalDistanceKm, 0.001)
    }

    @Test
    fun `starting twice does not replace an open ride`() {
        val log = RideLog(context)
        log.start(now = 10L)
        log.addPoint(1.0, 2.0, 10, 100, now = 20L)
        log.start(now = 30L)

        val ride = log.rides.value.single()
        assertTrue(ride.inProgress)
        assertEquals(10L, ride.startedAt)
        assertEquals(1, ride.points.size)
    }
}
