package eu.kanade.tachiyomi.animesource.model

import fi.iki.elonen.NanoHTTPD

/**
 * Class for NanoHTTPD server used by anime extensions for local proxies/streams.
 *
 * @since extensions-lib 17
 */
open class HttpServer : NanoHTTPD(0) {
    val url: String
        get() = "http://localhost:$listeningPort"

    companion object {
        const val PLACEHOLDER_URL = "http://localhost:1"
    }
}
