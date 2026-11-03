package dev.tvsopen.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationLinkParserTest {

    @Test
    fun `parses Google Maps query coordinates`() {
        val destination = DestinationLinkParser.parse(
            "https://www.google.com/maps/search/?api=1&query=11.2588,75.7804",
        )

        assertNotNull(destination)
        assertTrue(destination!!.hasCoordinates)
        assertEquals(11.2588, destination.latitude!!, 0.00001)
        assertEquals(75.7804, destination.longitude!!, 0.00001)
    }

    @Test
    fun `parses geo URI and retains a human label`() {
        val destination = DestinationLinkParser.parse("geo:11.2588,75.7804?q=Example%20Cafe")

        assertNotNull(destination)
        assertEquals("Example Cafe", destination!!.label)
        assertEquals(11.2588, destination.latitude!!, 0.00001)
    }

    @Test
    fun `keeps a place-only share unresolved`() {
        val destination = DestinationLinkParser.parse(
            "https://www.google.com/maps/search/?api=1&query=Example%20Cafe",
        )

        assertNotNull(destination)
        assertFalse(destination!!.hasCoordinates)
        assertEquals("Example Cafe", destination.label)
    }

    @Test
    fun `parses coordinates embedded in a Google Maps place redirect`() {
        val destination = DestinationLinkParser.parse(
            "https://www.google.com/maps/place/Example/@9.9639148,76.4058745,17z/data",
        )

        assertNotNull(destination)
        assertEquals(9.9639148, destination!!.latitude!!, 0.00001)
        assertEquals(76.4058745, destination.longitude!!, 0.00001)
    }
}
