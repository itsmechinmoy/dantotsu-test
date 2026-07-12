package ani.dantotsu.media.manga.mangareader.ocr

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.FileUrl
import ani.dantotsu.databinding.DialogOcrTranslationBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OcrTranslationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogOcrTranslationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOcrTranslationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        val fileUrl = arguments?.getSerializable("image_url") as? FileUrl
        if (fileUrl == null || fileUrl.url.isEmpty()) {
            Toast.makeText(context, "No page image found", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        lifecycleScope.launch {
            try {
                // 1. Fetch the bitmap from cache/Glide
                val bitmap = loadBitmap(fileUrl)
                if (bitmap == null) {
                    Toast.makeText(context, "Failed to load page bitmap", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@launch
                }

                // 2. Perform OCR
                val ocrResult = GlensOcrEngine().recognizePage(bitmap)
                val detectedText = ocrResult.text

                if (detectedText.isEmpty()) {
                    binding.ocrProgressBar.visibility = View.GONE
                    binding.ocrResultsContainer.visibility = View.VISIBLE
                    binding.ocrDetectedText.text = "(No text detected on page)"
                    binding.ocrTranslatedText.text = "(No text to translate)"
                    return@launch
                }

                // 3. Perform Translation
                val translatedText = translateText(detectedText)

                // 4. Update UI
                binding.ocrProgressBar.visibility = View.GONE
                binding.ocrResultsContainer.visibility = View.VISIBLE
                binding.ocrDetectedText.text = detectedText
                binding.ocrTranslatedText.text = translatedText

                // Setup Action Buttons
                binding.ocrCopyButton.setOnClickListener {
                    val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Manga Translation", translatedText)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }

                binding.ocrCloseButton.setOnClickListener {
                    dismiss()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Translation failed: ${e.message}", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    private suspend fun loadBitmap(fileUrl: FileUrl): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Glide.with(this@OcrTranslationBottomSheet)
                .asBitmap()
                .let {
                    if (fileUrl.url.startsWith("file://") || fileUrl.url.startsWith("content://")) {
                        it.load(fileUrl.url)
                    } else {
                        it.load(GlideUrl(fileUrl.url) { fileUrl.headers })
                    }
                }
                .submit()
                .get()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=$encodedText")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val firstArray = jsonArray.getJSONArray(0)
            val result = StringBuilder()
            for (i in 0 until firstArray.length()) {
                result.append(firstArray.getJSONArray(i).getString(0))
            }
            result.toString()
        } catch (e: Exception) {
            "Translation failed: ${e.message}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(fileUrl: FileUrl): OcrTranslationBottomSheet {
            return OcrTranslationBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable("image_url", fileUrl)
                }
            }
        }
    }
}
