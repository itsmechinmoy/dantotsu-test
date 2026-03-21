package ani.dantotsu.connections.simkl

import android.util.Log
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.media.Media
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SimklQueries(private val client: HttpClient) {

    companion object {
        private const val TAG = "SimklQueries"
        private const val BASE_URL = "https://api.simkl.com"
        private val idCache = mutableMapOf<Int, Int?>()
        fun clearCache() { idCache.clear() }
    }

    private suspend fun getSimklIdFromAnilist(anilistId: Int): Int? = withContext(Dispatchers.IO) {
        if (idCache.containsKey(anilistId)) {
            return@withContext idCache[anilistId]
        }
        try {
            val url = "$BASE_URL/search/id?anilist=$anilistId&client_id=${Simkl.CLIENT_ID}"
            Log.d(TAG, "🔍 Searching Simkl ID for AniList: $anilistId")

            val response: HttpResponse = client.get(url)
            val body = response.bodyAsText()
            Log.d(TAG, "Response: $body")

            if (response.status == HttpStatusCode.OK && body.isNotBlank()) {
                val json = JSONArray(body)
                if (json.length() > 0) {
                    val simklId = json.getJSONObject(0).getJSONObject("ids").getInt("simkl")
                    Log.d(TAG, "✅ Found Simkl ID: $simklId")
                    idCache[anilistId] = simklId
                    return@withContext simklId
                }
            }
            idCache[anilistId] = null
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error finding Simkl ID for AniList ID $anilistId: ${e.message}", e)
            idCache[anilistId] = null
            null
        }
    }

    suspend fun updateAnimeProgress(
        token: String,
        anilistAnimeId: Int,
        previousProgress: Int,
        newProgress: Int,
        status: String,
        score: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
//            Log.d(TAG, "📍 updateAnimeProgress START")
//            Log.d(TAG, "  AniList ID: $anilistAnimeId")
//            Log.d(TAG, "  Progress: $previousProgress -> $newProgress")
//            Log.d(TAG, "  Status: $status")
//            Log.d(TAG, "  Score: $score")

            val simklId = getSimklIdFromAnilist(anilistAnimeId)
            if (simklId == null) {
                Log.w(TAG, "⚠️ Anime not found on Simkl")
                return@withContext true
            }

            // ========== STEP 1: Update status ==========
            Log.d(TAG, "📍 Step 1: Update status")

            val statusPayload = JSONObject().apply {
                put("shows", JSONArray().apply {
                    put(JSONObject().apply {
                        put("ids", JSONObject().put("simkl", simklId))
                        put("to", status)
                    })
                })
            }

            Log.d(TAG, "📤 Status payload: $statusPayload")

            val statusResponse: HttpResponse = client.post("$BASE_URL/sync/add-to-list") {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
                contentType(ContentType.Application.Json)
                setBody(statusPayload.toString())
            }

            Log.d(TAG, "📥 Status response: ${statusResponse.status}")

            if (!(statusResponse.status == HttpStatusCode.OK || statusResponse.status == HttpStatusCode.Created)) {
                Log.e(TAG, "❌ Failed to update status")
                return@withContext false
            }

            // ========== STEP 2: Update rating (ONLY if score is provided and > 0) ==========
            if (score != null && score > 0) {  // 🔥 CHANGED: Only if user actually rated it
                Log.d(TAG, "📍 Step 2: Update rating to $score")

                val ratingPayload = JSONObject().apply {
                    put("shows", JSONArray().apply {
                        put(JSONObject().apply {
                            put("ids", JSONObject().put("simkl", simklId))
                            put("rating", score)
                        })
                    })
                }

                Log.d(TAG, "📤 Rating payload: $ratingPayload")

                val ratingResponse: HttpResponse = client.post("$BASE_URL/sync/ratings") {
                    header("Authorization", "Bearer $token")
                    header("simkl-api-key", Simkl.CLIENT_ID)
                    contentType(ContentType.Application.Json)
                    setBody(ratingPayload.toString())
                }

                val ratingBody = ratingResponse.bodyAsText()
                Log.d(TAG, "📥 Rating response: ${ratingResponse.status} - $ratingBody")

                if (!(ratingResponse.status == HttpStatusCode.OK || ratingResponse.status == HttpStatusCode.Created)) {
                    Log.e(TAG, "⚠️ Failed to update rating (non-critical)")
                } else {
                    Log.d(TAG, "✅ Rating updated successfully")
                }
            } else if (score == 0) {
                // 🔥 NEW: Remove rating if user cleared it
                Log.d(TAG, "📍 Step 2: Remove rating (user cleared it)")

                val removeRatingPayload = JSONObject().apply {
                    put("shows", JSONArray().apply {
                        put(JSONObject().apply {
                            put("ids", JSONObject().put("simkl", simklId))
                        })
                    })
                }

                Log.d(TAG, "📤 Remove rating payload: $removeRatingPayload")

                val removeRatingResponse: HttpResponse = client.post("$BASE_URL/sync/ratings/remove") {
                    header("Authorization", "Bearer $token")
                    header("simkl-api-key", Simkl.CLIENT_ID)
                    contentType(ContentType.Application.Json)
                    setBody(removeRatingPayload.toString())
                }

                Log.d(TAG, "📥 Remove rating response: ${removeRatingResponse.status}")
            } else {
                Log.d(TAG, "ℹ️ No rating to update (score is null)")
            }

            // ========== STEP 3: Handle episode updates ==========

            if (newProgress < previousProgress && previousProgress > 0) {
                Log.d(TAG, "📍 Step 3: Episodes decreased ($previousProgress -> $newProgress), removing old history")

                val removePayload = buildEpisodesPayload(simklId, 0)

                val removeResponse: HttpResponse = client.post("$BASE_URL/sync/history/remove") {
                    header("Authorization", "Bearer $token")
                    header("simkl-api-key", Simkl.CLIENT_ID)
                    contentType(ContentType.Application.Json)
                    setBody(removePayload)
                }

                Log.d(TAG, "📥 Remove response: ${removeResponse.status}")
            }

            if (newProgress > 0) {
                val stepNum = if (newProgress < previousProgress) 4 else if (score != null && score > 0) 3 else 2
                Log.d(TAG, "📍 Step $stepNum: Add episodes 1-$newProgress")

                val addPayload = buildEpisodesPayload(simklId, newProgress)

                val addResponse: HttpResponse = client.post("$BASE_URL/sync/history") {
                    header("Authorization", "Bearer $token")
                    header("simkl-api-key", Simkl.CLIENT_ID)
                    contentType(ContentType.Application.Json)
                    setBody(addPayload)
                }

                val addBody = addResponse.bodyAsText()
                Log.d(TAG, "📥 Add episodes response: ${addResponse.status} - $addBody")

                if (!(addResponse.status == HttpStatusCode.OK || addResponse.status == HttpStatusCode.Created)) {
                    Log.e(TAG, "❌ Failed to add episodes")
                    return@withContext false
                }
            }

            Log.d(TAG, "✅ Successfully updated: Status=$status, Progress=$newProgress, Score=$score")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            false
        }
    }

    private fun buildEpisodesPayload(simklId: Int, upToEpisode: Int): String {
        return JSONObject().apply {
            put("shows", JSONArray().apply {
                put(JSONObject().apply {
                    put("ids", JSONObject().put("simkl", simklId))
                    put("episodes", JSONArray().apply {
                        for (ep in 1..upToEpisode) {
                            put(JSONObject().put("number", ep))
                        }
                    })
                })
            })
        }.toString()
    }

    suspend fun removeAnime(token: String, anilistId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📍 removeAnime called for AniList ID: $anilistId")

            val simklId = getSimklIdFromAnilist(anilistId)
            if (simklId == null) {
                Log.w(TAG, "⚠️ Anime not found on Simkl, nothing to delete")
                return@withContext true
            }

            val url = "$BASE_URL/sync/history/remove"
            val payload = JSONObject().apply {
                put("shows", JSONArray().put(
                    JSONObject().put("ids", JSONObject().put("simkl", simklId))
                ))
            }

            Log.d(TAG, "📤 Delete payload: $payload")

            val response: HttpResponse = client.post(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }

            val responseBody = response.bodyAsText()
            Log.d(TAG, "📥 Delete response: ${response.status} - $responseBody")

            val success = response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created
            if (success) {
                Log.d(TAG, "✅ Successfully deleted Simkl ID $simklId")
                idCache.remove(anilistId)
            } else {
                Log.e(TAG, "❌ Failed to delete from Simkl")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing anime $anilistId", e)
            false
        }
    }

    suspend fun getAnimeStatus(token: String, anilistId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val simklId = getSimklIdFromAnilist(anilistId) ?: return@withContext null

            val response: HttpResponse = client.get("$BASE_URL/sync/all-items/anime") {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
            }

            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val json = JSONObject(body)

                val animeArray = json.optJSONArray("anime")
                if (animeArray != null) {
                    for (i in 0 until animeArray.length()) {
                        val item = animeArray.getJSONObject(i)
                        val show = item.optJSONObject("show")
                        val ids = show?.optJSONObject("ids")

                        if (ids?.optInt("simkl") == simklId) {
                            val status = item.optString("status", "unknown")
                            val progress = item.optInt("watched_episodes_count", 0)
                            Log.d(TAG, "✅ Found on Simkl: Status=$status, Progress=$progress")
                            return@withContext "Status: $status, Progress: $progress"
                        }
                    }
                }
                Log.w(TAG, "⚠️ Anime not found in user's list!")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking anime status: ${e.message}", e)
            null
        }
    }

    suspend fun getUserAnimeList(token: String): List<SimklAnimeEntry> {
        return try {
            val url = "$BASE_URL/sync/all-items/anime"
            val response: HttpResponse = client.get(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
            }

            if (response.status != HttpStatusCode.OK) {
                return emptyList()
            }

            val jsonObject = JSONObject(response.bodyAsText())
            val result = mutableListOf<SimklAnimeEntry>()
            val animeArray = jsonObject.optJSONArray("anime")
            if (animeArray != null) {
                for (i in 0 until animeArray.length()) {
                    val item = animeArray.getJSONObject(i)
                    val show = item.optJSONObject("show") ?: continue
                    val ids = show.optJSONObject("ids") ?: continue
                    ids.optInt("anilist", 0).takeIf { it > 0 }?.let { anilistId ->
                        Anilist.query.getMedia(anilistId)?.let { media ->
                            result.add(
                                SimklAnimeEntry(
                                    media = media,
                                    status = item.optString("status"),
                                    progress = item.optInt("watched_episodes_count", 0),
                                    rating = item.optInt("user_rating", 0)
                                )
                            )
                        }
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user anime list", e)
            emptyList()
        }
    }

    suspend fun getUserProfile(token: String): SimklUser? {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get("$BASE_URL/users/settings") {
                    header("Authorization", "Bearer $token")
                    header("simkl-api-key", Simkl.CLIENT_ID)
                }
                if (response.status == HttpStatusCode.OK) {
                    val json = JSONObject(response.bodyAsText())
                    val userJson = json.getJSONObject("user")
                    SimklUser(
                        name = userJson.getString("name"),
                        avatar = userJson.getString("avatar")
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user profile", e)
                null
            }
        }
    }

    data class SimklAnimeEntry(
        val media: Media,
        val status: String,
        val progress: Int,
        val rating: Int = 0
    )

    data class SimklUser(
        val name: String,
        val avatar: String
    )
}
