package ani.dantotsu.widgets.upcoming

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.BitmapUtil.downloadImageAsBitmap
import ani.dantotsu.util.Logger
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class UpcomingRemoteViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {
    private var widgetItems = mutableListOf<WidgetItem>()
    private var refreshing = false
    private val prefs =
        context.getSharedPreferences(UpcomingWidget.PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate() {
        Logger.log("UpcomingRemoteViewsFactory onCreate")
        fillWidgetItems()
    }

    private fun timeUntil(timeUntil: Long): String {
        val days = timeUntil / (1000 * 60 * 60 * 24)
        val hours = (timeUntil % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        val minutes = ((timeUntil % (1000 * 60 * 60 * 24)) % (1000 * 60 * 60)) / (1000 * 60)
        return buildString {
            if (days > 0) append("$days day${if (days > 1) "s" else ""} ")
            if (hours > 0 || days > 0) append("$hours hour${if (hours > 1) "s" else ""} ")
            append("$minutes minute${if (minutes > 1) "s" else ""}")
        }.trim()
    }

    override fun onDataSetChanged() {
        if (refreshing) return
        Logger.log("UpcomingRemoteViewsFactory onDataSetChanged")
        widgetItems.clear()
        fillWidgetItems()
    }

    private fun fillWidgetItems() {
        refreshing = true
        val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
        val prefs = context.getSharedPreferences(UpcomingWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdated = prefs.getLong(UpcomingWidget.LAST_UPDATE, 0)
        val serializedMedia = prefs.getString(UpcomingWidget.PREF_SERIALIZED_MEDIA, "")
        if (System.currentTimeMillis() - lastUpdated > 1000 * 60 * 60 * 4 || serializedMedia.isNullOrEmpty()) {
            runBlocking(Dispatchers.IO) {
                val upcoming = Anilist.query.getUpcomingAnime(userId)
                val seen = mutableSetOf<Int>()
                upcoming.forEach { media ->
                    if (seen.add(media.id)) {
                        // Get the next episode number from anime.nextAiringEpisode
                        // Add 1 because nextAiringEpisode is 0-indexed (episode - 1)
                        val nextEpisode = media.anime?.nextAiringEpisode?.let { it + 1 }
                        Logger.log("Episode for ${media.userPreferredName}: $nextEpisode")
                        
                        widgetItems.add(
                            WidgetItem(
                                title = media.userPreferredName,
                                countdown = timeUntil(media.timeUntilAiring ?: 0),
                                image = media.cover ?: "",
                                id = media.id,
                                episode = nextEpisode
                            )
                        )
                    }
                }
                prefs.edit().putLong(UpcomingWidget.LAST_UPDATE, System.currentTimeMillis()).apply()
                val serialized = serializeMedia(upcoming)
                if (serialized != null) {
                    prefs.edit().putString(UpcomingWidget.PREF_SERIALIZED_MEDIA, serialized).apply()
                } else {
                    prefs.edit().putString(UpcomingWidget.PREF_SERIALIZED_MEDIA, "").apply()
                    Logger.log("Error serializing media")
                }
                refreshing = false
            }
        } else {
            refreshing = false
            val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdated
            val media = deserializeMedia(serializedMedia)
            if (media != null) {
                val seen = mutableSetOf<Int>()
                media.forEach { mediaItem ->
                    if (seen.add(mediaItem.id)) {
                        val remainingTime = (mediaItem.timeUntilAiring?.minus(timeSinceLastUpdate) ?: 0).coerceAtLeast(0)
                        val nextEpisode = mediaItem.anime?.nextAiringEpisode?.let { it + 1 }
                        widgetItems.add(
                            WidgetItem(
                                title = mediaItem.userPreferredName,
                                countdown = timeUntil(remainingTime),
                                image = mediaItem.cover ?: "",
                                id = mediaItem.id,
                                episode = nextEpisode
                            )
                        )
                    }
                }
            } else {
                prefs.edit().putString(UpcomingWidget.PREF_SERIALIZED_MEDIA, "").apply()
                prefs.edit().putLong(UpcomingWidget.LAST_UPDATE, 0).apply()
                Logger.log("Error deserializing media")
                fillWidgetItems()
            }
        }
    }

    private fun serializeMedia(media: List<Media>): String? {
        return try {
            val gson = GsonBuilder()
                .registerTypeAdapter(SChapter::class.java, InstanceCreator<SChapter> {
                    SChapterImpl()
                })
                .registerTypeAdapter(SAnime::class.java, InstanceCreator<SAnime> {
                    SAnimeImpl()
                })
                .registerTypeAdapter(SEpisode::class.java, InstanceCreator<SEpisode> {
                    SEpisodeImpl()
                })
                .create()
            gson.toJson(media)
        } catch (e: Exception) {
            Logger.log("Error serializing media: $e")
            Logger.log(e)
            null
        }
    }

    private fun deserializeMedia(json: String): List<Media>? {
        return try {
            val gson = GsonBuilder()
                .registerTypeAdapter(SChapter::class.java, InstanceCreator<SChapter> {
                    SChapterImpl()
                })
                .registerTypeAdapter(SAnime::class.java, InstanceCreator<SAnime> {
                    SAnimeImpl()
                })
                .registerTypeAdapter(SEpisode::class.java, InstanceCreator<SEpisode> {
                    SEpisodeImpl()
                })
                .create()
            gson.fromJson(json, Array<Media>::class.java).toList()
        } catch (e: Exception) {
            Logger.log("Error deserializing media: $e")
            Logger.log(e)
            null
        }
    }

    override fun onDestroy() {
        widgetItems.clear()
    }

    override fun getCount(): Int = widgetItems.size

    override fun getViewAt(position: Int): RemoteViews {
        Logger.log("UpcomingRemoteViewsFactory getViewAt")
        val item = widgetItems[position]
        val titleTextColor = prefs.getInt(UpcomingWidget.PREF_TITLE_TEXT_COLOR, Color.WHITE)
        val countdownTextColor = prefs.getInt(UpcomingWidget.PREF_COUNTDOWN_TEXT_COLOR, Color.WHITE)
        val rv = RemoteViews(context.packageName, R.layout.item_upcoming_widget).apply {
            setTextViewText(R.id.text_show_title, item.title)
            setTextViewText(R.id.text_show_countdown, item.countdown)
            val episodeText = if (item.episode != null) {
                "Episode ${item.episode}"
            } else {
                ""
            }
            setTextViewText(R.id.text_show_episode, episodeText)
            setTextColor(R.id.text_show_title, titleTextColor)
            setTextColor(R.id.text_show_countdown, countdownTextColor)
            setTextColor(R.id.text_show_episode, countdownTextColor)
            
            val bitmap = downloadImageAsBitmap(item.image)
            setImageViewBitmap(R.id.image_show_icon, bitmap)
            
            val fillInIntent = Intent().apply {
                putExtra("mediaId", item.id)
            }
            setOnClickFillInIntent(R.id.widget_item, fillInIntent)
        }
        return rv
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.item_upcoming_widget)

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(p0: Int): Long = p0.toLong()

    override fun hasStableIds(): Boolean = true
}

data class WidgetItem(
    val title: String,
    val countdown: String,
    val image: String,
    val id: Int,
    val episode: Int?
)
