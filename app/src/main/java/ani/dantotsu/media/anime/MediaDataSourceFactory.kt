package ani.dantotsu.media.anime

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.httpengine.HttpEngineDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import ani.dantotsu.util.Logger
import okhttp3.OkHttpClient
import org.chromium.net.CronetProvider
import java.util.concurrent.Executors

/**
 * Multi-tier HTTP DataSource resolver for Media3 / ExoPlayer.
 *
 * Tries the highest-capability engine available on this device:
 *
 *   Tier 1 (API 34+)   — System [HttpEngineDataSource] (Android OS Chromium stack).
 *                        Provides HTTP/3 (QUIC) + HTTP/2. Zero APK footprint.
 *
 *   Tier 2 (<API 34, GMS devices)
 *                      — [CronetDataSource] backed by Google Play Services Cronet.
 *                        Provides HTTP/3 (QUIC) + HTTP/2. ~100 KB APK footprint (glue only).
 *                        On F-Droid builds [CronetProvider] class is absent at runtime
 *                        → caught → falls through cleanly.
 *
 *   Tier 3 (fallback)  — [OkHttpDataSource] (existing HTTP/2 baseline). Always available.
 *
 * Localhost/loopback streams always use the OkHttp factory (Tier 3) so that the
 * existing gzip-decompression and Accept-Encoding interceptors remain active for
 * the NanoHTTPD torrent proxy.
 */
@UnstableApi
object MediaDataSourceFactory {

    /**
     * Resolves and returns the best available [HttpDataSource.Factory] for the
     * given [context] and request [headers].
     *
     * @param context      Android context (used for Cronet provider lookup).
     * @param headers      Default request headers to set on the factory.
     * @param okHttpClient The OkHttpClient used as the Tier 3 fallback.
     * @param isLocalhost  When true, skips QUIC tiers and returns OkHttp directly.
     *                     QUIC over loopback is undefined and the NanoHTTPD proxy
     *                     requires the OkHttp interceptor chain.
     */
    fun resolveHttpFactory(
        context: Context,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient,
        isLocalhost: Boolean = false,
    ): HttpDataSource.Factory {
        if (isLocalhost) {
            Logger.log("DataSource: localhost stream — using OkHttp (HTTP/2, QUIC bypassed)")
            return buildOkHttpFactory(okHttpClient, headers)
        }

        // Tier 1 — Android 14+ system HttpEngine
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tryHttpEngine(context, headers)?.let { return it }
        }

        // Tier 2 — Google Play Services Cronet (absent on F-Droid; caught safely)
        tryCronet(context, headers)?.let { return it }

        // Tier 3 — OkHttp (HTTP/2, always available)
        Logger.log("DataSource: using OkHttp (HTTP/2)")
        return buildOkHttpFactory(okHttpClient, headers)
    }

    // -------------------------------------------------------------------------
    // Tier 1
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // API 34
    private fun tryHttpEngine(
        context: Context,
        headers: Map<String, String>,
    ): HttpDataSource.Factory? = runCatching {
        val engine = android.net.http.HttpEngine.Builder(context)
            .setEnableQuic(true)
            .setEnableHttp2(true)
            .build()
        HttpEngineDataSource.Factory(engine, Executors.newCachedThreadPool())
            .apply { setDefaultRequestProperties(headers) }
            .also { Logger.log("DataSource: using HttpEngine (HTTP/3 + HTTP/2)") }
    }.getOrElse { e ->
        Logger.log("DataSource: HttpEngine init failed — ${e.message}")
        null
    }

    // -------------------------------------------------------------------------
    // Tier 2
    // -------------------------------------------------------------------------

    /**
     * Attempts to build a [CronetDataSource.Factory] via the GMS Cronet provider.
     *
     * This function deliberately catches [Throwable] (not just [Exception]) because
     * on F-Droid builds the [CronetProvider] class itself is absent, which causes a
     * [NoClassDefFoundError] (a [LinkageError] subclass, not an [Exception]).
     */
    private fun tryCronet(
        context: Context,
        headers: Map<String, String>,
    ): HttpDataSource.Factory? = runCatching {
        val providers = CronetProvider.getAllProviders(context)
        val provider = providers.firstOrNull { p ->
            p.isEnabled && p.name != CronetProvider.PROVIDER_NAME_FALLBACK
        } ?: run {
            Logger.log("DataSource: no active Cronet provider found")
            return@runCatching null
        }

        val engine = provider.createBuilder()
            .enableQuic(true)
            .enableHttp2(true)
            .build()

        CronetDataSource.Factory(engine, Executors.newCachedThreadPool())
            .apply { setDefaultRequestProperties(headers) }
            .also { Logger.log("DataSource: using Cronet '${provider.name}' (HTTP/3 + HTTP/2)") }
    }.getOrElse { e ->
        // NoClassDefFoundError on F-Droid (CronetProvider class absent), or any
        // runtime failure on a GMS device — both are safe to swallow here.
        Logger.log("DataSource: Cronet unavailable — ${e.message}")
        null
    }

    // -------------------------------------------------------------------------
    // Tier 3 (also used directly by DrmDownloader for license requests)
    // -------------------------------------------------------------------------

    /**
     * Builds an [OkHttpDataSource.Factory] with the supplied [headers].
     * DRM license fetches always use this path (they need the OkHttp cookie /
     * CloudFlare interceptor chain and are not latency-critical).
     */
    fun buildOkHttpFactory(
        okHttpClient: OkHttpClient,
        headers: Map<String, String>,
    ): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient).apply {
            setDefaultRequestProperties(headers)
            headers["User-Agent"]?.let { setUserAgent(it) }
        }
}
