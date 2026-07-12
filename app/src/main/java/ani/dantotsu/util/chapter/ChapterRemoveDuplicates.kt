package ani.dantotsu.util.chapter

import ani.dantotsu.media.manga.MangaChapter

/**
 * Returns a copy of the list with duplicate chapters removed.
 */
fun List<MangaChapter>.removeDuplicates(currentChapter: MangaChapter): List<MangaChapter> {
    return groupBy { it.number }
        .map { (_, chapters) ->
            chapters.find { it.link == currentChapter.link }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }
}
