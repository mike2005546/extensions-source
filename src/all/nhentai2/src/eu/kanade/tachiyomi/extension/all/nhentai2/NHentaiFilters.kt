package eu.kanade.tachiyomi.extension.all.nhentai2

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

// ─────────────────────────────────────────────────────────────
//  Sort Filter
// ─────────────────────────────────────────────────────────────

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf("Recent", "All time", "Today", "Week", "Month"),
    ) {
    fun toApiValue(): String = when (state) {
        1 -> "popular"
        2 -> "popular-today"
        3 -> "popular-week"
        4 -> "popular-month"
        else -> "date"
    }
}

// ─────────────────────────────────────────────────────────────
//  Language Filter
// ─────────────────────────────────────────────────────────────

class LanguageFilter(selected: Int = 0) :
    Filter.Select<String>(
        "Language filter",
        arrayOf(
            "All",
            "English",
            "Japanese",
            "Chinese",
            "Korean",
            "Spanish",
            "French",
            "German",
            "Portuguese",
            "Russian",
            "Thai",
            "Vietnamese",
            "Indonesian",
            "Polish",
            "Arabic",
            "Ukrainian",
            "Turkish",
        ),
        selected,
    ) {
    fun toQueryPart(): String = when (state) {
        1 -> "language:english"
        2 -> "language:japanese"
        3 -> "language:chinese"
        4 -> "language:korean"
        5 -> "language:spanish"
        6 -> "language:french"
        7 -> "language:german"
        8 -> "language:portuguese"
        9 -> "language:russian"
        10 -> "language:thai"
        11 -> "language:vietnamese"
        12 -> "language:indonesian"
        13 -> "language:polish"
        14 -> "language:arabic"
        15 -> "language:ukrainian"
        16 -> "language:turkish"
        else -> ""
    }
}

// ─────────────────────────────────────────────────────────────
//  Tag Type Filter
// ─────────────────────────────────────────────────────────────

class TagFilter(name: String, val prefix: String) : Filter.Text(name)

fun getFilterList(defaultLang: Int = 0) = FilterList(
    Filter.Header("Search supports operators: artist:name, tag:name, parody:name, -word"),
    Filter.Separator(),
    SortFilter(),
//    LanguageFilter(defaultLang),
    Filter.Separator(),
    Filter.Header("Tag text filters"),
    TagFilter("Artist", "artist"),
    TagFilter("Parody", "parody"),
    TagFilter("Character", "character"),
    TagFilter("Group", "group"),
    TagFilter("Tags (include)", "tag"),
    TagFilter("Tags (exclude — prefix with -)", "tag"),
)
