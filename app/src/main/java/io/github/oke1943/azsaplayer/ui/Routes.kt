package io.github.oke1943.azsaplayer.ui

import android.util.Base64
import io.github.oke1943.azsaplayer.library.Selection
import io.github.oke1943.azsaplayer.settings.ViewKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 画面遷移の行き先。 */
object Routes {
    const val HOME = "home"
    const val NOW_PLAYING = "now"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val SETTINGS_DISPLAY = "settings/display"
    const val SETTINGS_HOME_VIEWS = "settings/home"
    const val SETTINGS_LIBRARY = "settings/library"
    const val SETTINGS_PLAYBACK = "settings/playback"
    const val SETTINGS_RG = "settings/rg"
    const val SETTINGS_ADVANCED = "settings/advanced"
    const val TAG_DUMP = "settings/tagdump"
    const val SCAN_ERRORS = "settings/errors"
    const val LICENSES = "settings/licenses"

    /** 再生画面からアルバムを開くときの仮のビュー（ホームの「アルバム」ビューの形式を借りる）。 */
    const val ALBUM_VIEW_ID = "_album"
    /** 検索から人物を開くときの仮のビュー。 */
    const val ARTIST_VIEW_ID = "_artist"
    const val ALBUM_ARTIST_VIEW_ID = "_albumartist"
    const val COMPOSER_VIEW_ID = "_composer"
    const val SEARCH = "search"

    /** 仮のビューIDと、そのビューの種類。 */
    val SYNTHETIC_VIEWS = mapOf(
        ALBUM_VIEW_ID to ViewKind.ALBUM,
        ARTIST_VIEW_ID to ViewKind.ARTIST,
        ALBUM_ARTIST_VIEW_ID to ViewKind.ALBUM_ARTIST,
        COMPOSER_VIEW_ID to ViewKind.COMPOSER,
    )

    fun browse(viewId: String, level: Int, path: List<Selection>) = "browse/$viewId/$level/${encodePath(path)}"
    fun viewEdit(viewId: String) = "settings/view/$viewId"
    fun track(id: Long) = "track/$id"
    fun playlistEdit(id: Long) = "playlist/$id"

    @Serializable
    private data class Sel(val k: ViewKind, val key: String, val t: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun encodePath(path: List<Selection>): String {
        val text = json.encodeToString(ListSerializer(Sel.serializer()), path.map { Sel(it.kind, it.key, it.title) })
        return Base64.encodeToString(text.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).ifEmpty { "_" }
    }

    fun decodePath(encoded: String): List<Selection> {
        if (encoded == "_" || encoded.isEmpty()) return emptyList()
        return runCatching {
            val text = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            json.decodeFromString(ListSerializer(Sel.serializer()), text).map { Selection(it.k, it.key, it.t) }
        }.getOrDefault(emptyList())
    }
}
