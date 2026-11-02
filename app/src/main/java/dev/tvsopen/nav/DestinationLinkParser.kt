package dev.tvsopen.nav

import android.net.Uri

/**
 * Extracts a destination from common Google Maps share formats without
 * resolving or uploading the link. Links that contain only a place name are
 * returned as unresolved so the UI can ask the rider to confirm the pin.
 */
data class SharedDestination(
    val source: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null,
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}

object DestinationLinkParser {

    private val COORDINATE_RE =
        Regex("""@(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    private val PAIR_RE =
        Regex("""(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")

    fun parse(sharedText: String): SharedDestination? {
        val source = sharedText.trim()
        if (source.isEmpty()) return null

        val uri = runCatching { Uri.parse(source) }.getOrNull()
        val queryPair = uri?.getQueryParameter("query")
            ?.let(::parsePair)
            ?: uri?.getQueryParameter("q")?.let(::parsePair)
        val embeddedPair = COORDINATE_RE.find(source)?.let {
            it.groupValues[1].toDoubleOrNull() to it.groupValues[2].toDoubleOrNull()
        } ?: PAIR_RE.find(source)?.let {
            it.groupValues[1].toDoubleOrNull() to it.groupValues[2].toDoubleOrNull()
        }
        val pair = queryPair ?: embeddedPair
        val label = uri?.getQueryParameter("q")
            ?.takeUnless { it.matches(PAIR_RE) }
            ?.takeIf { it.isNotBlank() }

        return SharedDestination(
            source = source,
            latitude = pair?.first,
            longitude = pair?.second,
            label = label,
        )
    }

    private fun parsePair(value: String): Pair<Double, Double>? {
        val match = PAIR_RE.find(value) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return lat to lon
    }
}
