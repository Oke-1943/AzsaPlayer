package io.github.oke1943.azsaplayer.settings

import androidx.annotation.StringRes
import io.github.oke1943.azsaplayer.R
import kotlinx.serialization.Serializable

/**
 * アプリ設定の全体。DataStoreにJSONとして保存し、そのまま書き出し・読み込みできる（6.5）。
 * 新しい項目には必ず初期値を持たせ、古いJSONを読み込んでも壊れないようにする。
 */
@Serializable
data class AppSettings(
    /** 設定の版。古い設定を読み込んだときの移行に使う（[SettingsRepository.migrate]）。 */
    val schema: Int = 1,
    val display: DisplaySettings = DisplaySettings(),
    val library: LibrarySettings = LibrarySettings(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val replayGain: ReplayGainSettings = ReplayGainSettings(),
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

@Serializable
enum class StartTab { FIRST, LAST }

@Serializable
data class DisplaySettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 強調色（ARGB）。 */
    val accentColor: Long = 0xFF6750A4,
    /** 文字サイズの倍率。 */
    val fontScale: Float = 1.0f,
    /** 複数値の連結記号（3.4）。 */
    val joinSeparator: String = "; ",
    val homeViews: List<ViewConfig> = ViewConfig.DEFAULT_HOME,
    val startTab: StartTab = StartTab.FIRST,
    /** ホーム右上に検索ボタンを置く（ver.5）。 */
    val showSearchButton: Boolean = true,
    val nowPlaying: NowPlayingSettings = NowPlayingSettings(),
)

@Serializable
data class NowPlayingSettings(
    val showArtwork: Boolean = true,
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showAlbum: Boolean = true,
    val showFormat: Boolean = false,
    val showReplayGain: Boolean = false,
    val showSeekBar: Boolean = true,
    val showControls: Boolean = true,
)

/** ホームのビューや一覧の段で扱う種類（6.1）。 */
@Serializable
enum class ViewKind(@StringRes val labelRes: Int) {
    ALBUM_ARTIST(R.string.kind_album_artist),
    ARTIST(R.string.kind_artist),
    ALBUM(R.string.kind_album),
    TRACK(R.string.kind_track),
    COMPOSER(R.string.kind_composer),
    GENRE(R.string.kind_genre),
    YEAR(R.string.kind_year),
    DECADE(R.string.kind_decade),
    FOLDER(R.string.kind_folder),
    PLAYLIST(R.string.kind_playlist),
    RECENT(R.string.kind_recent),
    CUSTOM(R.string.kind_custom),
    /** 保存データ上の名前は互換性のため旧名（COVER_FLOW）のまま。 */
    @kotlinx.serialization.SerialName("COVER_FLOW")
    ALBUM_FLOW(R.string.kind_album_flow),
}

@Serializable
enum class SortBy(@StringRes val labelRes: Int) {
    SORT_KEY(R.string.sort_sort_key),
    NAME(R.string.sort_name),
    ARTIST(R.string.sort_artist),
    ALBUM(R.string.sort_album),
    YEAR(R.string.sort_year),
    DATE_ADDED(R.string.sort_date_added),
    TRACK_NUMBER(R.string.sort_track_number),
    COUNT(R.string.sort_count),
}

@Serializable
enum class Layout { LIST, GRID }

/** 一覧のアートワークの大きさの初期値（dp）。どの一覧でも行の高さがそろうよう共通にする。 */
const val ARTWORK_SIZE = 56

/** 一覧1段分の表示形式（6.3）。 */
@Serializable
data class ListStyle(
    val layout: Layout = Layout.LIST,
    val gridColumns: Int = 2,
    val showArtwork: Boolean = false,
    /** アートワークの大きさ（dp）。 */
    val artworkSize: Int = ARTWORK_SIZE,
    /** 1行目のテンプレート。空なら名前。 */
    val line1: String = "",
    /** 2行目のテンプレート。空なら非表示。 */
    val line2: String = "",
    val sortBy: SortBy = SortBy.SORT_KEY,
    val descending: Boolean = false,
    val showIndex: Boolean = true,
)

/**
 * ホームの1ビュー。[levels] はビューから曲に至るまでの段（6.2）。最後は必ず TRACK。
 * [styles] は段ごとの表示形式で、[levels] と同じ添字を使う。
 */
@Serializable
data class ViewConfig(
    val id: String,
    val kind: ViewKind,
    /** タブの名前。空なら種類の名前を表示言語で出す（ver.6）。 */
    val title: String = "",
    /** CUSTOM のときのタグ名（例：LABEL）。 */
    val customTag: String = "",
    val levels: List<ViewKind> = defaultLevels(kind),
    val styles: List<ListStyle> = defaultStyles(levels),
) {
    fun styleAt(level: Int): ListStyle = styles.getOrNull(level) ?: defaultStyle(levels.getOrElse(level) { ViewKind.TRACK })

    companion object {
        fun defaultLevels(kind: ViewKind): List<ViewKind> = when (kind) {
            ViewKind.ALBUM -> listOf(ViewKind.ALBUM, ViewKind.TRACK)
            ViewKind.TRACK, ViewKind.RECENT -> listOf(ViewKind.TRACK)
            ViewKind.PLAYLIST -> listOf(ViewKind.PLAYLIST, ViewKind.TRACK)
            // Album Flow：ジャケットでアルバムを選び、曲へ
            ViewKind.ALBUM_FLOW -> listOf(ViewKind.ALBUM, ViewKind.TRACK)
            ViewKind.FOLDER -> listOf(ViewKind.FOLDER, ViewKind.TRACK)
            else -> listOf(kind, ViewKind.ALBUM, ViewKind.TRACK)
        }

        fun defaultStyle(kind: ViewKind): ListStyle = when (kind) {
            ViewKind.ALBUM, ViewKind.ALBUM_ARTIST -> ListStyle(showArtwork = true)
            ViewKind.TRACK -> ListStyle(sortBy = SortBy.TRACK_NUMBER, line2 = TRACK_LINE2)
            ViewKind.YEAR, ViewKind.DECADE -> ListStyle(sortBy = SortBy.NAME)
            ViewKind.FOLDER, ViewKind.PLAYLIST -> ListStyle(sortBy = SortBy.NAME)
            else -> ListStyle()
        }

        fun defaultStyles(levels: List<ViewKind>): List<ListStyle> = levels.mapIndexed { i, k ->
            when {
                // アルバムアーティスト画面内のアルバムは年の昇順、同年はソートキー順（4.3）
                k == ViewKind.ALBUM && i > 0 -> defaultStyle(k).copy(sortBy = SortBy.YEAR)
                // 曲だけの一覧はタイトルのソートキー順
                k == ViewKind.TRACK && i == 0 -> ListStyle(sortBy = SortBy.SORT_KEY, line2 = TRACK_LINE2)
                else -> defaultStyle(k)
            }
        }

        fun create(kind: ViewKind, customTag: String = ""): ViewConfig {
            val base = ViewConfig(
                id = "${kind.name.lowercase()}-${System.currentTimeMillis().toString(36)}",
                kind = kind,
                title = if (kind == ViewKind.CUSTOM) customTag else "",
                customTag = customTag,
            )
            return when (kind) {
                ViewKind.RECENT -> base.copy(styles = listOf(ListStyle(sortBy = SortBy.DATE_ADDED, descending = true, showIndex = false, line2 = TRACK_LINE2)))
                ViewKind.ALBUM_FLOW -> albumFlow(base.id)
                else -> base
            }
        }

        /** Album Flow のビュー。アルバムはアルバムアーティスト順に並べる。 */
        fun albumFlow(id: String = "album-flow") = ViewConfig(
            id = id,
            kind = ViewKind.ALBUM_FLOW,
            styles = listOf(ListStyle(showArtwork = true, sortBy = SortBy.ARTIST), defaultStyle(ViewKind.TRACK)),
        )

        /** 曲の2行目の初期値：その曲のアーティスト（ver.3）。 */
        const val TRACK_LINE2 = "<Artist>"

        val DEFAULT_HOME: List<ViewConfig> = listOf(
            ViewConfig(id = "album-artist", kind = ViewKind.ALBUM_ARTIST),
            ViewConfig(id = "album", kind = ViewKind.ALBUM),
            albumFlow(),
            ViewConfig(id = "playlist", kind = ViewKind.PLAYLIST),
        )
    }
}

@Serializable
data class PathRule(val from: String, val to: String)

@Serializable
data class LibrarySettings(
    /** 走査するフォルダ。指定したフォルダだけを読む（空なら何も読まない）。 */
    val folders: List<String> = emptyList(),
    val excludedFolders: List<String> = emptyList(),
    val respectNomedia: Boolean = false,
    /** 起動時の自動スキャン。ver.2から廃止（スキャンは設定から手動で行う）。古い設定の読み込み用に残す。 */
    val scanOnStartup: Boolean = false,
    /** 複数値の分割記号（3.3）。初期値は「;」のみ。 */
    val splitSeparators: List<String> = listOf(";"),
    /** 複数値として扱うフィールド。 */
    val multiValueFields: List<String> = listOf("ARTIST", "ALBUMARTIST", "COMPOSER", "GENRE"),
    /** 先頭の冠詞（The, A, An）を並び替えで無視する（4.3）。 */
    val ignoreArticles: Boolean = false,
    /** M3U内のWindowsパスを端末上のパスへ置き換える規則（8章）。 */
    val playlistPathRules: List<PathRule> = emptyList(),
)

@Serializable
data class PlaybackSettings(
    val resumeOnStart: Boolean = true,
)

@Serializable
enum class ReplayGainMode { OFF, TRACK, ALBUM, AUTO }

@Serializable
data class ReplayGainSettings(
    val mode: ReplayGainMode = ReplayGainMode.TRACK,
    /** タグがある曲用のプリアンプ（dB）。 */
    val preampTagged: Float = 0f,
    /** タグがない曲用のプリアンプ（dB）。 */
    val preampUntagged: Float = 0f,
    val preventClipping: Boolean = true,
)
