package ani.dantotsu.media.manga.mangareader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.media.manga.saveImage
import ani.dantotsu.snackString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LongPressPageActionsDialog : BottomSheetDialogFragment() {

    private var pageUrl: String = ""
    private var pageHeaders: HashMap<String, String> = hashMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageUrl = arguments?.getString(ARG_URL) ?: ""
        @Suppress("UNCHECKED_CAST")
        pageHeaders = (arguments?.getSerializable(ARG_HEADERS) as? HashMap<String, String>)
            ?: hashMapOf()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 48)
        }

        val title = TextView(ctx).apply {
            text = ctx.getString(R.string.page_actions_title)
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        fun row(label: String, action: () -> Unit): View =
            TextView(ctx).apply {
                text = label
                textSize = 15f
                setPadding(0, 20, 0, 20)
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }

        root.addView(row(ctx.getString(R.string.save_page))     { savePage() })
        root.addView(row(ctx.getString(R.string.share_page))    { sharePage() })
        root.addView(row(ctx.getString(R.string.copy_page_url)) { copyUrl() })

        return root
    }
    
    private fun fetchBytes(): ByteArray? = try {
        val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            pageHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            connectTimeout = 15_000
            readTimeout    = 30_000
        }
        conn.inputStream.use { it.readBytes() }.also { conn.disconnect() }
    } catch (_: Exception) { null }
    
    private fun savePage() {
        dismiss()
        val ctx = context ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = fetchBytes() ?: error("Could not fetch image")
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("Could not decode image")
                val filename = "page_${System.currentTimeMillis()}.jpg"
                saveImage(
                    bitmap,
                    ctx.contentResolver,
                    filename,
                    android.graphics.Bitmap.CompressFormat.JPEG,
                    95
                )
                withContext(Dispatchers.Main) {
                    snackString(ctx.getString(R.string.saving_page))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackString(ctx.getString(R.string.error_saving_page, e.localizedMessage))
                }
            }
        }
    }

    private fun sharePage() {
        dismiss()
        val ctx = context ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = fetchBytes() ?: error("Could not fetch image")
                val file = File(ctx.cacheDir, "share_page_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { it.write(bytes) }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(intent, ctx.getString(R.string.share_page)))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackString(ctx.getString(R.string.error_sharing_page, e.localizedMessage))
                }
            }
        }
    }

    private fun copyUrl() {
        dismiss()
        val ctx = context ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("page_url", pageUrl))
        snackString(ctx.getString(R.string.copied_page_url))
    }

    companion object {
        private const val ARG_URL     = "page_url"
        private const val ARG_HEADERS = "page_headers"

        fun newInstance(
            url: String,
            headers: HashMap<String, String> = hashMapOf()
        ) = LongPressPageActionsDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, url)
                putSerializable(ARG_HEADERS, headers)
            }
        }
    }
}
