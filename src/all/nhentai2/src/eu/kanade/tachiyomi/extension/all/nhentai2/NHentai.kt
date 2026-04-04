package eu.kanade.tachiyomi.extension.all.nhentai2

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

open class NHentai(
    override val lang: String = "all",
    /** Pre-built language clause injected into every query, e.g. "language:english" */
    private val searchLang: String = "",
) : HttpSource() {

    override val name = "nHentai"

    override val baseUrl = "https://nhentai.net"

    private val apiUrl = "$baseUrl/api/v2"

    override val supportsLatest = true

    override val client = network.cloudflareClient
        .newBuilder()
        // Respect the tightest documented limit: 45 req/min for gallery detail
        .rateLimit(permits = 3, period = 4)
        .build()

    private val json: Json by injectLazy()

    // ─────────────────────────────────────────────────────────
    //  CDN — lazily fetched, cached for the session
    // ─────────────────────────────────────────────────────────

    private var cdnConfig: CdnConfigResponse? = null

    private fun getCdnConfig(): CdnConfigResponse = cdnConfig ?: run {
        val response = client.newCall(GET("$apiUrl/config", headers)).execute()
        val config = response.parseAs<CdnConfigResponse>()
        cdnConfig = config
        config
    }

    /** Returns the primary image CDN base URL (with trailing slash already included if server ends with /).
     *  Falls back to the known public CDN if the response is empty for any reason. */
    private fun imageBase(): String = (getCdnConfig().imageServers.firstOrNull() ?: "https://cdn.nhentai.net").let {
        if (it.endsWith("/")) it else "$it/"
    }

    private fun thumbBase(): String = (getCdnConfig().thumbServers.firstOrNull() ?: "https://t.nhentai.net").let {
        if (it.endsWith("/")) it else "$it/"
    }

    // ─────────────────────────────────────────────────────────
    //  Headers
    // ─────────────────────────────────────────────────────────

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ─────────────────────────────────────────────────────────
    //  Popular Manga
    // ─────────────────────────────────────────────────────────

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/galleries".toHttpUrl().newBuilder().apply {
            if (searchLang.isNotBlank()) addQueryParameter("query", searchLang)
            addQueryParameter("sort", "popular")
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseGalleryList(response)

    // ─────────────────────────────────────────────────────────
    //  Latest Manga
    // ─────────────────────────────────────────────────────────

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/galleries".toHttpUrl().newBuilder().apply {
            if (searchLang.isNotBlank()) addQueryParameter("query", searchLang)
            addQueryParameter("sort", "date")
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseGalleryList(response)

    // ─────────────────────────────────────────────────────────
    //  Search Manga
    // ─────────────────────────────────────────────────────────

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        // Direct ID / URL lookup
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH).trim()
            return client.newCall(detailRequest(id))
                .asObservableSuccess()
                .map { response ->
                    val manga = parseGalleryDetail(response)
                    MangasPage(listOf(manga), false)
                }
        }

        if (query.startsWith("https://nhentai.net/g/")) {
            val id = query.trimEnd('/').substringAfterLast('/')
            return client.newCall(detailRequest(id))
                .asObservableSuccess()
                .map { response ->
                    val manga = parseGalleryDetail(response)
                    MangasPage(listOf(manga), false)
                }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val parts = mutableListOf<String>()

        // Injected language constraint
        if (searchLang.isNotBlank()) parts += searchLang

        // Keyword
        if (query.isNotBlank()) parts += query

        // Filters
        var sortValue = "date"
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> sortValue = filter.toApiValue()
                is LanguageFilter -> {
                    val lang = filter.toQueryPart()
                    if (lang.isNotBlank() && searchLang.isBlank()) parts += lang
                }

                is TagFilter -> {
                    if (filter.state.isNotBlank()) {
                        filter.state.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { term ->
                                parts += if (filter.prefix.isNotBlank()) {
                                    if (term.startsWith("-")) {
                                        "-${filter.prefix}:${term.removePrefix("-")}"
                                    } else {
                                        "${filter.prefix}:$term"
                                    }
                                } else {
                                    term
                                }
                            }
                    }
                }

                else -> {}
            }
        }

        val finalQuery = parts.joinToString(" ").trim().ifEmpty { "*" }

        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("query", finalQuery)
            addQueryParameter("sort", sortValue)
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseGalleryList(response)

    override fun getFilterList(): FilterList = getFilterList(
        defaultLang = when (lang) {
            "en" -> 1
            "ja" -> 2
            "zh" -> 3
            "ko" -> 4
            "es" -> 5
            "fr" -> 6
            "de" -> 7
            "pt" -> 8
            "ru" -> 9
            "th" -> 10
            "vi" -> 11
            "id" -> 12
            "pl" -> 13
            "ar" -> 14
            "uk" -> 15
            "tr" -> 16
            else -> 0
        },
    )

    // ─────────────────────────────────────────────────────────
    //  Manga Details
    // ─────────────────────────────────────────────────────────

    private fun detailRequest(galleryId: String): Request = GET("$apiUrl/galleries/$galleryId", headers)

    override fun mangaDetailsRequest(manga: SManga): Request = detailRequest(manga.url)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(mangaDetailsRequest(manga))
        .asObservableSuccess()
        .map { response -> parseGalleryDetail(response) }

    private fun parseGalleryDetail(response: Response): SManga {
        val detail = response.parseAs<GalleryDetailResponse>()
        return detail.toSManga(thumbBase())
    }

    override fun mangaDetailsParse(response: Response): SManga = parseGalleryDetail(response)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g/${manga.url}/"

    // ─────────────────────────────────────────────────────────
    //  Chapter List — galleries are always a single "chapter"
    // ─────────────────────────────────────────────────────────

    override fun chapterListRequest(manga: SManga): Request = detailRequest(manga.url)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .map { response ->
            val detail = response.parseAs<GalleryDetailResponse>()
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Oneshot"
                    date_upload = detail.uploadDate * 1000L
                    chapter_number = 1f
                },
            )
        }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${chapter.url}/"

    // ─────────────────────────────────────────────────────────
    //  Page List
    // ─────────────────────────────────────────────────────────

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/galleries/${chapter.url}/pages", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pagesRes = response.parseAs<GalleryPagesResponse>()
        val base = imageBase()
        return pagesRes.pages.map { pageInfo ->
            Page(
                index = pageInfo.number - 1,
                imageUrl = "$base${pageInfo.path}",
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private fun parseGalleryList(response: Response): MangasPage {
        val data = response.parseAs<PaginatedResponse>()
        val thumb = thumbBase()
        val mangas = data.result.map { it.toSManga(thumb) }
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = currentPage < data.numPages
        return MangasPage(mangas, hasNextPage)
    }

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
