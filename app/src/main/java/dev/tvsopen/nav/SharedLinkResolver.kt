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
                var current = destination.source
                repeat(MAX_REDIRECTS) {
                    val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        useCaches = false
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                    try {
                        val status = connection.responseCode
                        if (status in 300..399) {
                            val location = connection.getHeaderField("Location")
                                ?: error("share link redirect did not include a destination")
                            val next = URL(URL(current), location).toString()
                            if (!isGoogleMapsHost(URL(next).host)) {
                                error("share link redirected outside Google Maps")
                            }
                            current = next
                            return@repeat
                        }
                        if (status !in 200..299) {
                            error("share link could not be resolved: HTTP $status")
                        }
                        val resolved = DestinationLinkParser.parse(current)
                        return@withContext Result.success(
                            resolved?.takeIf { it.hasCoordinates } ?: error(
                                "Google Maps did not include coordinates in the shared link",
                            ),
                        )
                    } finally {
                        connection.disconnect()
                    }
                }
                error("share link used too many redirects")
            }
        }

    private fun isShortMapsLink(source: String): Boolean =
        runCatching {
            val url = URL(source)
            if (url.protocol != "https") return@runCatching null
            url.host.lowercase()
        }
            .getOrNull()
            ?.equals("maps.app.goo.gl", ignoreCase = true) == true

    private fun isGoogleMapsHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "maps.app.goo.gl" ||
            normalized == "maps.google.com" ||
            normalized == "www.google.com" ||
            normalized == "google.com"
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "TVSRideCompanion/1.0"
    }
}
