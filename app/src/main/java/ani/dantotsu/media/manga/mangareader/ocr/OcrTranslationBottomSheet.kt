package ani.dantotsu.media.manga.mangareader.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.databinding.DialogOcrTranslationBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

        val bitmap = currentBitmap
        if (bitmap == null || bitmap.isRecycled) {
            Toast.makeText(context, "No page image available for translation", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        lifecycleScope.launch {
            try {
                // 1. Recognize text using ML Kit on-device Japanese recognizer
                val detectedText = recognizeText(bitmap)

                if (detectedText.isBlank()) {
                    binding.ocrProgressBar.visibility = View.GONE
                    binding.ocrResultsContainer.visibility = View.VISIBLE
                    binding.ocrDetectedText.text = getString(R.string.no_text_detected)
                    binding.ocrTranslatedText.text = getString(R.string.no_text_detected)
                    return@launch
                }

                // 2. Translate text via Google Translate API
                val translatedText = translateText(detectedText)

                // 3. Update UI
                binding.ocrProgressBar.visibility = View.GONE
                binding.ocrResultsContainer.visibility = View.VISIBLE
                binding.ocrDetectedText.text = detectedText
                binding.ocrTranslatedText.text = translatedText

                binding.ocrCopyButton.setOnClickListener {
                    val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Manga Translation", translatedText)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }

                binding.ocrCloseButton.setOnClickListener {
                    dismiss()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Translation error: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    private suspend fun processWithRecognizer(
        recognizer: TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    private suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val isCopy = safeBitmap !== bitmap
        try {
            val image = InputImage.fromBitmap(safeBitmap, 0)

            // 1. Attempt Japanese text recognition first
            var text = try {
                val jpRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                processWithRecognizer(jpRecognizer, image)
            } catch (_: Exception) {
                ""
            }

            // 2. Fallback to default Latin recognizer if Japanese produces blank text or fails
            if (text.isBlank()) {
                text = try {
                    val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    processWithRecognizer(latinRecognizer, image)
                } catch (_: Exception) {
                    ""
                }
            }

            text
        } finally {
            if (isCopy) {
                safeBitmap.recycle()
            }
        }
    }

    private suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=")
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
            "Translation failed: ${e.localizedMessage ?: e.message}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        currentBitmap = null
    }

    companion object {
        private var currentBitmap: Bitmap? = null

        fun newInstance(bitmap: Bitmap): OcrTranslationBottomSheet {
            currentBitmap = bitmap
            return OcrTranslationBottomSheet()
        }
    }
}
