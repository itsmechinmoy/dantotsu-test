package ani.dantotsu.others

import ani.dantotsu.FileUrl
import ani.dantotsu.client
import ani.dantotsu.media.Media
import ani.dantotsu.media.anime.Episode
import ani.dantotsu.tryWithSuspend
import ani.dantotsu.util.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

object Kitsu {

    suspend fun getKitsuEpisodesDetails(media: Media): Map<String, Episode>? {
        Logger.log("Kitsu : title=${media.mainName()}")
        return try {
            tryWithSuspend {
                // 1. Search for Anime by Title
                val title = URLEncoder.encode(media.mainName(), "utf-8")
                val searchUrl = "https://kitsu.io/api/edge/anime?filter[text]=$title&page[limit]=1"
                val searchRes = client.get(searchUrl).parsed<KitsuAnimeSearch>()
                
                val animeId = searchRes.data?.firstOrNull()?.id ?: return@tryWithSuspend null
                media.idKitsu = animeId

                // 2. Fetch Episodes with Pagination
                val allEpisodes = mutableMapOf<String, Episode>()
                var offset = 0
                val limit = 20
                
                while (true) {
                    val episodesUrl = "https://kitsu.io/api/edge/anime/$animeId/episodes?page[limit]=$limit&page[offset]=$offset&sort=number"
                    val episodesRes = client.get(episodesUrl).parsed<KitsuEpisodes>()
                    
                    val pageEpisodes = episodesRes.data?.associate { ep ->
                        val num = ep.attributes?.number?.toString() ?: return@associate null to null
                        val epNum = if (num.endsWith(".0")) num.substringBefore(".") else num
                        epNum to Episode(
                            number = epNum,
                            title = ep.attributes.canonicalTitle,
                            desc = (ep.attributes.synopsis ?: ep.attributes.description)?.replace(
                                Regex("\\(Source:.*\\)"),
                                ""
                            )?.trim(),
                            thumb = FileUrl[ep.attributes.thumbnail?.original],
                            extra = mapOf(
                                "season" to ep.attributes.seasonNumber.toString(),
                                "airDate" to ep.attributes.airdate.toString(),
                                "length" to ep.attributes.length.toString()
                            )
                        )
                    }?.filterKeys { it != null }?.mapKeys { it.key!! }?.filterValues { it != null }?.mapValues { it.value!! }
                    
                    if (pageEpisodes != null) {
                        allEpisodes.putAll(pageEpisodes)
                    }

                    if (episodesRes.links?.next == null || pageEpisodes.isNullOrEmpty()) {
                        break
                    }
                    offset += limit
                }
                
                allEpisodes
            }
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    data class KitsuAnimeSearch(
        val data: List<AnimeData>? = null
    )

    @Serializable
    data class AnimeData(
        val id: String? = null,
        val type: String? = null
    )

    @Serializable
    data class KitsuEpisodes(
        val data: List<EpisodeData>? = null,
        val meta: Meta? = null,
        val links: Links? = null
    )

    @Serializable
    data class EpisodeData(
        val id: String? = null,
        val type: String? = null,
        val attributes: EpisodeAttributes? = null
    )

    @Serializable
    data class EpisodeAttributes(
        val synopsis: String? = null,
        val description: String? = null,
        val canonicalTitle: String? = null,
        val seasonNumber: Int? = null,
        val number: Int? = null,
        val airdate: String? = null,
        val length: Int? = null,
        val thumbnail: EpisodeThumbnail? = null
    )

    @Serializable
    data class EpisodeThumbnail(
        val original: String? = null
    )

    @Serializable
    data class Meta(
        val count: Int? = null
    )

    @Serializable
    data class Links(
        val first: String? = null,
        val next: String? = null,
        val last: String? = null
    )
}
