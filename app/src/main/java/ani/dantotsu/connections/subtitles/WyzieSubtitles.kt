package ani.dantotsu.connections.subtitles

import ani.dantotsu.Mapper
import ani.dantotsu.client
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object WyzieSubtitles {

    private const val BASE_URL = "https://sub.wyzie.ru/search"


    suspend fun getWyzieSubtitles(imdbId: String, season: Int, episode: Int): List<WyzieSub> {
        return withContext(Dispatchers.IO) {
            try {
                // Get languages from Prefs
                val languages = PrefManager.getVal<Set<String>>(PrefName.OnlineSubtitleLanguages).joinToString(",")

                val url = "$BASE_URL?id=$imdbId&season=$season&episode=$episode&language=$languages"
                Logger.log("WyzieSubtitles: Fetching from $url")

                val response = client.get(url)
                val text = response.text

                // Basic check for valid JSON array start
                if (text.trim().startsWith("<") || !text.trim().startsWith("[")) {
                     Logger.log("WyzieSubtitles: Invalid response (likely 404/Error Page)")
                     return@withContext emptyList()
                }

                val data = Mapper.json.decodeFromString<List<WyzieSub>>(text)
                Logger.log("WyzieSubtitles: Decoded ${data.size} subs")

                data.sortedWith(compareByDescending<WyzieSub> {
                    it.format.lowercase() == "ass"
                }.thenBy {
                    it.displayLabel
                })

            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}

@Serializable
data class WyzieSub(
    @SerialName("id") val id: String,
    @SerialName("url") val url: String,
    @SerialName("display") val display: String?,
    @SerialName("language") val language: String,
    @SerialName("format") val format: String
) {
    val displayLabel: String
        get() = display ?: language
}
