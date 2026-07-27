package ani.dantotsu.media.anime

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import ani.dantotsu.R

@OptIn(UnstableApi::class)
class AnimePlayerService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.video)
            .build()
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return activeSession
    }

    companion object {
        var activeSession: MediaSession? = null

        fun start(context: Context, session: MediaSession) {
            activeSession = session
            val intent = Intent(context, AnimePlayerService::class.java)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            activeSession = null
            val intent = Intent(context, AnimePlayerService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
