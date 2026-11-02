package dev.tvsopen.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRoutingProviderTest {

    @Test
    fun parsesOpenRouteServiceGeoJsonStepsAndGeometry() {
        val payload = """
            {
              "features": [{
                "geometry": {"type":"LineString","coordinates":[[75.0,11.0],[75.01,11.01]]},
                "properties": {
                  "summary": {"distance": 1234.0, "duration": 321.0},
                  "segments": [{
                    "steps": [{
                      "instruction": "Turn left onto Example Road",
                      "name": "Example Road",
                      "distance": 120.0,
                      "way_points": [0, 1],
                      "maneuver": {"type": "turn-left"}
                    }]
                  }]
                }
              }]
            }
        """.trimIndent()

        val route = HttpRoutingProvider(RoutingProviderConfig("https://example.test"))
            .parse(payload, RouteCoordinate(11.01, 75.01))

        assertEquals(1234, route.distanceMeters)
        assertEquals(321, route.durationSeconds)
        assertEquals(2, route.geometry.size)
        assertEquals("Turn left onto Example Road", route.steps.single().instruction)
        assertEquals(RouteCoordinate(11.01, 75.01), route.steps.single().coordinate)
    }
}
