package ani.dantotsu.connections.anilist

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import ani.dantotsu.loadMedia
import ani.dantotsu.startMainActivity
import ani.dantotsu.themes.ThemeManager

class UrlMedia : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        val data: Uri? = intent?.data
        if (data == null) {
            startMainActivity(this)
            return
        }

        try {
            when (data.scheme?.lowercase()) {
                "dantotsu" -> handleDantotsuScheme(data)
                "http", "https" -> handleHttpScheme(data)
                else -> handleFallbackScheme(data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startMainActivity(this)
        }
    }

    private fun handleDantotsuScheme(uri: Uri) {
        val host = uri.host?.lowercase().orEmpty()
        val pathSegments = uri.pathSegments
        val firstSegment = pathSegments.firstOrNull()?.lowercase().orEmpty()

        val action = if (host.isNotEmpty() && host != "open" && host != "media") {
            host
        } else if (firstSegment.isNotEmpty()) {
            firstSegment
        } else {
            uri.getQueryParameter("type")?.lowercase() ?: "anime"
        }

        val idStr = when {
            host.isNotEmpty() && host != "open" && host != "media" -> pathSegments.firstOrNull()
            pathSegments.size > 1 -> pathSegments[1]
            else -> uri.getQueryParameter("id")
        }

        val isMAL = uri.getBooleanQueryParameter("mal", false) || uri.getQueryParameter("source")?.equals("mal", ignoreCase = true) == true
        val continueMedia = uri.getBooleanQueryParameter("continue", false)

        when (action) {
            "anime", "watch" -> {
                val id = idStr?.toIntOrNull() ?: uri.getQueryParameter("id")?.toIntOrNull()
                if (id != null && id > 0) {
                    loadMedia = id
                    startMainActivity(
                        this,
                        bundleOf("mediaId" to id, "mal" to isMAL, "continue" to continueMedia, "mediaType" to "ANIME")
                    )
                } else {
                    startMainActivity(this)
                }
            }
            "manga", "read" -> {
                val id = idStr?.toIntOrNull() ?: uri.getQueryParameter("id")?.toIntOrNull()
                if (id != null && id > 0) {
                    loadMedia = id
                    startMainActivity(
                        this,
                        bundleOf("mediaId" to id, "mal" to isMAL, "continue" to continueMedia, "mediaType" to "MANGA")
                    )
                } else {
                    startMainActivity(this)
                }
            }
            "media", "open" -> {
                val id = idStr?.toIntOrNull() ?: uri.getQueryParameter("id")?.toIntOrNull()
                val type = (uri.getQueryParameter("type") ?: "ANIME").uppercase()
                if (id != null && id > 0) {
                    loadMedia = id
                    startMainActivity(
                        this,
                        bundleOf("mediaId" to id, "mal" to isMAL, "continue" to continueMedia, "mediaType" to type)
                    )
                } else {
                    startMainActivity(this)
                }
            }
            "user", "u", "profile" -> {
                val username = idStr ?: uri.getQueryParameter("username") ?: uri.getQueryParameter("name")
                if (!username.isNullOrEmpty()) {
                    startMainActivity(this, bundleOf("username" to username))
                } else {
                    startMainActivity(this)
                }
            }
            else -> {
                startMainActivity(this)
            }
        }
    }

    private fun handleHttpScheme(data: Uri) {
        val host = data.host?.lowercase().orEmpty()
        val segments = data.pathSegments
        val firstSegment = segments.firstOrNull()?.lowercase().orEmpty()

        if (firstSegment == "user" || firstSegment == "u") {
            val username = segments.getOrNull(1)
            startMainActivity(this, bundleOf("username" to username))
            return
        }

        var id: Int? = intent?.extras?.getInt("media", 0) ?: 0
        var isMAL = false
        var continueMedia = true
        if (id == 0) {
            continueMedia = false
            isMAL = host.contains("myanimelist.net")
            id = segments.getOrNull(1)?.toIntOrNull()
        } else {
            loadMedia = id
        }

        val mediaType = when (firstSegment) {
            "anime", "watch" -> "ANIME"
            "manga", "read" -> "MANGA"
            else -> firstSegment.uppercase()
        }

        startMainActivity(
            this,
            bundleOf("mediaId" to id, "mal" to isMAL, "continue" to continueMedia, "mediaType" to mediaType)
        )
    }

    private fun handleFallbackScheme(data: Uri) {
        val type = data.pathSegments?.getOrNull(0)
        if (type != "user") {
            val id = data.pathSegments?.getOrNull(1)?.toIntOrNull() ?: intent?.extras?.getInt("media", 0) ?: 0
            val isMAL = data.host != "anilist.co"
            val mediaType = type?.uppercase()
            startMainActivity(
                this,
                bundleOf("mediaId" to id, "mal" to isMAL, "continue" to false, "mediaType" to mediaType)
            )
        } else {
            val username = data.pathSegments?.getOrNull(1)
            startMainActivity(this, bundleOf("username" to username))
        }
    }
}
