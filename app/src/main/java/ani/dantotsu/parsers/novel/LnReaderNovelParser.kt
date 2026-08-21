package ani.dantotsu.parsers.novel
import ani.dantotsu.FileUrl
import ani.dantotsu.parsers.Book
import ani.dantotsu.parsers.NovelParser
import ani.dantotsu.parsers.ShowResponse
import ani.dantotsu.util.Logger
import kotlinx.serialization.json.Json
import java.io.File

class LnReaderNovelParser(
    private val plugin: LnReaderInstalledPlugin
) : NovelParser() {

    override val name: String    get() = plugin.name
    override val hostUrl: String get() = plugin.site
    override val saveName: String get() = "lnreader_${plugin.id}"
    override val iconUrl: String get() = plugin.iconUrl

    override val volumeRegex = Regex(
        "vol\\.? (\\d+(\\.\\d+)?)|volume (\\d+(\\.\\d+)?)",
        RegexOption.IGNORE_CASE
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    override suspend fun search(query: String): List<ShowResponse> {
        return try {
            val raw = if (query.isNotBlank()) {
                try {
                    engineCall("searchNovels", """["${query.jsEscape()}", 1]""")
                } catch (e: Exception) {
                    Logger.log("LnReaderNovelParser[${plugin.id}] searchNovels failed, fallback to popular: ${e.message}")
                    engineCall("popularNovels", """[1, {}]""")
                }
            } else {
                engineCall("popularNovels", """[1, {"showLatestNovels": true}]""")
            }
            val items = json.decodeFromString<List<LnNovelItem>>(raw)
            items.map { item ->
                ShowResponse(
                    name     = item.name,
                    link     = item.path,
                    coverUrl = item.cover ?: ""
                )
            }
        } catch (e: Exception) {
            Logger.log("LnReaderNovelParser[${plugin.id}].search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?): Book {
        return try {
            val novelRaw = engineCall("parseNovel", """["${link.jsEscape()}"]""")
            val novel = json.decodeFromString<LnSourceNovel>(novelRaw)
            val chapters = mutableListOf<LnChapterItem>()
            if (!novel.chapters.isNullOrEmpty()) {
                chapters.addAll(novel.chapters)
            }
            val totalPages = novel.totalPages ?: 1
            if (totalPages > 1) {
                for (p in 2..totalPages) {
                    try {
                        val pageRaw = engineCall("parsePage", """["${link.jsEscape()}", "$p"]""")
                        val page = json.decodeFromString<LnSourcePage>(pageRaw)
                        if (!page.chapters.isNullOrEmpty()) {
                            chapters.addAll(page.chapters)
                        }
                    } catch (e: Exception) {
                        Logger.log("LnReaderNovelParser[${plugin.id}] parsePage $p failed: ${e.message}")
                    }
                }
            } else if (chapters.isEmpty()) {
                try {
                    val pageRaw = engineCall("parsePage", """["${link.jsEscape()}", "1"]""")
                    val page = json.decodeFromString<LnSourcePage>(pageRaw)
                    if (!page.chapters.isNullOrEmpty()) {
                        chapters.addAll(page.chapters)
                    }
                } catch (_: Exception) {}
            }

            val links = chapters.map { ch ->
                val headers = mutableMapOf("X-Chapter-Name" to ch.name)
                ch.releaseTime?.let { headers["X-Release-Time"] = it }
                ch.chapterNumber?.let { headers["X-Chapter-Number"] = it.toString() }
                FileUrl(url = ch.path, headers = headers)
            }

            Book(
                name        = novel.name,
                img         = FileUrl(novel.cover ?: ""),
                description = novel.summary,
                links       = links
            )
        } catch (e: Exception) {
            Logger.log("LnReaderNovelParser[${plugin.id}].loadBook error: ${e.message}")
            Book(name = "Error", img = FileUrl(""), description = e.message, links = emptyList())
        }
    }

    suspend fun loadChapterHtml(chapterPath: String): String {
        return try {
            val raw = engineCall("parseChapter", """["${chapterPath.jsEscape()}"]""")
            runCatching { json.decodeFromString<String>(raw) }.getOrDefault(raw)
        } catch (e: Exception) {
            Logger.log("LnReaderNovelParser[${plugin.id}].loadChapterHtml error: ${e.message}")
            "<html><body><p>Failed to load chapter: ${e.message}</p></body></html>"
        }
    }
    
    private suspend fun engineCall(method: String, argsJson: String): String {
        val jsCode = File(plugin.jsFilePath).readText()
        return LnReaderJsEngine.call(
            pluginJs  = jsCode,
            pluginId  = plugin.id,
            method    = method,
            argsJson  = argsJson,
        )
    }
    
    private fun String.jsEscape(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("`", "\\`")
}
