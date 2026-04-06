package eu.kanade.tachiyomi.extension.all.nhentai2

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        // "all" must be first — it has no language restriction
        NHentai(),
        NHentai(lang = "en", searchLang = "language:english"),
        NHentai(lang = "ja", searchLang = "language:japanese"),
        NHentai(lang = "zh", searchLang = "language:chinese"),
    )
}
