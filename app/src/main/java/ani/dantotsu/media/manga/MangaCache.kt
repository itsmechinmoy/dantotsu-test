package ani.dantotsu.media.manga

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import ani.dantotsu.snackString
import ani.dantotsu.util.Logger
import ani.dantotsu.util.createDataSaver
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

data class ImageData(
    val page: Page,
    val source: HttpSource
) {
    suspend fun fetchAndProcessImage(
        page: Page,
        httpSource: HttpSource
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val originalUrl = page.imageUrl ?: return@withContext null
                val compressedUrl = createDataSaver().compress(originalUrl)

                val bitmap = fetchBitmap(page, httpSource, compressedUrl)
                    ?: if (compressedUrl != originalUrl) {
                        Logger.log("Data saver proxy failed for $compressedUrl, retrying original URL")
                        fetchBitmap(page, httpSource, originalUrl)
                    } else {
                        null
                    }

                if (bitmap == null) {
                    Logger.log("Failed to load image after retrying original URL")
                    snackString("An error occurred: unable to load image")
                }

                return@withContext bitmap
            } catch (e: Exception) {
                Logger.log("An error occurred: ${e.message}")
                snackString("An error occurred: ${e.message}")
                return@withContext null
            }
        }
    }

    private suspend fun fetchBitmap(
        page: Page,
        httpSource: HttpSource,
        imageUrl: String,
    ): Bitmap? {
        val originalUrl = page.imageUrl
        page.imageUrl = imageUrl

        return try {
            val response = httpSource.getImage(page)
            Logger.log("Response: ${response.code} - ${response.message}")

            response.use {
                it.body.byteStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }
        } catch (e: Exception) {
            Logger.log("Failed to load image $imageUrl: ${e.message}")
            null
        } finally {
            page.imageUrl = originalUrl
        }
    }
}

fun saveImage(
    bitmap: Bitmap,
    contentResolver: ContentResolver,
    filename: String,
    format: Bitmap.CompressFormat,
    quality: Int
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/${format.name.lowercase()}")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Dantotsu/Manga"
                )
            }

            val uri: Uri? =
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                contentResolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(format, quality, os)
                } ?: throw FileNotFoundException("Failed to open output stream for URI: $uri")
            }
        } else {
            val directory =
                File("${Environment.getExternalStorageDirectory()}${File.separator}Dantotsu${File.separator}Manga")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)

            // Check if the file already exists
            if (file.exists()) {
                println("File already exists: ${file.absolutePath}")
                return
            }

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(format, quality, outputStream)
            }
        }
    } catch (e: FileNotFoundException) {
        println("File not found: ${e.message}")
    } catch (e: Exception) {
        println("Exception while saving image: ${e.message}")
    }
}

class MangaCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024 / 2).toInt()
    private val cache = LruCache<String, ImageData>(maxMemory)

    @Synchronized
    fun put(key: String, imageDate: ImageData) {
        cache.put(key, imageDate)
    }

    @Synchronized
    fun get(key: String): ImageData? = cache.get(key)

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    fun size(): Int = cache.size()


}
