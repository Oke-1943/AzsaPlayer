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
    val playlists = PlaylistRepository(db, library, settings)
    val artwork = ArtworkLoader(context)
    val player = PlayerConnection(context, scope)
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
