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

fun updateProgress(media: Media, number: String, isLastChapter: Boolean = false) {
//    android.util.Log.wtf("UPDATE_CALLED", "✅✅✅ updateProgress CALLED for ${media.nameRomaji} ✅✅✅")
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
//    Log.d("ProgressDebug", "========== UPDATE PROGRESS START ==========")
//    Log.d("ProgressDebug", "Incognito: $incognito")
//    Log.d("ProgressDebug", "Media: ${media.nameRomaji} (${media.id})")
//    Log.d("ProgressDebug", "Is anime: ${media.anime != null}")
//    Log.d("ProgressDebug", "New progress: $number")

    if (!incognito) {
        if (Anilist.userid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val a = number.toFloatOrNull()?.toInt()
                if ((a ?: 0) > (media.userProgress ?: -1)) {
                    a?.let { progress ->
                        val previousProgress = media.userProgress ?: 0
                        val total = media.anime?.totalEpisodes ?: media.manga?.totalChapters
                        val status = when {
                            media.userStatus == "REPEATING" -> media.userStatus
                            total != null && progress >= total -> "COMPLETED"
                            isLastChapter -> "COMPLETED"
                            else -> "CURRENT"
                        }

                        // Update AniList
                        Anilist.mutation.editList(media.id, progress, status = status)

                        // Update MAL
                        MAL.query.editList(media.idMAL, media.anime != null, progress, null, status ?: "CURRENT")

//                        // ========== SIMKL DEBUG ==========
//                        Log.d("SimklDebug", "========== SIMKL SECTION START ==========")
//                        Log.d("SimklDebug", "Media anime check: ${media.anime != null}")
//                        Log.d("SimklDebug", "Simkl instance: ${Simkl.getInstance()}")
//                        Log.d("SimklDebug", "Is logged in: ${Simkl.getInstance().isLoggedIn()}")
//                        Log.d("SimklDebug", "Is enabled: ${Simkl.getInstance().isEnabled()}")
//                        Log.d("SimklDebug", "Access token: '${Simkl.getInstance().accessToken}'")
//                        Log.d("SimklDebug", "Token length: ${Simkl.getInstance().accessToken.length}")

                        var simklSuccess = false
                        if (media.anime != null && Simkl.getInstance().isLoggedIn()) {
//                            Log.d("SimklDebug", "✅ Passed anime != null check")
//                            Log.d("SimklDebug", "✅ Passed isLoggedIn check")

                            if (!Simkl.getInstance().isEnabled()) {
//                                Log.e("SimklDebug", "❌❌❌ SIMKL IS NOT ENABLED! ❌❌❌")
//                                Log.e("SimklDebug", "This is why sync is failing!")
                            }

                            try {
                                val simklStatus = when (media.userStatus?.uppercase()) {
                                    "CURRENT", "REPEATING" -> "watching"
                                    "PLANNING" -> "plantowatch"
                                    "COMPLETED" -> "completed"
                                    "PAUSED" -> "hold"
                                    "DROPPED" -> "dropped"
                                    else -> "watching"
                                }

//                                Log.d("SimklDebug", "Mapped status: ${media.userStatus} → $simklStatus")
//                                Log.d("SimklDebug", "Calling updateAnimeProgress:")
//                                Log.d("SimklDebug", "  - ID: ${media.id}")
//                                Log.d("SimklDebug", "  - Previous: $previousProgress")
//                                Log.d("SimklDebug", "  - New: $progress")
//                                Log.d("SimklDebug", "  - Status: $simklStatus")
//                                Log.d("SimklDebug", "  - Score: ${media.userScore}")

                                simklSuccess = Simkl.getInstance().updateAnimeProgress(
                                    anilistAnimeId = media.id,
                                    previousProgress = previousProgress,
                                    newProgress = progress,
                                    status = simklStatus,
                                    score = media.userScore
                                )

//                                Log.d("SimklDebug", "Update result: $simklSuccess")
//                                if (simklSuccess) {
//                                    Log.d("SimklDebug", "✅✅✅ SIMKL UPDATE SUCCESS ✅✅✅")
//                                } else {
//                                    Log.e("SimklDebug", "❌❌❌ SIMKL UPDATE FAILED ❌❌❌")
//                                }

                            } catch (e: Exception) {
//                                Log.e("SimklDebug", "❌❌❌ EXCEPTION IN SIMKL SYNC ❌❌❌", e)
//                                e.printStackTrace()
                            }
                        } else {
//                            Log.e("SimklDebug", "❌ Did not enter Simkl sync block!")
//                            if (media.anime == null) {
//                                Log.e("SimklDebug", "  Reason: media.anime is NULL (this is manga, not anime)")
//                            }
//                            if (!Simkl.getInstance().isLoggedIn()) {
//                                Log.e("SimklDebug", "  Reason: NOT LOGGED IN")
//                                Log.e("SimklDebug", "  Token: '${Simkl.getInstance().accessToken}'")
//                            }
                        }
//                        Log.d("SimklDebug", "========== SIMKL SECTION END ==========")

                        launch(Dispatchers.Main) {
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
//    Log.d("ProgressDebug", "========== UPDATE PROGRESS END ==========")
}