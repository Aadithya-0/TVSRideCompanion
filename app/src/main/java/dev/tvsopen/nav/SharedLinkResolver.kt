package dev.tvsopen.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves short share links without logging the URL or sending it anywhere
 * except the host selected by the user. The final URL is parsed locally.
 */
class SharedLinkResolver {

    suspend fun resolve(destination: SharedDestination): Result<SharedDestination> =
        withContext(Dispatchers.IO) {
            if (destination.hasCoordinates || !isShortMapsLink(destination.source)) {
                return@withContext Result.success(destination)
            }
            runCatching {
                val connection = (URL(destination.source).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    useCaches = true
                }
                try {
                    if (connection.responseCode !in 200..399) {
                        error("share link could not be resolved: HTTP ${connection.responseCode}")
                    }
                    if (!isGoogleMapsHost(connection.url.host)) {
                        error("share link redirected outside Google Maps")
                    }
                    val resolved = DestinationLinkParser.parse(connection.url.toString())
                    resolved ?: destination
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun isShortMapsLink(source: String): Boolean =
        runCatching {
            val url = URL(source)
            if (url.protocol != "https") return@runCatching null
            url.host.lowercase()
        }
            .getOrNull()
            ?.endsWith("maps.app.goo.gl") == true

    private fun isGoogleMapsHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "maps.app.goo.gl" ||
            normalized == "maps.google.com" ||
            normalized == "www.google.com" ||
            normalized == "google.com"
    }
}
