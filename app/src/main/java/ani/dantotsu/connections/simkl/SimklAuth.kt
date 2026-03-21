package ani.dantotsu.connections.simkl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class SimklAuth(private val client: HttpClient) {

    companion object {
        private const val AUTH_URL = "https://simkl.com/oauth/authorize"
        const val REDIRECT_URI = "dantotsu://simkl"

        fun startLogin(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                "$AUTH_URL?response_type=code&client_id=${Simkl.CLIENT_ID}&redirect_uri=$REDIRECT_URI"
            ))
            context.startActivity(intent)
        }
    }

    suspend fun exchangeCodeForToken(code: String): String? {
        val url = "https://api.simkl.com/oauth/token"
        val payload = JSONObject().apply {
            put("code", code)
            put("client_id", Simkl.CLIENT_ID)
            put("client_secret", SimklCredentials.CLIENT_SECRET)
            put("redirect_uri", REDIRECT_URI)
            put("grant_type", "authorization_code")
        }.toString()

        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            header("simkl-api-key", Simkl.CLIENT_ID)
            setBody(payload)
        }

        return if (response.status == HttpStatusCode.OK) {
            val responseBody = response.bodyAsText()
            JSONObject(responseBody).getString("access_token")
        } else {
            null
        }
    }

    suspend fun batchUpdateAnime(token: String, animeList: List<AnimeUpdate>): Boolean {
        return try {
            animeList.forEach { anime ->
                val queries = SimklQueries(client)
                queries.updateAnimeProgress(
                    token = token,
                    anilistAnimeId = anime.anilistId,
                    previousProgress = 0,
                    newProgress = anime.progress,
                    status = anime.status,
                    score = if (anime.score > 0) anime.score else null
                )
                delay(500)
            }
            true
        } catch (e: Exception) {
            Log.e("SimklAuth", "Batch update failed", e)
            false
        }
    }
}
