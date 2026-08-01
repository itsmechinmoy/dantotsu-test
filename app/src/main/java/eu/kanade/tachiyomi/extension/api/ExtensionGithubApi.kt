package eu.kanade.tachiyomi.extension.api

import ani.dantotsu.asyncMap
import ani.dantotsu.media.MediaType
import ani.dantotsu.parsers.novel.AvailableNovelSources
import ani.dantotsu.parsers.novel.NovelExtension
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AvailableAnimeSources
import eu.kanade.tachiyomi.extension.manga.model.AvailableMangaSources
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import java.io.ByteArrayInputStream
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

internal class ExtensionGithubApi {
    private val networkService: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private fun List<ExtensionSourceJsonObject>.toAnimeExtensionSources(): List<AvailableAnimeSources> {
        return this.map {
            AvailableAnimeSources(
                id = it.id,
                lang = it.lang,
                name = it.name,
                baseUrl = it.baseUrl,
            )
        }
    }

    private fun List<ExtensionJsonObject>.toAnimeExtensions(repository: String): List<AnimeExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                val majorLibVersion = libVersion.toInt()
                majorLibVersion >= ExtensionLoader.ANIME_LIB_VERSION_MIN && majorLibVersion <= ExtensionLoader.ANIME_LIB_VERSION_MAX
            }
            .map {
                AnimeExtension.Available(
                    name = it.name.substringAfter("Aniyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources?.toAnimeExtensionSources().orEmpty(),
                    apkName = it.apk,
                    repository = repository,
                    iconUrl = it.iconUrl ?: "${repository.removeSuffix("/index.min.json").removeSuffix("/index.json").removeSuffix("/repo.json")}/icon/${it.pkg}.png",
                )
            }
    }

    private fun updateStoreUrl(oldUrl: String, newUrl: String, mediaType: MediaType) {
        val prefName = when (mediaType) {
            MediaType.ANIME -> PrefName.AnimeExtensionRepos
            MediaType.MANGA -> PrefName.MangaExtensionRepos
            MediaType.NOVEL -> PrefName.NovelExtensionRepos
        }
        val current = PrefManager.getVal<Set<String>>(prefName)
        if (current.contains(oldUrl)) {
            val updated = current.minus(oldUrl).plus(newUrl)
            PrefManager.setVal(prefName, updated)
        }
    }

    private fun ByteArray.decompressIfGzipped(): ByteArray {
        if (this.size < 2) return this
        val isGzip = (this[0].toInt() and 0xFF == 0x1F) && (this[1].toInt() and 0xFF == 0x8B)
        if (!isGzip) return this
        return try {
            ByteArrayInputStream(this).source().gzip().buffer().readByteArray()
        } catch (e: Throwable) {
            this
        }
    }

    private fun normalizeRepoUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.startsWith("github.com/")) {
            url = "https://$url"
        }
        if (url.contains("github.com/") && url.contains("/raw/")) {
            url = url.replace("github.com/", "raw.githubusercontent.com/").replace("/raw/", "/")
        }
        return url
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun fetchExtensions(
        repoUrl: String,
        mediaType: MediaType,
        originalUrl: String = repoUrl
    ): List<ExtensionJsonObject> {
        val normalizedUrl = normalizeRepoUrl(repoUrl)
        var targetUrl = normalizedUrl
        if (!targetUrl.endsWith(".json") && !targetUrl.endsWith(".pb")) {
            targetUrl = "$normalizedUrl${if (normalizedUrl.endsWith('/')) "" else "/"}repo.json"
        }

        try {
            val response = try {
                networkService.client
                    .newCall(GET(targetUrl))
                    .awaitSuccess()
            } catch (e: Throwable) {
                if (targetUrl.endsWith("repo.json")) {
                    val fallback = targetUrl.replace("repo.json", "index.min.json")
                    networkService.client.newCall(GET(fallback)).awaitSuccess()
                } else if (targetUrl.endsWith("index.min.json") || targetUrl.endsWith("index.json")) {
                    val fallbackPb = targetUrl.substringBeforeLast("/") + "/index.pb"
                    networkService.client.newCall(GET(fallbackPb)).awaitSuccess()
                } else {
                    throw e
                }
            }

            val rawBytes = response.body.bytes()
            if (rawBytes.isEmpty()) return emptyList()

            val responseBytes = rawBytes.decompressIfGzipped()
            if (responseBytes.isEmpty()) return emptyList()
            val firstByte = responseBytes[0]

            if (firstByte == 0x5B.toByte()) { // '[' -> JSON array
                val bodyString = responseBytes.toString(Charsets.UTF_8)
                val jsonList = runCatching { json.decodeFromString<List<ExtensionJsonObject>>(bodyString) }.getOrElse { emptyList() }

                val isDummyNotice = jsonList.any {
                    it.pkg.contains("keiyoushi") || it.pkg.contains("mihon") ||
                        it.name.contains("Outdated App", ignoreCase = true) ||
                        it.name.contains("Update to Mihon", ignoreCase = true)
                }

                if (isDummyNotice || targetUrl.contains("index.min.json")) {
                    val repoJsonUrl = targetUrl.substringBeforeLast("/") + "/repo.json"
                    if (repoJsonUrl != targetUrl) {
                        val repoResult = runCatching { fetchExtensions(repoJsonUrl, mediaType, originalUrl) }.getOrNull()
                        if (!repoResult.isNullOrEmpty() && repoResult.none { it.pkg.contains("keiyoushi") || it.pkg.contains("mihon") }) {
                            return repoResult
                        }
                    }
                    val pbUrl = targetUrl.substringBeforeLast("/") + "/index.pb"
                    if (pbUrl != targetUrl) {
                        val pbResult = runCatching { fetchExtensions(pbUrl, mediaType, originalUrl) }.getOrNull()
                        if (!pbResult.isNullOrEmpty()) {
                            return pbResult
                        }
                    }
                }

                return jsonList
            } else {
                val store = if (firstByte == 0x7B.toByte()) { // '{'
                    val bodyString = responseBytes.toString(Charsets.UTF_8)
                    if (bodyString.contains("\"index_v2\"") || bodyString.contains("\"indexV2\"")) {
                        val legacyRepo = json.decodeFromString<NetworkLegacyExtensionRepo>(bodyString)
                        val nextUrl = legacyRepo.indexV2
                        if (nextUrl != null) {
                            updateStoreUrl(originalUrl, nextUrl, mediaType)
                            return fetchExtensions(nextUrl, mediaType, originalUrl)
                        }
                    }
                    json.decodeFromString<NetworkExtensionStore>(bodyString)
                } else { // Protobuf (.pb)
                    try {
                        ProtoBuf.decodeFromByteArray<NetworkExtensionStore>(responseBytes)
                    } catch (e: Throwable) {
                        val directList = runCatching { ProtoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(responseBytes) }.getOrNull()
                        if (directList != null) {
                            return mapExtensionList(directList, mediaType)
                        }
                        throw e
                    }
                }

                val resolvedList: NetworkExtensionStore.ExtensionList? = if (store.extensionListUrl != null) {
                    val listResponse = networkService.client.newCall(GET(store.extensionListUrl)).awaitSuccess()
                    val listBytes = listResponse.body.bytes().decompressIfGzipped()
                    if (listBytes.isNotEmpty() && listBytes[0] == 0x7B.toByte()) { // '{'
                        json.decodeFromString<NetworkExtensionStore.ExtensionList>(listBytes.toString(Charsets.UTF_8))
                    } else if (listBytes.isNotEmpty()) {
                        ProtoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(listBytes)
                    } else {
                        null
                    }
                } else {
                    store.extensionList
                }

                if (resolvedList != null) {
                    return mapExtensionList(resolvedList, mediaType)
                } else if (targetUrl.endsWith("repo.json")) {
                    val fallback = targetUrl.replace("repo.json", "index.min.json")
                    return fetchExtensions(fallback, mediaType, originalUrl)
                }
            }
        } catch (e: Throwable) {
            Logger.log("fetchExtensions error for $repoUrl: $e")
        }
        return emptyList()
    }

    private fun mapExtensionList(
        resolvedList: NetworkExtensionStore.ExtensionList,
        mediaType: MediaType
    ): List<ExtensionJsonObject> {
        val prefix = when (mediaType) {
            MediaType.ANIME -> "Aniyomi: "
            MediaType.MANGA -> "Tachiyomi: "
            else -> ""
        }
        return resolvedList.extensions.map { ext ->
            val sourcesMapped = ext.sources.map { src ->
                ExtensionSourceJsonObject(
                    id = src.id,
                    lang = src.language,
                    name = src.name,
                    baseUrl = src.homeUrl
                )
            }
            val primaryLang = ext.sources.firstOrNull()?.language ?: "all"
            val prefixName = if (ext.name.startsWith(prefix)) ext.name else "$prefix${ext.name}"
            ExtensionJsonObject(
                name = prefixName,
                pkg = ext.packageName,
                apk = ext.resources.apkUrl,
                lang = primaryLang,
                code = ext.versionCode,
                version = ext.versionName,
                nsfw = if (ext.contentWarning == NetworkExtensionStore.ContentWarning.NSFW || ext.contentWarning == NetworkExtensionStore.ContentWarning.MIXED) 1 else 0,
                hasReadme = 0,
                hasChangelog = 0,
                sources = sourcesMapped,
                iconUrl = ext.resources.iconUrl,
                extensionLib = ext.extensionLib,
            )
        }
    }

    suspend fun findAnimeExtensions(): List<AnimeExtension.Available> {
        return withIOContext {
            val extensions: ArrayList<AnimeExtension.Available> = arrayListOf()
            val repos = PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos).toMutableList()

            repos.asyncMap {
                try {
                    var repoExtensions = fetchExtensions(it, MediaType.ANIME)
                    if (repoExtensions.isEmpty()) {
                        val fallback = fallbackRepoUrl(it)
                        if (fallback != null) {
                            repoExtensions = fetchExtensions(fallback, MediaType.ANIME)
                        }
                    }
                    extensions.addAll(repoExtensions.toAnimeExtensions(it))
                } catch (e: Throwable) {
                    Logger.log("Failed to get anime extensions")
                    Logger.log(e)
                }
            }
            extensions
        }
    }

    fun getAnimeApkUrl(extension: AnimeExtension.Available): String {
        return if (extension.apkName.startsWith("http")) {
            extension.apkName
        } else {
            "${extension.repository.removeSuffix("index.min.json")}/apk/${extension.apkName}"
        }
    }

    private fun List<ExtensionSourceJsonObject>.toMangaExtensionSources(): List<AvailableMangaSources> {
        return this.map {
            AvailableMangaSources(
                id = it.id,
                lang = it.lang,
                name = it.name,
                baseUrl = it.baseUrl,
            )
        }
    }

    private fun List<ExtensionJsonObject>.toMangaExtensions(repository: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.MANGA_LIB_VERSION_MIN && libVersion <= ExtensionLoader.MANGA_LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources?.toMangaExtensionSources().orEmpty(),
                    apkName = it.apk,
                    repository = repository,
                    iconUrl = it.iconUrl ?: "${repository.removeSuffix("/index.min.json").removeSuffix("/index.json").removeSuffix("/repo.json")}/icon/${it.pkg}.png",
                )
            }
    }

    suspend fun findMangaExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            val extensions: ArrayList<MangaExtension.Available> = arrayListOf()
            val repos = PrefManager.getVal<Set<String>>(PrefName.MangaExtensionRepos).toMutableList()

            repos.asyncMap {
                try {
                    var repoExtensions = fetchExtensions(it, MediaType.MANGA)
                    if (repoExtensions.isEmpty()) {
                        val fallback = fallbackRepoUrl(it)
                        if (fallback != null) {
                            repoExtensions = fetchExtensions(fallback, MediaType.MANGA)
                        }
                    }
                    extensions.addAll(repoExtensions.toMangaExtensions(it))
                } catch (e: Throwable) {
                    Logger.log("Failed to get manga extensions")
                    Logger.log(e)
                }
            }
            extensions
        }
    }

    fun getMangaApkUrl(extension: MangaExtension.Available): String {
        return if (extension.apkName.startsWith("http")) {
            extension.apkName
        } else {
            "${extension.repository.removeSuffix("index.min.json")}/apk/${extension.apkName}"
        }
    }

    suspend fun findNovelExtensions(): List<NovelExtension.Available> {
        return withIOContext {
            val extensions: ArrayList<NovelExtension.Available> = arrayListOf()
            val repos = PrefManager.getVal<Set<String>>(PrefName.NovelExtensionRepos).toMutableList()

            repos.asyncMap {
                try {
                    var repoExtensions = fetchExtensions(it, MediaType.NOVEL)
                    if (repoExtensions.isEmpty()) {
                        val fallback = fallbackRepoUrl(it)
                        if (fallback != null) {
                            repoExtensions = fetchExtensions(fallback, MediaType.NOVEL)
                        }
                    }
                    extensions.addAll(repoExtensions.toNovelExtensions(it))
                } catch (e: Throwable) {
                    Logger.log("Failed to get novel extensions")
                    Logger.log(e)
                }
            }
            extensions
        }
    }

    private fun List<ExtensionJsonObject>.toNovelExtensions(repository: String): List<NovelExtension.Available> {
        return mapNotNull { extension ->
            val sources = extension.sources?.map { source ->
                ExtensionSourceJsonObject(
                    source.id,
                    source.lang,
                    source.name,
                    source.baseUrl,
                )
            }
            val iconUrl = extension.iconUrl ?: "${repository.removeSuffix("/index.min.json").removeSuffix("/index.json").removeSuffix("/repo.json")}/icon/${extension.pkg}.png"
            NovelExtension.Available(
                extension.name,
                extension.pkg,
                extension.apk,
                extension.code,
                repository,
                sources?.toNovelSources() ?: emptyList(),
                iconUrl,
            )
        }
    }

    private fun List<ExtensionSourceJsonObject>.toNovelSources(): List<AvailableNovelSources> {
        return map { source ->
            AvailableNovelSources(
                source.id,
                source.lang,
                source.name,
                source.baseUrl,
            )
        }
    }

    fun getNovelApkUrl(extension: NovelExtension.Available): String {
        return if (extension.versionName.startsWith("http")) {
            extension.versionName
        } else {
            "${extension.repository.removeSuffix("index.min.json")}/apk/${extension.pkgName}.apk"
        }
    }

    private fun fallbackRepoUrl(repoUrl: String): String? {
        var fallbackRepoUrl = "https://gcore.jsdelivr.net/gh/"
        val strippedRepoUrl = repoUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .removeSuffix("/index.min.json")
            .removeSuffix("/index.json")
            .removeSuffix("/repo.json")
        val repoUrlParts = strippedRepoUrl.split("/")
        if (repoUrlParts.size < 3) {
            return null
        }
        val repoOwner = repoUrlParts[1]
        val repoName = repoUrlParts[2]
        fallbackRepoUrl += "$repoOwner/$repoName"
        val repoBranch = if (repoUrlParts.size > 3) {
            repoUrlParts[3]
        } else {
            "main"
        }
        fallbackRepoUrl += "@$repoBranch"
        return fallbackRepoUrl
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<ExtensionSourceJsonObject>?,
    val iconUrl: String? = null,
    val extensionLib: String? = null,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private fun ExtensionJsonObject.extractLibVersion(): Double {
    extensionLib?.toDoubleOrNull()?.let { return it }
    val parts = version.split('.')
    return if (parts.size >= 2) {
        val majorMinor = "${parts[0]}.${parts[1]}"
        majorMinor.toDoubleOrNull() ?: parts[0].toDoubleOrNull() ?: 1.0
    } else {
        version.toDoubleOrNull() ?: 1.0
    }
}
