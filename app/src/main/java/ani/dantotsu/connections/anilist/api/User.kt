package ani.dantotsu.connections.anilist.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class User(
    @SerialName("id") var id: Int = 0,
    @SerialName("name") var name: String? = null,
    @SerialName("about") var about: String? = null,
    @SerialName("avatar") var avatar: UserAvatar? = null,
    @SerialName("bannerImage") var bannerImage: String? = null,
    @SerialName("isFollowing") var isFollowing: Boolean? = null,
    @SerialName("isFollower") var isFollower: Boolean? = null,
    @SerialName("isBlocked") var isBlocked: Boolean? = null,
    @SerialName("options") var options: UserOptions? = null,
    @SerialName("mediaListOptions") var mediaListOptions: MediaListOptions? = null,
    @SerialName("favourites") var favourites: Favourites? = null,
    @SerialName("statistics") var statistics: UserStatisticTypes? = null,
    @SerialName("unreadNotificationCount") var unreadNotificationCount: Int? = null,
    @SerialName("siteUrl") var siteUrl: String? = null,
) : java.io.Serializable

@Serializable
data class UserOptions(
    @SerialName("titleLanguage") var titleLanguage: UserTitleLanguage? = null,
    @SerialName("displayAdultContent") var displayAdultContent: Boolean? = null,
    @SerialName("airingNotifications") var airingNotifications: Boolean? = null,
    @SerialName("profileColor") var profileColor: String? = null,
    @SerialName("timezone") var timezone: String? = null,
    @SerialName("activityMergeTime") var activityMergeTime: Int? = null,
    @SerialName("staffNameLanguage") var staffNameLanguage: UserStaffNameLanguage? = null,
    @SerialName("restrictMessagesToFollowing") var restrictMessagesToFollowing: Boolean? = null,
) : java.io.Serializable

@Serializable
data class UserAvatar(
    @SerialName("large") var large: String? = null,
    @SerialName("medium") var medium: String? = null,
) : java.io.Serializable

@Serializable
data class UserStatisticTypes(
    @SerialName("anime") var anime: UserStatistics? = null,
    @SerialName("manga") var manga: UserStatistics? = null
) : java.io.Serializable

@Serializable
enum class UserTitleLanguage {
    @SerialName("ENGLISH")
    ENGLISH,

    @SerialName("ROMAJI")
    ROMAJI,

    @SerialName("NATIVE")
    NATIVE
}

@Serializable
enum class UserStaffNameLanguage {
    @SerialName("ROMAJI_WESTERN")
    ROMAJI_WESTERN,

    @SerialName("ROMAJI")
    ROMAJI,

    @SerialName("NATIVE")
    NATIVE
}

@Serializable
enum class ScoreFormat {
    @SerialName("POINT_100")
    POINT_100,

    @SerialName("POINT_10_DECIMAL")
    POINT_10_DECIMAL,

    @SerialName("POINT_10")
    POINT_10,

    @SerialName("POINT_5")
    POINT_5,

    @SerialName("POINT_3")
    POINT_3,
}

@Serializable
data class UserStatistics(
    //
    @SerialName("count") var count: Int?,
    @SerialName("meanScore") var meanScore: Float?,
    @SerialName("standardDeviation") var standardDeviation: Float?,
    @SerialName("minutesWatched") var minutesWatched: Int?,
    @SerialName("episodesWatched") var episodesWatched: Int?,
    @SerialName("chaptersRead") var chaptersRead: Int?,
    @SerialName("volumesRead") var volumesRead: Int?,
    //    @SerialName("formats") var formats: List<UserFormatStatistic>?,
    //    @SerialName("statuses") var statuses: List<UserStatusStatistic>?,
    //    @SerialName("scores") var scores: List<UserScoreStatistic>?,
    //    @SerialName("lengths") var lengths: List<UserLengthStatistic>?,
    //    @SerialName("releaseYears") var releaseYears: List<UserReleaseYearStatistic>?,
    //    @SerialName("startYears") var startYears: List<UserStartYearStatistic>?,
    //    @SerialName("genres") var genres: List<UserGenreStatistic>?,
    //    @SerialName("tags") var tags: List<UserTagStatistic>?,
    //    @SerialName("countries") var countries: List<UserCountryStatistic>?,
    //    @SerialName("voiceActors") var voiceActors: List<UserVoiceActorStatistic>?,
    //    @SerialName("staff") var staff: List<UserStaffStatistic>?,
    //    @SerialName("studios") var studios: List<UserStudioStatistic>?,
)

@Serializable
data class Favourites(
    // Favourite anime
    @SerialName("anime") var anime: MediaConnection?,

    // Favourite manga
    @SerialName("manga") var manga: MediaConnection?,

    // Favourite characters
    @SerialName("characters") var characters: CharacterConnection?,

    // Favourite staff
    @SerialName("staff") var staff: StaffConnection?,

    // Favourite studios
    @SerialName("studios") var studios: StudioConnection?,
)

@Serializable
data class MediaListOptions(
    // The score format the user is using for media lists
    @SerialName("scoreFormat") var scoreFormat: ScoreFormat?,

    // The default order list rows should be displayed in
    @SerialName("rowOrder") var rowOrder: String?,

    // The user's anime list options
    @SerialName("animeList") var animeList: MediaListTypeOptions?,

    // The user's manga list options
    @SerialName("mangaList") var mangaList: MediaListTypeOptions?,
)

@Serializable
data class MediaListTypeOptions(
    // The order each list should be displayed in
    @SerialName("sectionOrder") var sectionOrder: List<String>?,

    //    // If the completed sections of the list should be separated by format
    //    @SerialName("splitCompletedSectionByFormat") var splitCompletedSectionByFormat: Boolean?,

    // The names of the user's custom lists
    @SerialName("customLists") var customLists: List<String>?,
    //
    //    // The names of the user's advanced scoring sections
    //    @SerialName("advancedScoring") var advancedScoring: List<String>?,
    //
    //    // If advanced scoring is enabled
    //    @SerialName("advancedScoringEnabled") var advancedScoringEnabled: Boolean?,
)
