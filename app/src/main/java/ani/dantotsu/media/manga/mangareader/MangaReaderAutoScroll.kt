package ani.dantotsu.media.manga.mangareader

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.settings.CurrentReaderSettings

class MangaReaderAutoScroll {

    var speed: Float = 3f 
    var isRunning: Boolean = false
        private set

    private var recyclerView: RecyclerView? = null
    private var direction: CurrentReaderSettings.Directions = CurrentReaderSettings.Directions.TOP_TO_BOTTOM
    
    private val handler = Handler(Looper.getMainLooper())
    private var accumulatedScroll = 0f

    private val scrollRunnable = object : Runnable {
        override fun run() {
            val rv = recyclerView
            if (!isRunning || rv == null) return

            accumulatedScroll += speed 
            val pixelsToScroll = accumulatedScroll.toInt()
            
            if (pixelsToScroll != 0) {
                accumulatedScroll -= pixelsToScroll
                
                when (direction) {
                    CurrentReaderSettings.Directions.TOP_TO_BOTTOM -> rv.scrollBy(0, pixelsToScroll)
                    CurrentReaderSettings.Directions.BOTTOM_TO_TOP -> rv.scrollBy(0, -pixelsToScroll)
                    CurrentReaderSettings.Directions.LEFT_TO_RIGHT -> rv.scrollBy(pixelsToScroll, 0)
                    CurrentReaderSettings.Directions.RIGHT_TO_LEFT -> rv.scrollBy(-pixelsToScroll, 0)
                }
            }
            
            handler.postDelayed(this, 16L) 
        }
    }

    fun attach(rv: RecyclerView, dir: CurrentReaderSettings.Directions) {
        recyclerView = rv
        direction = dir
    }

    fun start() {
        if (isRunning) stop()
        if (recyclerView == null) return
        isRunning = true
        accumulatedScroll = 0f
        handler.post(scrollRunnable)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(scrollRunnable)
    }

    fun toggle(): Boolean {
        return if (isRunning) { stop(); false } else { start(); true }
    }

    fun destroy() {
        stop()
        recyclerView = null
    }
}
