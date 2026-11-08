package io.github.oke1943.azsaplayer

import android.app.Application
import android.content.Context
import io.github.oke1943.azsaplayer.data.AppDatabase
import io.github.oke1943.azsaplayer.library.ArtworkLoader
import io.github.oke1943.azsaplayer.library.LibraryRepository
import io.github.oke1943.azsaplayer.playback.PlayerConnection
import io.github.oke1943.azsaplayer.playlist.PlaylistRepository
import io.github.oke1943.azsaplayer.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** アプリ全体で共有する部品。 */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val library = LibraryRepository(context, db, settings, scope)
    val playlists = PlaylistRepository(db, library, settings) {
        AppLocale.wrap(context).getString(R.string.smart_playlist_name)
    }
    val artwork = ArtworkLoader(context)
    val player = PlayerConnection(context, scope)

    /**
     * 画面を閉じても取り消されない書き込み用のスコープ（メインスレッド。トーストも出せる）。
     * プレイリストへの追加のように、操作の直後に画面やダイアログが閉じる処理で使う。
     */
    fun persistScope(): CoroutineScope = CoroutineScope(scope.coroutineContext + Dispatchers.Main)
}

class MusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as MusicApp).container
