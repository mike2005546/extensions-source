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
        NHentai(lang = "ko", searchLang = "language:korean"),
        NHentai(lang = "es", searchLang = "language:spanish"),
        NHentai(lang = "fr", searchLang = "language:french"),
        NHentai(lang = "de", searchLang = "language:german"),
        NHentai(lang = "pt", searchLang = "language:portuguese"),
        NHentai(lang = "ru", searchLang = "language:russian"),
        NHentai(lang = "th", searchLang = "language:thai"),
        NHentai(lang = "vi", searchLang = "language:vietnamese"),
        NHentai(lang = "id", searchLang = "language:indonesian"),
        NHentai(lang = "pl", searchLang = "language:polish"),
        NHentai(lang = "ar", searchLang = "language:arabic"),
        NHentai(lang = "uk", searchLang = "language:ukrainian"),
        NHentai(lang = "tr", searchLang = "language:turkish"),
    )
}
