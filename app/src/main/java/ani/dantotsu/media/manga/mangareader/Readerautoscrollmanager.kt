package ani.dantotsu.media.manga.mangareader

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.dantotsu.settings.CurrentReaderSettings
import java.util.Timer
import java.util.TimerTask

class ReaderAutoScrollManager {

    var speedSeconds: Float = 3f

    private var timer: Timer? = null
    private var recycler: RecyclerView? = null
    private var pager: ViewPager2? = null
    private var layout: CurrentReaderSettings.Layouts = CurrentReaderSettings.Layouts.CONTINUOUS
    private var direction: CurrentReaderSettings.Directions = CurrentReaderSettings.Directions.TOP_TO_BOTTOM

    var isRunning: Boolean = false
        private set

    fun updateLayout(
        recyclerView: RecyclerView?,
        viewPager: ViewPager2?,
        currentLayout: CurrentReaderSettings.Layouts,
        currentDirection: CurrentReaderSettings.Directions,
    ) {
        val wasRunning = isRunning
        if (wasRunning) stop()
        recycler = recyclerView
        pager = viewPager
        layout = currentLayout
        direction = currentDirection
        if (wasRunning) start()
    }

    fun start() {
        if (isRunning) stop()
        isRunning = true
        timer = Timer()

        if (layout != CurrentReaderSettings.Layouts.PAGED) {
            val tickMs = 50L
            val pixelsPerSecond = 1800f / speedSeconds.coerceAtLeast(0.5f)
            val pixelsPerTick = (pixelsPerSecond * tickMs / 1000f).toInt().coerceAtLeast(1)
            val isReversed = direction == CurrentReaderSettings.Directions.BOTTOM_TO_TOP ||
                    direction == CurrentReaderSettings.Directions.RIGHT_TO_LEFT

            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    recycler?.post {
                        if (direction == CurrentReaderSettings.Directions.TOP_TO_BOTTOM ||
                            direction == CurrentReaderSettings.Directions.BOTTOM_TO_TOP
                        ) {
                            recycler?.scrollBy(0, if (isReversed) -pixelsPerTick else pixelsPerTick)
                        } else {
                            recycler?.scrollBy(if (isReversed) -pixelsPerTick else pixelsPerTick, 0)
                        }
                    }
                }
            }, tickMs, tickMs)
        } else {
            val intervalMs = (speedSeconds * 1000f).toLong().coerceAtLeast(500L)
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    pager?.post {
                        val next = (pager?.currentItem ?: 0) + 1
                        if (next < (pager?.adapter?.itemCount ?: 0)) {
                            pager?.currentItem = next
                        } else {
                            stop()
                        }
                    }
                }
            }, intervalMs, intervalMs)
        }
    }

    fun stop() {
        isRunning = false
        timer?.cancel()
        timer?.purge()
        timer = null
    }

    fun toggle(): Boolean {
        return if (isRunning) {
            stop()
            false
        } else {
            start()
            true
        }
    }

    fun destroy() {
        stop()
        recycler = null
        pager = null
    }
}
