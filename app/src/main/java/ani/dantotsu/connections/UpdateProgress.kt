package ani.dantotsu.connections

import android.util.Log
import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.connections.simkl.Simkl
import ani.dantotsu.currContext
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

fun updateProgress(media: Media, number: String, isLastChapter: Boolean = false, score: Int? = null) {
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
    if (!incognito) {
        if (Anilist.userid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val a = number.toFloatOrNull()?.toInt()
                if ((a ?: 0) > (media.userProgress ?: -1)) {
                    a?.let { progress ->
                        score?.let { media.userScore = it }
                        val previousProgress = media.userProgress ?: 0
                        val total = media.anime?.totalEpisodes ?: media.manga?.totalChapters
                        val status = when {
                            media.userStatus == "REPEATING" -> media.userStatus
                            total != null && progress >= total -> "COMPLETED"
                            isLastChapter -> "COMPLETED"
                            else -> "CURRENT"
                        }

                        val anilistJob = async {
                            Anilist.mutation.editList(media.id, progress, status = status)
                        }

                        val malJob = async {
                            MAL.query.editList(media.idMAL, media.anime != null, progress, null, status ?: "CURRENT")
                        }

                        val simklJob = async {
                            if (media.anime != null && Simkl.getInstance().isLoggedIn()) {
                                try {
                                    Simkl.getInstance().updateAnimeProgress(
                                        anilistAnimeId = media.id,
                                        previousProgress = previousProgress,
                                        newProgress = progress,
                                        status = media.userStatus ?: "CURRENT",
                                        score = media.userScore
                                    )
                                } catch (e: Exception) {
                                    false
                                }
                            } else false
                        }

                        anilistJob.await()
                        malJob.await()
                        simklJob.await()

                        withContext(Dispatchers.Main) {
                            val baseMsg = currContext()?.getString(R.string.setting_progress, progress)
                            toast(baseMsg)
                        }
                    }
                }
                media.userProgress = a
                Refresh.all()
            }
        } else {
            toast(currContext()?.getString(R.string.login_anilist_account))
        }
    } else {
        toast("Sneaky sneaky :3")
    }
}