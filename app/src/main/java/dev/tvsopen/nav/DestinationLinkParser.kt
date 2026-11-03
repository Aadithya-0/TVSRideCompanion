package dev.tvsopen.nav

import android.net.Uri
import java.net.URLDecoder

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
        val queryValue = uri?.getQueryParameter("query")
            ?: rawQueryParameter(source, "query")
        val qValue = uri?.getQueryParameter("q")
            ?: rawQueryParameter(source, "q")
        val queryPair = queryValue
            ?.let(::parsePair)
            ?: qValue?.let(::parsePair)
        val embeddedPair = COORDINATE_RE.find(source)?.let {
            it.groupValues[1].toDoubleOrNull() to it.groupValues[2].toDoubleOrNull()
        } ?: PAIR_RE.find(source)?.let {
            it.groupValues[1].toDoubleOrNull() to it.groupValues[2].toDoubleOrNull()
        }
        val pair = queryPair ?: embeddedPair
        val label = (qValue ?: queryValue)
            ?.takeUnless { parsePair(it) != null }
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

    private fun rawQueryParameter(source: String, key: String): String? =
        Regex("""(?:[?&])${Regex.escape(key)}=([^&#]*)""")
            .find(source)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
}
