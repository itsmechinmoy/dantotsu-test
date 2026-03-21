package ani.dantotsu.connections.simkl

import android.util.Log
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlin.math.roundToInt

class Simkl private constructor() {

    companion object {
        val CLIENT_ID = SimklCredentials.CLIENT_ID
        private const val TAG = "Simkl"

        @Volatile
        private var instance: Simkl? = null

        fun getInstance(): Simkl {
            return instance ?: synchronized(this) {
                instance ?: Simkl().also { instance = it }
            }
        }

        fun mapAniListToSimklStatus(anilistStatus: String): String {
            return when (anilistStatus.uppercase()) {
                "CURRENT", "WATCHING" -> "watching"
                "COMPLETED" -> "completed"
                "PLANNING", "PLAN_TO_WATCH" -> "plantowatch"
                "PAUSED", "ON_HOLD" -> "hold"
                "DROPPED" -> "dropped"
                "REPEATING" -> "watching"
                else -> "plantowatch"
            }
        }
    }


    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 15000
        }
    }
    val queries = SimklQueries(client)
    val auth = SimklAuth(client)

    var accessToken: String
        get() = PrefManager.getVal(PrefName.SimklToken, "")
        set(value) {
            PrefManager.setVal(PrefName.SimklToken, value)
            setEnabled(value.isNotEmpty())
        }

    // 🔥 CHANGE THIS - Add in-memory cache like AniList
    var username: String? = null
        private set

    var avatar: String? = null
        private set

    var user: SimklQueries.SimklUser?
        get() {
            // 🔥 LOAD FROM MEMORY FIRST
            if (username != null && avatar != null) {
                return SimklQueries.SimklUser(username!!, avatar!!)
            }
            // Then try from prefs
            val name = PrefManager.getVal(PrefName.SimklUsername, "")
            val avatarUrl = PrefManager.getVal(PrefName.SimklAvatar, "")
            if (name.isNotEmpty()) {
                username = name
                avatar = avatarUrl
                return SimklQueries.SimklUser(name, avatarUrl)
            }
            return null
        }
        set(value) {
            // 🔥 SAVE TO BOTH MEMORY AND PREFS
            username = value?.name
            avatar = value?.avatar
            PrefManager.setVal(PrefName.SimklUsername, value?.name ?: "")
            PrefManager.setVal(PrefName.SimklAvatar, value?.avatar ?: "")
        }

    // 🔥 ONLY FETCH WHEN NEEDED
    suspend fun fetchAndSaveUser() {
        if (isLoggedIn()) {
            user = queries.getUserProfile(accessToken)
        }
    }

    fun isLoggedIn(): Boolean {
        return accessToken.isNotEmpty()
    }

    fun isEnabled(): Boolean {
        return PrefManager.getVal(PrefName.SimklEnabled, false)
    }

    fun setEnabled(enabled: Boolean) {
        PrefManager.setVal(PrefName.SimklEnabled, enabled)
        Log.d(TAG, "Simkl anime sync ${if (enabled) "enabled" else "disabled"}")
    }

    // Standard method to retrieve saved token (matches AniList/MAL pattern)
    fun getSavedToken(): Boolean {
        val token = PrefManager.getVal(PrefName.SimklToken, "")
        accessToken = token
        return token.isNotEmpty()
    }

    // Standard method to clear token and user data (matches AniList/MAL pattern)
    fun removeSavedToken() {
        accessToken = ""
        username = null
        avatar = null
        user = null
        PrefManager.removeVal(PrefName.SimklToken)
        PrefManager.removeVal(PrefName.SimklUsername)
        PrefManager.removeVal(PrefName.SimklAvatar)
        SimklQueries.clearCache()
        Log.d(TAG, "Removed Simkl token and cleared user data")
    }

    // Kept for backwards compatibility
    fun logout() {
        removeSavedToken()
    }

    suspend fun updateAnimeProgress(
        anilistAnimeId: Int,
        previousProgress: Int = 0,
        newProgress: Int,
        status: String = "CURRENT",
        score: Int? = null  // ← Make sure this is being passed!
    ): Boolean {
        if (!isEnabled() || !isLoggedIn()) return false

        val simklStatus = mapAniListToSimklStatus(status)

        // 🔥 ADD DEBUG HERE
        Log.d(TAG, "📍 Simkl.updateAnimeProgress received score: $score")

        val simklScore = score?.let {
            when {
                it == 0 -> null
                // Scores from the app are in 100-point scale (user input 1 → 10, 10 → 100)
                // Simkl uses 10-point scale, so always divide by 10
                else -> (it / 10.0).roundToInt().coerceIn(1, 10)
            }
        }

        // 🔥 ADD DEBUG HERE
        Log.d(TAG, "📍 Converted to simklScore: $simklScore")

        return queries.updateAnimeProgress(
            token = accessToken,
            anilistAnimeId = anilistAnimeId,
            previousProgress = previousProgress,
            newProgress = newProgress,
            status = simklStatus,
            score = simklScore
        )
    }

    suspend fun removeAnime(anilistAnimeId: Int): Boolean {
        if (!isLoggedIn()) return false

        return try {
            queries.removeAnime(accessToken, anilistAnimeId)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing anime: ${e.message}", e)
            false
        }
    }

    suspend fun getUserAnimeList(): List<SimklQueries.SimklAnimeEntry> {
        if (!isLoggedIn()) return emptyList()

        return try {
            queries.getUserAnimeList(accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting anime list: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun exportAnimeList(animeList: List<AnimeUpdate>): Boolean {
        if (!isLoggedIn()) return false

        return try {
            auth.batchUpdateAnime(accessToken, animeList)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting anime: ${e.message}", e)
            false
        }
    }
}

data class AnimeUpdate(
    val anilistId: Int,
    val progress: Int,
    val status: String,
    val score: Int
)
