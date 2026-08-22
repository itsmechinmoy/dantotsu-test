package ani.dantotsu.di.injekt

import android.app.Application
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.download.DownloadsManager
import ani.dantotsu.media.manga.MangaCache
import ani.dantotsu.parsers.novel.NovelExtensionManager
import ani.dantotsu.torrent.TorrentServerManager
import dev.zacsweers.metro.Inject
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

@OptIn(ExperimentalSerializationApi::class)
@Inject
class MetroInteropModule(
    private val context: Context,
    private val downloadsManager: DownloadsManager,
    private val networkHelper: NetworkHelper,
    private val animeExtensionManager: AnimeExtensionManager,
    private val mangaExtensionManager: MangaExtensionManager,
    private val novelExtensionManager: NovelExtensionManager,
    private val torrentServerManager: TorrentServerManager,
    private val downloadAddonManager: DownloadAddonManager,
    private val mangaCache: MangaCache,
    private val databaseProvider: StandaloneDatabaseProvider,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(context)
        if (context is Application) {
            addSingleton<Application>(context)
        }

        addSingleton(downloadsManager)
        addSingleton(networkHelper)
        addSingleton(networkHelper.client)
        addSingletonFactory { JavaScriptEngine(context) }

        addSingleton(animeExtensionManager)
        addSingleton(mangaExtensionManager)
        addSingleton(novelExtensionManager)
        addSingleton(torrentServerManager)
        addSingleton(downloadAddonManager)

        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(context, get()) }
        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(context, get()) }

        addSingleton(json)
        addSingleton(protoBuf)
        addSingleton(databaseProvider)
        addSingleton(mangaCache)

        // Preferences
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(context as Application)
        }
        addSingletonFactory {
            SourcePreferences(Injekt.get())
        }
        addSingletonFactory {
            BasePreferences(context, Injekt.get())
        }
    }
}
