package ani.dantotsu.addons.download

import android.content.Context
import android.net.Uri
import ani.dantotsu.util.Logger
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.SessionState
import com.google.gson.Gson
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NativeVideoDownloader(private val context: Context) : DownloadAddonApiV2 {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextSessionId = AtomicLong(1000)
    private val activeSessions = ConcurrentHashMap<Long, DownloadSession>()
    private val uriMap = ConcurrentHashMap<String, Uri>()

    // aria2 process variables
    private var aria2Process: Process? = null
    private var rpcPort: Int = 6800
    private var aria2Secret: String = ""
    private val aria2Mutex = Mutex()

    sealed class DownloadSession {
        abstract val sessionId: Long
        abstract val downloadPath: String
        abstract fun cancel()
        abstract fun getStatus(): String
        abstract fun getStackTrace(): String?
        abstract fun hadError(): Boolean

        data class FFMpegSession(
            override val sessionId: Long,
            override val downloadPath: String
        ) : DownloadSession() {
            override fun cancel() {
                FFmpegKit.cancel(sessionId)
            }

            override fun getStatus(): String {
                FFmpegKitConfig.getFFmpegSessions().forEach {
                    if (it.sessionId == sessionId) {
                        return when (it.state) {
                            SessionState.COMPLETED -> "COMPLETED"
                            SessionState.FAILED -> "FAILED"
                            SessionState.RUNNING -> "RUNNING"
                            else -> "UNKNOWN"
                        }
                    }
                }
                return "UNKNOWN"
            }

            override fun getStackTrace(): String? {
                FFmpegKitConfig.getFFmpegSessions().forEach {
                    if (it.sessionId == sessionId) {
                        return it.failStackTrace
                    }
                }
                return null
            }

            override fun hadError(): Boolean {
                FFmpegKitConfig.getFFmpegSessions().forEach {
                    if (it.sessionId == sessionId) {
                        return it.returnCode.isValueError
                    }
                }
                return false
            }
        }

        class Aria2Session(
            override val sessionId: Long,
            override val downloadPath: String,
            val gid: String,
            val tempFile: File,
            val targetUri: Uri?,
            val context: Context,
            val job: Job
        ) : DownloadSession() {
            @Volatile
            var currentStatus: String = "RUNNING"
            @Volatile
            var failReason: String? = null
            @Volatile
            var hasError: Boolean = false

            @Volatile
            var downloadedBytes: Long = 0L
            @Volatile
            var totalBytes: Long = 0L

            override fun cancel() {
                currentStatus = "FAILED"
                failReason = "Cancelled by user"
                job.cancel()
            }

            override fun getStatus(): String = currentStatus
            override fun getStackTrace(): String? = failReason
            override fun hadError(): Boolean = hasError
        }

        class HlsSession(
            override val sessionId: Long,
            override val downloadPath: String,
            val tempFile: File,
            val targetUri: Uri?,
            val context: Context,
            val job: Job
        ) : DownloadSession() {
            @Volatile
            var currentStatus: String = "RUNNING"
            @Volatile
            var failReason: String? = null
            @Volatile
            var hasError: Boolean = false

            val downloadedBytes = java.util.concurrent.atomic.AtomicLong(0L)
            @Volatile
            var totalBytes: Long = 0L

            override fun cancel() {
                currentStatus = "FAILED"
                failReason = "Cancelled by user"
                job.cancel()
            }

            override fun getStatus(): String = currentStatus
            override fun getStackTrace(): String? = failReason
            override fun hadError(): Boolean = hasError
        }
    }

    override fun cancelDownload(sessionId: Long) {
        activeSessions[sessionId]?.cancel()
    }

    override fun getDownloadedBytes(sessionId: Long): Long {
        val session = activeSessions[sessionId] ?: return -1L
        return when (session) {
            is DownloadSession.Aria2Session -> session.downloadedBytes
            is DownloadSession.HlsSession -> session.downloadedBytes.get()
            else -> -1L
        }
    }

    override fun getEstimatedTotalBytes(sessionId: Long): Long {
        val session = activeSessions[sessionId] ?: return -1L
        return when (session) {
            is DownloadSession.Aria2Session -> session.totalBytes
            is DownloadSession.HlsSession -> session.totalBytes
            else -> -1L
        }
    }

    override fun setDownloadPath(context: Context, uri: Uri): String {
        val path = FFmpegKitConfig.getSafParameterForWrite(context, uri)
        uriMap[path] = uri
        return path
    }

    override fun getReadPath(context: Context, uri: Uri): String {
        return FFmpegKitConfig.getSafParameter(context, uri, "r")
    }

    override suspend fun executeFFProbe(
        videoUrl: String,
        headers: Map<String, String>,
        logCallback: (String) -> Unit
    ) {
        val headersStr = buildHeadersString(headers)
        val request = "${headersStr}-i \"$videoUrl\" -show_entries format=duration -of csv=\"p=0\""
        FFprobeKit.executeAsync(
            request,
            { session ->
                val output = session.output ?: session.allLogsAsString
                if (output != null) {
                    val duration = output.lines().map { it.trim() }.firstOrNull { it.toDoubleOrNull() != null }
                    if (duration != null) {
                        logCallback(duration)
                    }
                }
            }, { log ->
                val msg = log.message
                if (msg != null && msg.trim().toDoubleOrNull() != null) {
                    logCallback(msg.trim())
                }
            })
    }

    override suspend fun executeFFMpeg(
        videoUrl: String,
        downloadPath: String,
        headers: Map<String, String>,
        subtitleUrls: List<Pair<String, String>>,
        audioUrls: List<Pair<String, String>>,
        statCallback: (Double) -> Unit
    ): Long {
        val sessionId = nextSessionId.incrementAndGet()
        val isHls = videoUrl.contains(".m3u8", ignoreCase = true) || videoUrl.contains("m3u8", ignoreCase = true)
        val isDash = videoUrl.contains(".mpd", ignoreCase = true) || videoUrl.contains("mpd", ignoreCase = true)

        if (isHls && subtitleUrls.isEmpty() && audioUrls.isEmpty()) {
            // HLS stream with no extra subtitle/audio tracks -> download using parallel HLS engine
            val tempFile = File(context.cacheDir, "hls_dl_${sessionId}.ts")
            val targetUri = uriMap[downloadPath]
            
            val job = scope.launch {
                try {
                    Logger.log("Built-in: Starting parallel HLS download for session $sessionId")
                    activeSessions[sessionId]?.let { (it as? DownloadSession.HlsSession)?.currentStatus = "RUNNING" }
                    
                    // Fetch total length if possible to drive statistics callback
                    var totalLength = 0.0
                    executeFFProbe(videoUrl, headers) { durationStr ->
                        durationStr.toDoubleOrNull()?.let { totalLength = it }
                    }

                    runParallelHlsDownload(videoUrl, headers, tempFile, sessionId) { progressPercent ->
                        val duration = if (totalLength > 0.0) totalLength else 100.0
                        statCallback(progressPercent.toDouble() * duration * 10.0)
                    }

                    // Copy completed temp file to SAF path
                    if (targetUri != null) {
                        copyFileToUri(tempFile, targetUri)
                    } else {
                        tempFile.copyTo(File(downloadPath), overwrite = true)
                    }
                    
                    val session = activeSessions[sessionId] as? DownloadSession.HlsSession
                    session?.currentStatus = "COMPLETED"
                    Logger.log("Built-in: Parallel HLS download completed for session $sessionId")
                } catch (e: Exception) {
                    val session = activeSessions[sessionId] as? DownloadSession.HlsSession
                    session?.currentStatus = "FAILED"
                    session?.hasError = true
                    session?.failReason = e.message
                    Logger.log("Built-in: Parallel HLS download failed: ${e.message}")
                    e.printStackTrace()
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            activeSessions[sessionId] = DownloadSession.HlsSession(
                sessionId = sessionId,
                downloadPath = downloadPath,
                tempFile = tempFile,
                targetUri = targetUri,
                context = context,
                job = job
            )
            return sessionId

        } else if (!isHls && !isDash && subtitleUrls.isEmpty() && audioUrls.isEmpty()) {
            // Progressive HTTP/HTTPS URL -> Download using multi-connection aria2 subprocess
            val tempFile = File(context.cacheDir, "aria_dl_${sessionId}.bin")
            val targetUri = uriMap[downloadPath]

            val job = scope.launch {
                try {
                    Logger.log("Built-in: Starting aria2 download for session $sessionId")
                    ensureAria2Running()
                    
                    val gid = callAria2AddUri(videoUrl, headers, tempFile)
                        ?: throw IOException("Failed to add URI to aria2")

                    val session = activeSessions[sessionId] as? DownloadSession.Aria2Session
                    session?.currentStatus = "RUNNING"

                    var totalLength = 0.0
                    executeFFProbe(videoUrl, headers) { durationStr ->
                        durationStr.toDoubleOrNull()?.let { totalLength = it }
                    }

                    // Poll status
                    while (isActive) {
                        val statusMap = callAria2TellStatus(gid)
                        if (statusMap == null) {
                            delay(1000)
                            continue
                        }
                        val status = statusMap["status"] as? String ?: "active"
                        val totalBytes = (statusMap["totalLength"] as? String)?.toLongOrNull() ?: 0L
                        val completedBytes = (statusMap["completedLength"] as? String)?.toLongOrNull() ?: 0L
                        val errorCode = statusMap["errorCode"] as? String ?: "0"

                        if (status == "complete") {
                            break
                        } else if (status == "error" || errorCode != "0") {
                            throw IOException("aria2 error code: $errorCode")
                        } else if (status == "removed") {
                            throw IOException("aria2 download removed")
                        }

                        val activeSession = activeSessions[sessionId] as? DownloadSession.Aria2Session
                        if (activeSession != null) {
                            activeSession.downloadedBytes = completedBytes
                            activeSession.totalBytes = totalBytes
                        }

                        if (totalBytes > 0L) {
                            val percent = (completedBytes * 100 / totalBytes).toInt()
                            val duration = if (totalLength > 0.0) totalLength else 100.0
                            statCallback(percent.toDouble() * duration * 10.0)
                        }
                        delay(1000)
                    }

                    // Copy completed temp file to SAF path
                    if (targetUri != null) {
                        copyFileToUri(tempFile, targetUri)
                    } else {
                        tempFile.copyTo(File(downloadPath), overwrite = true)
                    }

                    val activeSession = activeSessions[sessionId] as? DownloadSession.Aria2Session
                    activeSession?.currentStatus = "COMPLETED"
                    Logger.log("Built-in: aria2 download completed for session $sessionId")
                } catch (e: Exception) {
                    val activeSession = activeSessions[sessionId] as? DownloadSession.Aria2Session
                    activeSession?.currentStatus = "FAILED"
                    activeSession?.hasError = true
                    activeSession?.failReason = e.message
                    Logger.log("Built-in: aria2 download failed: ${e.message}")
                    e.printStackTrace()
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            activeSessions[sessionId] = DownloadSession.Aria2Session(
                sessionId = sessionId,
                downloadPath = downloadPath,
                gid = "",
                tempFile = tempFile,
                targetUri = targetUri,
                context = context,
                job = job
            )
            return sessionId

        } else {
            // Complex stream or has separate tracks/manifests -> fall back to embedded FFmpeg
            Logger.log("Built-in: Falling back to embedded FFmpeg downloader for session $sessionId")
            val command = StringBuilder()
            val headersStr = buildHeadersString(headers)
            command.append("${headersStr}-i \"$videoUrl\" ")

            for (sub in subtitleUrls) {
                command.append("${headersStr}-i \"${sub.first}\" ")
            }
            for (audio in audioUrls) {
                command.append("${headersStr}-i \"${audio.first}\" ")
            }

            val totalInputs = 1 + subtitleUrls.size + audioUrls.size
            if (totalInputs > 1) {
                for (i in 0 until totalInputs) {
                    command.append("-map $i ")
                }
            }
            command.append("-c copy ")
            if (subtitleUrls.isNotEmpty()) {
                command.append("-c:s srt ")
            }
            for ((index, sub) in subtitleUrls.withIndex()) {
                command.append("-metadata:s:s:$index language=\"${sub.second}\" ")
            }
            for ((index, audio) in audioUrls.withIndex()) {
                command.append("-metadata:s:a:${index + 1} language=\"${audio.second}\" ")
            }
            command.append("$downloadPath ")

            val exec = FFmpegKit.executeAsync(command.toString(),
                { session ->
                    Logger.log("Built-in FFmpeg session exited: state=${session.state} rc=${session.returnCode}")
                }, {
                    // console logs
                }) {
                statCallback(it.time)
            }
            val rawId = exec.sessionId
            activeSessions[rawId] = DownloadSession.FFMpegSession(rawId, downloadPath)
            return rawId
        }
    }

    override suspend fun customFFMpeg(
        command: String,
        videoUrls: List<String>,
        logCallback: (String) -> Unit
    ): Long {
        val actualCommand = if (command == "1" && videoUrls.size >= 2) {
            "-i ${videoUrls[0]} -c copy ${videoUrls[1]}"
        } else {
            var cmd = command
            videoUrls.forEachIndexed { index, url ->
                cmd = cmd.replace("{$index}", url)
            }
            cmd
        }
        val exec = FFmpegKit.executeAsync(actualCommand,
            { session ->
                Logger.log("Built-in Custom FFmpeg exited: ${session.state} rc=${session.returnCode}")
            }, {
                logCallback(it.message)
            }) {
            // stats
        }
        val rawId = exec.sessionId
        activeSessions[rawId] = DownloadSession.FFMpegSession(rawId, "")
        return rawId
    }

    override suspend fun customFFProbe(
        command: String,
        videoUrls: List<String>,
        logCallback: (String) -> Unit
    ) {
        var cmd = command
        videoUrls.forEachIndexed { index, url ->
            cmd = cmd.replace("{$index}", url)
        }
        FFprobeKit.executeAsync(cmd,
            {
                // logs
            }, {
                logCallback(it.message)
            })
    }

    override fun getState(sessionId: Long): String {
        return activeSessions[sessionId]?.getStatus() ?: "UNKNOWN"
    }

    override fun getStackTrace(sessionId: Long): String? {
        return activeSessions[sessionId]?.getStackTrace()
    }

    override fun hadError(sessionId: Long): Boolean {
        return activeSessions[sessionId]?.hadError() ?: false
    }

    // ==========================================
    // PARALLEL HLS SEGMENT DOWNLOADER (AniZen style)
    // ==========================================
    private suspend fun runParallelHlsDownload(
        playlistUrl: String,
        headers: Map<String, String>,
        tempFile: File,
        sessionId: Long,
        progressCallback: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = Injekt.get<NetworkHelper>().downloadClient
        val okHeaders = headers.toHeaders()
        
        var currentUrl = playlistUrl
        var lines: List<String>

        // Resolve Master Playlist to picked variant
        while (true) {
            val req = Request.Builder().url(currentUrl).headers(okHeaders).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Failed HLS resolution: ${res.code}")
                lines = res.body?.string()?.lines() ?: emptyList()
            }
            val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
            if (isMaster) {
                val baseUrl = currentUrl.substringBeforeLast("/") + "/"
                val subUrl = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?: throw IOException("Variant HLS playlist empty")
                currentUrl = if (subUrl.startsWith("http")) subUrl else baseUrl + subUrl
                continue
            }
            break
        }

        val baseUrl = currentUrl.substringBeforeLast("/") + "/"
        val segments = mutableListOf<String>()
        var encryptionKeyUrl: String? = null
        var mediaSequence = 0

        for (line in lines) {
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = line.substringAfter(":").trim().toIntOrNull() ?: 0
            } else if (line.startsWith("#EXT-X-KEY:METHOD=AES-128")) {
                val match = Regex("URI=\"([^\"]+)\"").find(line)
                encryptionKeyUrl = match?.groupValues?.get(1)
                if (encryptionKeyUrl != null && !encryptionKeyUrl.startsWith("http")) {
                    encryptionKeyUrl = baseUrl + encryptionKeyUrl
                }
            } else if (!line.startsWith("#") && line.isNotBlank()) {
                segments.add(if (line.startsWith("http")) line else baseUrl + line)
            }
        }

        if (segments.isEmpty()) throw IOException("HLS segments list is empty")

        var secretKey: SecretKeySpec? = null
        if (encryptionKeyUrl != null) {
            val req = Request.Builder().url(encryptionKeyUrl).headers(okHeaders).build()
            client.newCall(req).execute().use { res ->
                val keyBytes = res.body?.bytes() ?: throw IOException("AES key empty")
                secretKey = SecretKeySpec(keyBytes, "AES")
            }
        }

        val segmentQueue = segments.mapIndexed { index, url -> index to url }.toMutableList()
        val downloadedCount = java.util.concurrent.atomic.LongAdder()
        
        val host = Uri.parse(playlistUrl).host ?: ""
        val threadCount = calculateDynamicConcurrency(host)
        val segmentFolder = File(context.cacheDir, "hls_parts_${System.currentTimeMillis()}")
        segmentFolder.mkdirs()

        try {
            coroutineScope {
                repeat(threadCount) {
                    launch {
                        while (isActive) {
                            val seg = synchronized(segmentQueue) {
                                if (segmentQueue.isNotEmpty()) segmentQueue.removeAt(0) else null
                            } ?: break
                            val partFile = File(segmentFolder, "seg_${seg.first}.part")
                            
                            var success = false
                            var attempts = 0
                            while (!success && attempts < 5) {
                                attempts++
                                try {
                                    val req = Request.Builder().url(seg.second).headers(okHeaders).build()
                                    client.newCall(req).execute().use { res ->
                                        if (!res.isSuccessful) throw IOException("Res code: ${res.code}")
                                        var data = res.body?.bytes() ?: throw IOException("Empty body")
                                        
                                        if (secretKey != null) {
                                            val seqNum = mediaSequence + seg.first
                                            val ivBytes = ByteBuffer.allocate(16).putLong(8, seqNum.toLong()).array()
                                            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                            cipher.init(Cipher.DECRYPT_MODE, secretKey!!, IvParameterSpec(ivBytes))
                                            data = cipher.doFinal(data)
                                        }

                                        FileOutputStream(partFile).use { it.write(data) }
                                        val dataSize = data.size.toLong()
                                        downloadedCount.increment()
                                        val hlsSession = activeSessions[sessionId] as? DownloadSession.HlsSession
                                        if (hlsSession != null) {
                                            hlsSession.downloadedBytes.addAndGet(dataSize)
                                            val count = downloadedCount.sum().toDouble()
                                            if (count > 0) {
                                                hlsSession.totalBytes = (hlsSession.downloadedBytes.get() * segments.size / count).toLong()
                                            }
                                        }
                                        val percent = (downloadedCount.sum().toDouble() * 100 / segments.size).toInt()
                                        progressCallback(percent)
                                        success = true
                                    }
                                } catch (e: Exception) {
                                    if (attempts >= 5) throw e
                                    delay(500)
                                }
                            }
                        }
                    }
                }
            }

            // Merge segment files
            FileOutputStream(tempFile).use { outStream ->
                val outChannel = outStream.channel
                for (i in 0 until segments.size) {
                    val partFile = File(segmentFolder, "seg_${i}.part")
                    if (partFile.exists()) {
                        java.io.FileInputStream(partFile).use { inStream ->
                            val inChannel = inStream.channel
                            inChannel.transferTo(0, inChannel.size(), outChannel)
                        }
                    }
                }
            }
        } finally {
            segmentFolder.deleteRecursively()
        }
    }

    private fun calculateDynamicConcurrency(host: String): Int {
        if (host.contains("animepahe") || host.contains("sibnet") || host.contains("video.sibnet")) return 1
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        return if (activityManager?.isLowRamDevice == true) 4 else 16
    }

    // ==========================================
    // aria2 SUBPROCESS MANAGEMENT
    // ==========================================
    private suspend fun ensureAria2Running() = aria2Mutex.withLock {
        if (aria2Process != null) return
        rpcPort = findFreePort(6800)
        aria2Secret = UUID.randomUUID().toString()
        val binaryPath = "${context.applicationInfo.nativeLibraryDir}/libaria2c.so"

        val procBuilder = ProcessBuilder(
            binaryPath,
            "--enable-rpc=true",
            "--rpc-listen-port=$rpcPort",
            "--rpc-listen-all=false",
            "--rpc-secret=$aria2Secret",
            "--daemon=false",
            "--max-connection-per-server=16",
            "--split=16",
            "--check-certificate=false",
            "--rpc-max-request-size=10M"
        )
        procBuilder.redirectErrorStream(true)
        val proc = procBuilder.start()
        aria2Process = proc

        // Read outputs to prevent blocking
        Thread {
            try {
                proc.inputStream.bufferedReader().use { reader ->
                    while (reader.readLine() != null) {
                        // ignore/discard logs
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }.start()

        delay(500)
    }

    private suspend fun callAria2AddUri(
        videoUrl: String,
        headers: Map<String, String>,
        tempFile: File
    ): String? {
        val uris = listOf(videoUrl)
        val options = mutableMapOf<String, Any>(
            "dir" to tempFile.parentFile!!.absolutePath,
            "out" to tempFile.name
        )
        if (headers.isNotEmpty()) {
            options["header"] = headers.map { "${it.key}: ${it.value}" }
        }
        val params = listOf("token:$aria2Secret", uris, options)
        val resultJson = callAria2Rpc("aria2.addUri", params) ?: return null
        return try {
            JsonParser.parseString(resultJson).asString
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun callAria2TellStatus(gid: String): Map<String, Any>? {
        val params = listOf("token:$aria2Secret", gid, listOf("status", "totalLength", "completedLength", "downloadSpeed", "errorCode"))
        val resultJson = callAria2Rpc("aria2.tellStatus", params) ?: return null
        return try {
            val obj = JsonParser.parseString(resultJson).asJsonObject
            val map = mutableMapOf<String, Any>()
            obj.entrySet().forEach { (k, v) ->
                map[k] = v.asString
            }
            map
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun callAria2Rpc(method: String, params: List<Any>): String? = withContext(Dispatchers.IO) {
        val url = "http://localhost:$rpcPort/jsonrpc"
        val requestMap = mapOf(
            "jsonrpc" to "2.0",
            "id" to "dantotsu",
            "method" to method,
            "params" to params
        )
        val requestBody = okhttp3.RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            Gson().toJson(requestMap)
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        try {
            val client = Injekt.get<NetworkHelper>().client
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val jsonObject = JsonParser.parseString(responseBody).asJsonObject
                    if (jsonObject.has("result")) {
                        return@withContext jsonObject.get("result").toString()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log("Built-in: RPC call failed: ${e.message}")
        }
        return@withContext null
    }

    // ==========================================
    // SAF UTILS & GENERAL HELPERS
    // ==========================================
    private fun copyFileToUri(source: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
            source.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IOException("Could not open output stream for SAF URI: $targetUri")
    }

    private fun findFreePort(startPort: Int): Int {
        var port = startPort
        while (port < 65535) {
            try {
                ServerSocket(port).use {
                    return port
                }
            } catch (e: IOException) {
                port++
            }
        }
        return startPort
    }

    private fun buildHeadersString(headers: Map<String, String>): String {
        if (headers.isEmpty()) return ""
        val sb = StringBuilder("-headers \"")
        for ((key, value) in headers) {
            sb.append("$key: $value\r\n")
        }
        sb.append("\" ")
        return sb.toString()
    }
}
