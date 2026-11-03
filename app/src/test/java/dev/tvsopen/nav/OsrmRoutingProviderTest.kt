package dev.tvsopen.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OsrmRoutingProviderTest {

    @Test
    fun parsesOsrmRouteGeometryAndSteps() {
        val payload = """
            {
              "code":"Ok",
              "routes":[{
                "distance":1234.0,
                "duration":321.0,
                "geometry":{"coordinates":[[75.0,11.0],[75.01,11.01]]},
                "legs":[{
                  "steps":[{
                    "distance":120.0,
                    "name":"Example Road",
                    "maneuver":{
                      "type":"turn",
                      "modifier":"left",
                      "location":[75.01,11.01]
                    }
                  }]
                }]
              }]
            }
        """.trimIndent()

        val route = OsrmRoutingProvider().parse(
            payload,
            RouteCoordinate(11.01, 75.01),
        )

        assertEquals(1234, route.distanceMeters)
        assertEquals(321, route.durationSeconds)
        assertEquals(2, route.geometry.size)
        assertEquals(RouteCoordinate(11.0, 75.0), route.geometry.first())
        assertTrue(route.steps.single().instruction.contains("left"))
        assertEquals(RouteCoordinate(11.01, 75.01), route.steps.single().coordinate)
    }
}
