package eu.kanade.tachiyomi.extension.all.nhentai2

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────
//  CDN Config
// ─────────────────────────────────────────────────────────────

@Serializable
data class CdnConfigResponse(
    @SerialName("image_servers") val imageServers: List<String>,
    @SerialName("thumb_servers") val thumbServers: List<String>,
)

// ─────────────────────────────────────────────────────────────
//  Gallery List (search / browse / popular / latest)
// ─────────────────────────────────────────────────────────────

@Serializable
data class PaginatedResponse(
    val result: List<GalleryListItem>,
    @SerialName("num_pages") val numPages: Int,
    @SerialName("per_page") val perPage: Int = 25,
    val total: Int? = null,
)

@Serializable
data class GalleryListItem(
    val id: Int,
    @SerialName("media_id") val mediaId: String,
    @SerialName("english_title") val englishTitle: String,
    @SerialName("japanese_title") val japaneseTitle: String? = null,
    val thumbnail: String,
    @SerialName("thumbnail_width") val thumbnailWidth: Int,
    @SerialName("thumbnail_height") val thumbnailHeight: Int,
    @SerialName("num_pages") val numPages: Int = 0,
    @SerialName("tag_ids") val tagIds: List<Int> = emptyList(),
    val blacklisted: Boolean = false,
) {
    fun toSManga(thumbBase: String): SManga = SManga.create().apply {
        url = id.toString()
        title = englishTitle
        thumbnail_url = "$thumbBase$thumbnail"
        status = SManga.COMPLETED
    }
}

// ─────────────────────────────────────────────────────────────
//  Gallery Detail
// ─────────────────────────────────────────────────────────────

@Serializable
data class GalleryDetailResponse(
    val id: Int,
    @SerialName("media_id") val mediaId: String,
    val title: GalleryTitle,
    val cover: CoverInfo,
    val thumbnail: CoverInfo,
    val scanlator: String = "",
    @SerialName("upload_date") val uploadDate: Long,
    val tags: List<TagResponse>,
    @SerialName("num_pages") val numPages: Int,
    @SerialName("num_favorites") val numFavorites: Int,
    val pages: List<PageInfo> = emptyList(),
) {
    fun toSManga(thumbBase: String): SManga = SManga.create().apply {
        url = id.toString()
        this.title = this@GalleryDetailResponse.title.pretty
        thumbnail_url = "$thumbBase${this@GalleryDetailResponse.thumbnail.path}"
        val tagsByType = tags.groupBy { it.type }

        author = tagsByType["artist"]?.joinToString { it.name }
            ?: tagsByType["group"]?.joinToString { it.name }

        artist = tagsByType["artist"]?.joinToString { it.name }

        genre = tags.filter { it.type != "language" }
            .joinToString { "${it.type}:${it.name}" }

        description = buildString {
            tagsByType["parody"]?.let { append("Parody: ", it.joinToString { p -> p.name }, "\n") }
            tagsByType["character"]?.let { append("Characters: ", it.joinToString { c -> c.name }, "\n") }
            if (scanlator.isNotBlank()) append("Scanlator: ", scanlator, "\n")
            append("Pages: ", numPages, "\n")
            append("Favorites: ", numFavorites, "\n")
            tagsByType["language"]?.let { append("Languages: ", it.joinToString { l -> l.name }, "\n") }
            this@GalleryDetailResponse.title.japanese
                ?.takeIf { it.isNotBlank() }
                ?.let { append("Japanese: ", it, "\n") }
        }.trim()

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }
}

@Serializable
data class GalleryTitle(
    val english: String,
    val japanese: String? = null,
    val pretty: String,
)

@Serializable
data class CoverInfo(
    val path: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class TagResponse(
    val id: Int,
    val type: String,
    val name: String,
    val slug: String,
    val url: String,
    val count: Int,
)

// ─────────────────────────────────────────────────────────────
//  Pages
// ─────────────────────────────────────────────────────────────

@Serializable
data class GalleryPagesResponse(
    @SerialName("gallery_id") val galleryId: Int,
    @SerialName("media_id") val mediaId: String,
    @SerialName("num_pages") val numPages: Int,
    val pages: List<PageInfo>,
)

@Serializable
data class PageInfo(
    val number: Int,
    val path: String,
    val width: Int,
    val height: Int,
    val thumbnail: String,
    @SerialName("thumbnail_width") val thumbnailWidth: Int,
    @SerialName("thumbnail_height") val thumbnailHeight: Int,
)
