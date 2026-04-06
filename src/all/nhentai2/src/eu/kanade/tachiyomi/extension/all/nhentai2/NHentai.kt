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
import okhttp3.Interceptor
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
        .addInterceptor(DomainRetryInterceptor())
        .build()

    private val json: Json by injectLazy()

    // ─────────────────────────────────────────────────────────
    //  CDN — lazily fetched, cached for the session
    // ─────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────
    //  CDN — stable defaults with subdomain retry
    // ─────────────────────────────────────────────────────────

    /** Returns the primary image CDN base URL. Defaults to i.nhentai.net. */
    private fun imageBase(): String = "https://i.nhentai.net/"

    /** Returns the primary thumbnail CDN base URL. Defaults to t.nhentai.net. */
    private fun thumbBase(): String = "https://t.nhentai.net/"

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
        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("query", "*")
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

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/galleries/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pagesRes = response.parseAs<GalleryDetailResponse>()
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

    private fun parseGalleryArray(response: Response): MangasPage {
        val data = response.parseAs<List<GalleryListItem>>()
        val thumb = thumbBase()
        val mangas = data.map { it.toSManga(thumb) }
        return MangasPage(mangas, false)
    }

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

    // ─────────────────────────────────────────────────────────
    //  Interceptor
    // ─────────────────────────────────────────────────────────

    /** Interceptor to retry image and thumbnail requests across alternate subdomains on 404 error. */
    private class DomainRetryInterceptor : Interceptor {
        private val imgSubdomains = listOf("i", "i1", "i2", "i3", "i4")
        private val thumbSubdomains = listOf("t", "t1", "t2", "t3", "t4")

        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)

            if (response.code != 404) return response

            val url = originalRequest.url
            val host = url.host
            if (!host.endsWith(".nhentai.net")) return response

            val subdomains = when {
                host.startsWith("i") -> imgSubdomains
                host.startsWith("t") -> thumbSubdomains
                else -> return response
            }

            // Extract the current prefix (e.g. "i3")
            val currentPrefix = host.substringBefore(".")

            // Try subsequent subdomains in the list
            val currentIndex = subdomains.indexOf(currentPrefix)
            val nextSubdomains = if (currentIndex != -1) {
                subdomains.subList(currentIndex + 1, subdomains.size)
            } else {
                subdomains
            }

            var latestResponse = response
            for (nextPrefix in nextSubdomains) {
                latestResponse.close()
                val newUrl = url.newBuilder()
                    .host("$nextPrefix.nhentai.net")
                    .build()
                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .build()
                latestResponse = chain.proceed(newRequest)
                if (latestResponse.isSuccessful) return latestResponse
                if (latestResponse.code != 404) return latestResponse
            }

            return latestResponse
        }
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
