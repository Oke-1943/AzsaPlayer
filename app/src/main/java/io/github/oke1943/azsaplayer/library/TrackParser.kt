package io.github.oke1943.azsaplayer.library

import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.settings.LibrarySettings
import io.github.oke1943.azsaplayer.tag.RawTags
import io.github.oke1943.azsaplayer.tag.ReplayGainInfo
import io.github.oke1943.azsaplayer.tag.TagRules
import java.io.File
import java.text.Normalizer

/** 曲に結びつく人物1件。[sortValue] はこの曲のソートタグから対応づいた値（4.2）。 */
data class PersonRef(val role: Int, val position: Int, val name: String, val sortValue: String?)

/** タグを解析した結果（DB登録前）。 */
data class ParsedTrack(
    val title: String,
    val titleSort: String?,
    val album: String,
    val albumSort: String?,
    val albumKey: String,
    val artistSortText: String,
    val albumArtistSortText: String,
    val year: Int?,
    val date: String?,
    val trackNo: Int?,
    val trackTotal: Int?,
    val discNo: Int?,
    val discTotal: Int?,
    val persons: List<PersonRef>,
    /** 人物以外の多値フィールド（GENREなど）。分割済み。 */
    val fieldValues: Map<String, List<String>>,
    val replayGain: ReplayGainInfo,
) {
    fun names(role: Int): List<String> = persons.filter { it.role == role }.sortedBy { it.position }.map { it.name }
}

/**
 * タグの解釈（仕様書 第3・4章）。Androidに依存しない純粋なロジックで、単体テストの対象。
 */
object TrackParser {

    private val PERSON_FIELDS = mapOf(
        "ARTIST" to (Role.ARTIST to "ARTISTSORT"),
        "ALBUMARTIST" to (Role.ALBUM_ARTIST to "ALBUMARTISTSORT"),
        "COMPOSER" to (Role.COMPOSER to "COMPOSERSORT"),
    )

    fun parse(path: String, tags: RawTags, settings: LibrarySettings): ParsedTrack {
        val seps = settings.splitSeparators
        val multi = settings.multiValueFields.map { it.uppercase() }.toSet()

        val persons = mutableListOf<PersonRef>()
        val sortTexts = HashMap<Int, String>()
        for ((field, roleAndSort) in PERSON_FIELDS) {
            val (role, sortField) = roleAndSort
            val names = tags.values(field)
            val sorts = tags.values(sortField)
            val pairs = if (field in multi) {
                TagRules.pairWithSort(names, sorts, seps)
            } else {
                // 複数値として扱わない設定のとき：値全体を1人として扱う
                val whole = names.joinToString("; ").let { nfc(it.trim()) }
                if (whole.isEmpty()) emptyList() else listOf(whole to sorts.joinToString("; ").ifBlank { null })
            }
            pairs.forEachIndexed { i, (name, sort) -> persons += PersonRef(role, i, name, sort?.ifBlank { null }) }
            // 曲単位の並び（4.2）：ソートタグがあればそれを、なければ表示名を使う
            val sortList = TagRules.splitRaw(sorts, seps)
            sortTexts[role] = if (sortList.isNotEmpty()) sortList.joinToString("; ") else pairs.joinToString("; ") { it.first }
        }

        val fieldValues = LinkedHashMap<String, List<String>>()
        for (field in multi - PERSON_FIELDS.keys) {
            val v = TagRules.split(tags.values(field), seps)
            if (v.isNotEmpty()) fieldValues[field] = v
        }
        if ("GENRE" !in multi) {
            tags.first("GENRE")?.let { fieldValues["GENRE"] = listOf(nfc(it.trim())) }
        }

        val title = tags.first("TITLE")?.let { nfc(it.trim()) } ?: File(path).nameWithoutExtension
        val album = tags.first("ALBUM")?.let { nfc(it.trim()) } ?: ""
        val albumArtists = persons.filter { it.role == Role.ALBUM_ARTIST }.map { it.name }
        val artists = persons.filter { it.role == Role.ARTIST }.map { it.name }
        val (trackNo, trackTotal) = parseNumberPair(tags.first("TRACKNUMBER"), tags.first("TRACKTOTAL"))
        val (discNo, discTotal) = parseNumberPair(tags.first("DISCNUMBER"), tags.first("DISCTOTAL"))
        val date = tags.first("DATE")?.trim()

        return ParsedTrack(
            title = title,
            titleSort = tags.first("TITLESORT")?.let { nfc(it.trim()) },
            album = album,
            albumSort = tags.first("ALBUMSORT")?.let { nfc(it.trim()) },
            albumKey = albumKey(album, albumArtists.ifEmpty { artists }),
            artistSortText = sortTexts[Role.ARTIST] ?: "",
            albumArtistSortText = sortTexts[Role.ALBUM_ARTIST]?.ifEmpty { null } ?: sortTexts[Role.ARTIST] ?: "",
            year = date?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() },
            date = date,
            trackNo = trackNo,
            trackTotal = trackTotal,
            discNo = discNo,
            discTotal = discTotal,
            persons = persons,
            fieldValues = fieldValues,
            replayGain = ReplayGainInfo.fromProperties(tags.properties, tags.codec),
        )
    }

    /**
     * アルバムの同一性（3.5）：アルバム名＋アルバムアーティストの組。
     * アルバムアーティストがない曲はアーティストで代用する（MusicBeeと同じ）。
     */
    fun albumKey(album: String, albumArtists: List<String>): String =
        TagRules.identityKey(album) + "\u0001" + albumArtists.joinToString("\u0002") { TagRules.identityKey(it) }

    /** 「1/15」や「1」と別欄の合計から（番号, 合計）を取り出す。 */
    fun parseNumberPair(value: String?, totalField: String?): Pair<Int?, Int?> {
        val parts = value?.split('/')?.map { it.trim() } ?: emptyList()
        val n = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 }
        val total = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: totalField?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        return n to total
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
}
