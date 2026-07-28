package ani.dantotsu.widgets.continue_widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import ani.dantotsu.MainActivity
import ani.dantotsu.R
import ani.dantotsu.util.BitmapUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContinueWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val PREFS_NAME = "ContinueWidgetPrefs"
        private const val KEY_ACTIVE_TITLE = "active_title"
        private const val KEY_ACTIVE_COVER = "active_cover"
        private const val KEY_ACTIVE_DETAIL = "active_detail"
        private const val KEY_ACTIVE_HEADER = "active_header"
        private const val KEY_IS_ACTIVE = "is_active"

        private const val KEY_LAST_WATCHED_TITLE = "last_watched_title"
        private const val KEY_LAST_WATCHED_COVER = "last_watched_cover"
        private const val KEY_LAST_WATCHED_DETAIL = "last_watched_detail"

        private const val KEY_LAST_READ_TITLE = "last_read_title"
        private const val KEY_LAST_READ_COVER = "last_read_cover"
        private const val KEY_LAST_READ_DETAIL = "last_read_detail"

        fun updatePlaybackState(
            context: Context,
            title: String?,
            coverUrl: String?,
            detail: String?,
            isExiting: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (!isExiting && !title.isNullOrEmpty()) {
                editor.putBoolean(KEY_IS_ACTIVE, true)
                editor.putString(KEY_ACTIVE_TITLE, title)
                editor.putString(KEY_ACTIVE_COVER, coverUrl)
                editor.putString(KEY_ACTIVE_DETAIL, detail ?: "Playing")
                editor.putString(KEY_ACTIVE_HEADER, "CURRENTLY WATCHING")

                // Update Last Watched
                editor.putString(KEY_LAST_WATCHED_TITLE, title)
                editor.putString(KEY_LAST_WATCHED_COVER, coverUrl)
                editor.putString(KEY_LAST_WATCHED_DETAIL, detail ?: "Resume Episode")
            } else {
                editor.putBoolean(KEY_IS_ACTIVE, false)
            }
            editor.apply()
            notifyWidgets(context)
        }

        fun updateReadingState(
            context: Context,
            title: String?,
            coverUrl: String?,
            detail: String?,
            isExiting: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (!isExiting && !title.isNullOrEmpty()) {
                editor.putBoolean(KEY_IS_ACTIVE, true)
                editor.putString(KEY_ACTIVE_TITLE, title)
                editor.putString(KEY_ACTIVE_COVER, coverUrl)
                editor.putString(KEY_ACTIVE_DETAIL, detail ?: "Reading")
                editor.putString(KEY_ACTIVE_HEADER, "CURRENTLY READING")

                // Update Last Read
                editor.putString(KEY_LAST_READ_TITLE, title)
                editor.putString(KEY_LAST_READ_COVER, coverUrl)
                editor.putString(KEY_LAST_READ_DETAIL, detail ?: "Resume Chapter")
            } else {
                editor.putBoolean(KEY_IS_ACTIVE, false)
            }
            editor.apply()
            notifyWidgets(context)
        }

        fun notifyWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ContinueWidget::class.java)
            )
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)

            val headerText: String
            val titleText: String
            val detailText: String
            val coverUrl: String?

            if (isActive) {
                headerText = prefs.getString(KEY_ACTIVE_HEADER, "CURRENTLY ACTIVE") ?: "CURRENTLY ACTIVE"
                titleText = prefs.getString(KEY_ACTIVE_TITLE, "Dantotsu") ?: "Dantotsu"
                detailText = prefs.getString(KEY_ACTIVE_DETAIL, "In Progress") ?: "In Progress"
                coverUrl = prefs.getString(KEY_ACTIVE_COVER, null)
            } else {
                val lastWatched = prefs.getString(KEY_LAST_WATCHED_TITLE, null)
                val lastRead = prefs.getString(KEY_LAST_READ_TITLE, null)

                if (!lastWatched.isNullOrEmpty()) {
                    headerText = "LAST WATCHED"
                    titleText = lastWatched
                    detailText = prefs.getString(KEY_LAST_WATCHED_DETAIL, "Tap to resume") ?: "Tap to resume"
                    coverUrl = prefs.getString(KEY_LAST_WATCHED_COVER, null)
                } else if (!lastRead.isNullOrEmpty()) {
                    headerText = "LAST READ"
                    titleText = lastRead
                    detailText = prefs.getString(KEY_LAST_READ_DETAIL, "Tap to resume") ?: "Tap to resume"
                    coverUrl = prefs.getString(KEY_LAST_READ_COVER, null)
                } else {
                    headerText = "DANTOTSU"
                    titleText = "Nothing active"
                    detailText = "Open Dantotsu to start"
                    coverUrl = null
                }
            }

            val gradientDrawable = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.linear_gradient_black,
                null
            ) as GradientDrawable
            gradientDrawable.colors = intArrayOf(Color.parseColor("#80000000"), Color.parseColor("#00000000"))
            gradientDrawable.cornerRadius = 0f
            val backgroundBitmap = gradientDrawable.toBitmap(720, 360)

            val views = RemoteViews(context.packageName, R.layout.widget_continue).apply {
                setImageViewBitmap(R.id.widget_background, backgroundBitmap)
                setTextViewText(R.id.widget_status_header, headerText)
                setTextViewText(R.id.widget_title, titleText)
                setTextViewText(R.id.widget_subtitle, detailText)

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                setOnClickPendingIntent(R.id.widgetRootContainer, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)

            if (!coverUrl.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = BitmapUtil.downloadImageAsBitmap(coverUrl)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            views.setImageViewBitmap(R.id.widget_cover, bitmap)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                }
            }
        }
    }
}
