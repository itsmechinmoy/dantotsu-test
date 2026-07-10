package ani.dantotsu.media.anime.mpv

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.serialization.json.Json

internal fun Uri.openContentFd(context: Context): String? {
    return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let {
        Utils.findRealPath(it)?.also { _ ->
            ParcelFileDescriptor.adoptFd(it).close()
        } ?: "fd://$it"
    }
}

internal fun Uri.resolveUri(context: Context): String? {
    val filepath = when (scheme) {
        "file" -> path
        "content" -> openContentFd(context)
        "data" -> "data://$schemeSpecificPart"
        in Utils.PROTOCOLS -> toString()
        else -> null
    }
    return filepath
}

internal fun Uri.getFileName(context: Context): String? {
    return context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex)
    }
}

inline fun <reified T> MPVNode.toObject(json: Json): T = json.decodeFromString<T>(toJson())
