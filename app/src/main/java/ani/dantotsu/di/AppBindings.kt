package ani.dantotsu.di

import android.app.Application
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.download.DownloadsManager
import ani.dantotsu.media.manga.MangaCache
import ani.dantotsu.parsers.novel.NovelExtensionManager
import ani.dantotsu.torrent.TorrentServerManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.preference.AndroidPreferenceStore
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager

@OptIn(ExperimentalSerializationApi::class)
@BindingContainer
object AppBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun providesApplication(context: Context): Application = context.applicationContext as Application

    @Provides
    @SingleIn(AppScope::class)
    fun providesPreferenceStore(application: Application): PreferenceStore = AndroidPreferenceStore(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesSourcePreferences(preferenceStore: PreferenceStore): SourcePreferences = SourcePreferences(preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun providesBasePreferences(application: Application, preferenceStore: PreferenceStore): BasePreferences =
        BasePreferences(application, preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun providesDownloadsManager(application: Application): DownloadsManager = DownloadsManager(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesNetworkHelper(application: Application): NetworkHelper = NetworkHelper(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesJavaScriptEngine(application: Application): JavaScriptEngine = JavaScriptEngine(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesAnimeExtensionManager(
        application: Application,
        sourcePreferences: SourcePreferences,
    ): AnimeExtensionManager = AnimeExtensionManager(application, sourcePreferences)

    @Provides
    @SingleIn(AppScope::class)
    fun providesMangaExtensionManager(
        application: Application,
        sourcePreferences: SourcePreferences,
    ): MangaExtensionManager = MangaExtensionManager(application, sourcePreferences)

    @Provides
    @SingleIn(AppScope::class)
    fun providesNovelExtensionManager(application: Application): NovelExtensionManager = NovelExtensionManager(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesTorrentServerManager(application: Application): TorrentServerManager = TorrentServerManager(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesDownloadAddonManager(application: Application): DownloadAddonManager = DownloadAddonManager(application)

    @Provides
    @SingleIn(AppScope::class)
    fun providesAnimeSourceManager(application: Application, extensionManager: AnimeExtensionManager): AnimeSourceManager =
        AndroidAnimeSourceManager(application, extensionManager)

    @Provides
    @SingleIn(AppScope::class)
    fun providesMangaSourceManager(application: Application, extensionManager: MangaExtensionManager): MangaSourceManager =
        AndroidMangaSourceManager(application, extensionManager)

    @Provides
    @SingleIn(AppScope::class)
    fun providesMangaCache(): MangaCache = MangaCache()

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabaseProvider(context: Context): StandaloneDatabaseProvider = StandaloneDatabaseProvider(context)

    @Provides
    @SingleIn(AppScope::class)
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesProtoBuf(): ProtoBuf = ProtoBuf
}
