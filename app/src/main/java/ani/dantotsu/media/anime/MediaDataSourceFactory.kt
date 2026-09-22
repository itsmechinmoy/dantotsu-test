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
 * All HTTP/3-capable tiers come from the single [media3-datasource-cronet] artifact —
 * there is no separate media3-datasource-httpengine artifact.
 *
 *   Tier 1 (API 34+)    — [HttpEngineDataSource] backed by the Android OS system HttpEngine.
 *                          No GMS required. HTTP/3 (QUIC) + HTTP/2. Zero APK footprint.
 *
 *   Tier 2 (API 30–33)  — [CronetDataSource] wrapping the system [android.net.http.HttpEngine].
 *                          Still uses the OS Chromium stack; no GMS required. HTTP/3 + HTTP/2.
 *
 *   Tier 3 (API 26–29, GMS devices)
 *                       — [CronetDataSource] via Google Play Services [CronetProvider].
 *                          On F-Droid builds [CronetProvider] class is absent at runtime
 *                          → caught → falls through cleanly. HTTP/3 + HTTP/2.
 *
 *   Tier 4 (fallback)   — [OkHttpDataSource] (existing HTTP/2 baseline). Always available.
 *
 * Localhost/loopback streams always use the OkHttp factory (Tier 4) so that the
 * existing gzip-decompression and Accept-Encoding interceptors remain active for
 * the NanoHTTPD torrent proxy.
 */
@UnstableApi
object MediaDataSourceFactory {

    /**
     * Resolves and returns the best available [HttpDataSource.Factory] for the
     * given [context] and request [headers].
     *
     * @param context      Android context (used for HttpEngine / Cronet provider lookup).
     * @param headers      Default request headers to set on the factory.
     * @param okHttpClient The OkHttpClient used as the Tier 4 fallback.
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

        // Tier 1 — API 34+: system HttpEngineDataSource (class lives inside media3-datasource-cronet)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tryHttpEngine(context, headers)?.let { return it }
        }

        // Tier 2 — API 30–33: CronetDataSource wrapping system HttpEngine (no GMS needed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            trySystemCronet(context, headers)?.let { return it }
        }

        // Tier 3 — GMS Cronet provider (absent on F-Droid; caught safely)
        tryCronetProvider(context, headers)?.let { return it }

        // Tier 4 — OkHttp (HTTP/2, always available)
        Logger.log("DataSource: using OkHttp (HTTP/2)")
        return buildOkHttpFactory(okHttpClient, headers)
    }

    // -------------------------------------------------------------------------
    // Tier 1 — system HttpEngineDataSource (API 34+)
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
            .also { Logger.log("DataSource: using HttpEngineDataSource (HTTP/3 + HTTP/2, API 34+)") }
    }.getOrElse { e ->
        Logger.log("DataSource: HttpEngine init failed — ${e.message}")
        null
    }

    // -------------------------------------------------------------------------
    // Tier 2 — CronetDataSource wrapping system HttpEngine (API 30–33)
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.R) // API 30
    private fun trySystemCronet(
        context: Context,
        headers: Map<String, String>,
    ): HttpDataSource.Factory? = runCatching {
        val engine = android.net.http.HttpEngine.Builder(context)
            .setEnableQuic(true)
            .setEnableHttp2(true)
            .build()
        CronetDataSource.Factory(engine, Executors.newCachedThreadPool())
            .apply { setDefaultRequestProperties(headers) }
            .also { Logger.log("DataSource: using CronetDataSource/HttpEngine (HTTP/3 + HTTP/2, API 30+)") }
    }.getOrElse { e ->
        Logger.log("DataSource: system CronetDataSource init failed — ${e.message}")
        null
    }

    // -------------------------------------------------------------------------
    // Tier 3 — GMS CronetProvider (API 26–29 GMS devices)
    // -------------------------------------------------------------------------

    /**
     * Attempts to build a [CronetDataSource.Factory] via the GMS Cronet provider.
     *
     * Catches [Throwable] (not just [Exception]) because on F-Droid builds the
     * [CronetProvider] class is absent → [NoClassDefFoundError] (a [LinkageError]).
     */
    private fun tryCronetProvider(
        context: Context,
        headers: Map<String, String>,
    ): HttpDataSource.Factory? = runCatching {
        val providers = CronetProvider.getAllProviders(context)
        val provider = providers.firstOrNull { p ->
            p.isEnabled && p.name != CronetProvider.PROVIDER_NAME_FALLBACK
        } ?: run {
            Logger.log("DataSource: no active GMS Cronet provider found")
            return@runCatching null
        }
        val engine = provider.createBuilder()
            .enableQuic(true)
            .enableHttp2(true)
            .build()
        CronetDataSource.Factory(engine, Executors.newCachedThreadPool())
            .apply { setDefaultRequestProperties(headers) }
            .also { Logger.log("DataSource: using GMS Cronet '${provider.name}' (HTTP/3 + HTTP/2)") }
    }.getOrElse { e ->
        // NoClassDefFoundError on F-Droid, or runtime failure on GMS device — safe to swallow.
        Logger.log("DataSource: GMS Cronet unavailable — ${e.message}")
        null
    }

    // -------------------------------------------------------------------------
    // Tier 4 (also used directly by DrmDownloader for license requests)
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
