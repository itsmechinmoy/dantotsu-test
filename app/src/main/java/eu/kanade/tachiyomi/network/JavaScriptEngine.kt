package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Util for evaluating JavaScript in sources.
 */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(private val context: Context) {

    /**
     * Evaluate arbitrary JavaScript code and get the result as a primitive type
     * (e.g., String, Int).
     *
     * @since extensions-lib 1.4
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var webView: WebView? = null
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                }
                val wrappedScript = "(function() { try { return ($script); } catch(e) { return 'ERROR: ' + e.message; } })()"
                webView.evaluateJavascript(wrappedScript) { result ->
                    webView.destroy()
                    if (result == null || result == "null") {
                        continuation.resume("" as T)
                    } else {
                        var cleaned = result
                        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2) {
                            cleaned = cleaned.substring(1, cleaned.length - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                                .replace("\\t", "\t")
                        }
                        if (cleaned.startsWith("ERROR: ")) {
                            continuation.resumeWithException(RuntimeException(cleaned))
                        } else {
                            continuation.resume(cleaned as T)
                        }
                    }
                }
            } catch (e: Throwable) {
                webView?.destroy()
                continuation.resumeWithException(e)
            }
        }
    }
}
