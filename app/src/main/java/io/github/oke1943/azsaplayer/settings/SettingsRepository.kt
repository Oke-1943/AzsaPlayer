package io.github.oke1943.azsaplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val settingsKey = stringPreferencesKey("app_settings_json")
    private val lastTabKey = stringPreferencesKey("last_home_tab")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { decodeOrNull(it) } ?: AppSettings()
    }

    val lastTab: Flow<String?> = context.dataStore.data.map { it[lastTabKey] }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val now = prefs[settingsKey]?.let { decodeOrNull(it) } ?: AppSettings()
            prefs[settingsKey] = json.encodeToString(AppSettings.serializer(), transform(now))
        }
    }

    /**
     * 古い設定の移行。ver.2：起動時スキャンを廃止し、リプレイゲインはトラックゲインを既定にする。
     */
    suspend fun migrate() {
        update { migrateSettings(it) }
    }

    suspend fun setLastTab(id: String) {
        context.dataStore.edit { it[lastTabKey] = id }
    }

    /** 設定全体のJSON書き出し（6.5）。 */
    suspend fun exportJson(): String = json.encodeToString(AppSettings.serializer(), current())

    /** JSONから読み込む。解析できなければ例外を投げ、現在の設定は変更しない。 */
    suspend fun importJson(text: String) {
        val parsed = json.decodeFromString(AppSettings.serializer(), text)
        update { parsed }
    }

    companion object {
        const val CURRENT_SCHEMA = 8

        /** 旧版（ver.1〜5）で保存された既定のタブ名。 */
        internal val LEGACY_DEFAULT_TITLES: Map<ViewKind, List<String>> = mapOf(
            ViewKind.ALBUM_ARTIST to listOf("アルバムアーティスト"),
            ViewKind.ARTIST to listOf("アーティスト"),
            ViewKind.ALBUM to listOf("アルバム"),
            ViewKind.TRACK to listOf("曲"),
            ViewKind.COMPOSER to listOf("作曲者"),
            ViewKind.GENRE to listOf("ジャンル"),
            ViewKind.YEAR to listOf("年"),
            ViewKind.DECADE to listOf("年代"),
            ViewKind.FOLDER to listOf("フォルダ"),
            ViewKind.PLAYLIST to listOf("プレイリスト"),
            ViewKind.RECENT to listOf("最近追加した曲"),
            ViewKind.CUSTOM to listOf("カスタム"),
            ViewKind.ALBUM_FLOW to listOf("カバーフロー", "Cover Flow"),
        )

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            coerceInputValues = true
        }

        fun decodeOrNull(text: String): AppSettings? =
            runCatching { json.decodeFromString(AppSettings.serializer(), text) }.getOrNull()
    }
}

/**
 * 古い設定の移行（純粋な関数。単体テストの対象）。
 * ver.2：起動時スキャンを廃止し、リプレイゲインはトラックゲインを既定にする。
 */
fun migrateSettings(old: AppSettings): AppSettings {
    var s = old
    if (s.schema < 2) {
        s = s.copy(
            library = s.library.copy(scanOnStartup = false),
            replayGain = s.replayGain.copy(mode = ReplayGainMode.TRACK),
        )
    }
    if (s.schema < 3) {
        // ver.3：曲一覧の2行目にアーティストを表示（2行目を空にしていた段だけ）
        s = s.copy(display = s.display.copy(homeViews = s.display.homeViews.map { v ->
            v.copy(styles = v.levels.indices.map { i ->
                val st = v.styleAt(i)
                if (v.levels[i] == ViewKind.TRACK && st.line2.isBlank()) st.copy(line2 = ViewConfig.TRACK_LINE2) else st
            })
        }))
    }
    if (s.schema < 4) {
        // ver.4：アートワークの大きさを56dpに統一し、Album Flow のタブをアルバムの隣に追加
        var views = s.display.homeViews.map { v ->
            v.copy(styles = v.levels.indices.map { i ->
                val st = v.styleAt(i)
                if (st.artworkSize == 48) st.copy(artworkSize = ARTWORK_SIZE) else st
            })
        }
        if (views.none { it.kind == ViewKind.ALBUM_FLOW }) {
            val at = views.indexOfFirst { it.kind == ViewKind.ALBUM }.let { if (it < 0) views.size else it + 1 }
            views = views.toMutableList().apply { add(at, ViewConfig.albumFlow()) }
        }
        s = s.copy(display = s.display.copy(homeViews = views))
    }
    if (s.schema < 6) {
        // ver.6：既定のタブ名（旧版で保存された日本語の名前）は空にして、表示言語に追従させる
        s = s.copy(display = s.display.copy(homeViews = s.display.homeViews.map { v ->
            val legacy = SettingsRepository.LEGACY_DEFAULT_TITLES[v.kind].orEmpty() + listOfNotNull(v.customTag.takeIf { v.kind == ViewKind.CUSTOM })
            if (v.title in legacy) v.copy(title = "") else v
        }))
    }
    if (s.schema < 7) {
        // ver.7：「Cover Flow」を「Album Flow」に改名。旧名のままのタブ名は空にして新しい名前に追従させる
        s = s.copy(display = s.display.copy(homeViews = s.display.homeViews.map { v ->
            if (v.kind == ViewKind.ALBUM_FLOW && v.title in listOf("カバーフロー", "Cover Flow")) v.copy(title = "") else v
        }))
    }
    if (s.schema < 8) {
        // ver.8（アプリ0.7.0）：アルバムアーティストの一覧にジャケットを表示するのを既定にする
        s = s.copy(display = s.display.copy(homeViews = s.display.homeViews.map { v ->
            if (v.levels.firstOrNull() != ViewKind.ALBUM_ARTIST) v
            else v.copy(styles = v.levels.indices.map { i -> if (i == 0) v.styleAt(0).copy(showArtwork = true) else v.styleAt(i) })
        }))
    }
    return if (s.schema == SettingsRepository.CURRENT_SCHEMA) s else s.copy(schema = SettingsRepository.CURRENT_SCHEMA)
}
