package io.github.oke1943.azsaplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.Selection
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.ui.browse.BrowseScreen
import io.github.oke1943.azsaplayer.ui.browse.HomeScreen
import io.github.oke1943.azsaplayer.ui.browse.PlaylistEditScreen
import io.github.oke1943.azsaplayer.ui.browse.TrackInfoScreen
import io.github.oke1943.azsaplayer.ui.player.NowPlayingScreen
import io.github.oke1943.azsaplayer.ui.player.QueueScreen
import io.github.oke1943.azsaplayer.ui.settings.AdvancedSettingsScreen
import io.github.oke1943.azsaplayer.ui.settings.DisplaySettingsScreen
import io.github.oke1943.azsaplayer.ui.settings.HomeViewsScreen
import io.github.oke1943.azsaplayer.ui.settings.LibrarySettingsScreen
import io.github.oke1943.azsaplayer.ui.settings.PlaybackSettingsScreen
import io.github.oke1943.azsaplayer.ui.settings.ReplayGainSettingsScreen
import io.github.oke1943.azsaplayer.ui.settings.ScanErrorsScreen
import io.github.oke1943.azsaplayer.ui.settings.SettingsHomeScreen
import io.github.oke1943.azsaplayer.ui.settings.TagDumpScreen
import io.github.oke1943.azsaplayer.ui.theme.MusicAppTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.oke1943.azsaplayer.StorageAccess
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings
    if (s == null) {
        // 設定を読み終えるまで（一瞬）何も描かない。初期値での描き直しによるタブのちらつきを防ぐ
        MusicAppTheme { Surface(Modifier.fillMaxSize()) {} }
        return
    }
    MusicAppTheme(s.display) {
        Surface(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            var hasStorageAccess by remember { mutableStateOf(StorageAccess.hasAny(context)) }
            LifecycleResumeEffect(Unit) {
                hasStorageAccess = StorageAccess.hasAny(context)
                onPauseOrDispose { }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                io.github.oke1943.azsaplayer.ui.browse.LocalBrowseLabels provides io.github.oke1943.azsaplayer.ui.browse.rememberBrowseLabels(),
            ) {
                if (!hasStorageAccess) {
                    StoragePermissionScreen(onGranted = { hasStorageAccess = StorageAccess.hasAny(context) })
                } else {
                    Startup(s)
                    AppNavHost(s)
                }
            }
        }
    }
}

/** 起動時の処理：設定の移行、ライブラリの読み込み、再生サービスへの接続。スキャンは設定から手動で行う。 */
@Composable
private fun Startup(@Suppress("UNUSED_PARAMETER") settings: AppSettings) {
    val c = LocalContext.current.container
    LaunchedEffect(Unit) {
        c.settings.migrate()
        c.player.connect()
        if (!c.library.loaded.value) c.library.reload()
    }
}

private const val NAV_MS = 250
private val DECELERATE = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Composable
private fun AppNavHost(settings: AppSettings) {
    val nav = rememberNavController()
    val lastTab by LocalContext.current.container.settings.lastTab.collectAsStateWithLifecycle(initialValue = null)
    val join = settings.display.joinSeparator

    fun openGroup(view: ViewConfig, level: Int, path: List<Selection>, g: GroupEntry) {
        nav.navigate(Routes.browse(view.id, level + 1, path + Selection(g.kind, g.key, g.title)))
    }

    // 画面遷移（ver.5）：行きと戻りが対称な横方向の遷移。速く動き出し、なめらかに止まる
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(NAV_MS, easing = DECELERATE)) { it / 6 } + fadeIn(tween(NAV_MS - 60, delayMillis = 40)) },
        exitTransition = { slideOutHorizontally(tween(NAV_MS, easing = DECELERATE)) { -it / 12 } + fadeOut(tween(NAV_MS / 2)) },
        popEnterTransition = { slideInHorizontally(tween(NAV_MS, easing = DECELERATE)) { -it / 12 } + fadeIn(tween(NAV_MS - 60, delayMillis = 40)) },
        popExitTransition = { slideOutHorizontally(tween(NAV_MS, easing = DECELERATE)) { it / 6 } + fadeOut(tween(NAV_MS / 2)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                lastTab = lastTab,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSearch = { nav.navigate(Routes.SEARCH) },
                onOpenGroup = { v, g -> openGroup(v, 0, emptyList(), g) },
                onOpenTrackInfo = { nav.navigate(Routes.track(it)) },
                onOpenNowPlaying = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable(
            "browse/{viewId}/{level}/{path}",
            arguments = listOf(
                navArgument("viewId") { type = NavType.StringType },
                navArgument("level") { type = NavType.IntType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { entry ->
            val viewId = entry.arguments?.getString("viewId") ?: ""
            val level = entry.arguments?.getInt("level") ?: 1
            val path = remember(entry) { Routes.decodePath(entry.arguments?.getString("path") ?: "") }
            BrowseScreen(
                settings = settings, viewId = viewId, level = level, path = path,
                onBack = { nav.popBackStack() },
                onOpenGroup = { v, g -> openGroup(v, level, path, g) },
                onOpenTrackInfo = { nav.navigate(Routes.track(it)) },
                onOpenNowPlaying = { nav.navigate(Routes.NOW_PLAYING) },
                onEditPlaylist = { nav.navigate(Routes.playlistEdit(it)) },
            )
        }
        composable(
            Routes.NOW_PLAYING,
            // 再生画面は下からせり上がり、閉じると下へ
            enterTransition = { slideInVertically(tween(NAV_MS, easing = DECELERATE)) { it / 5 } + fadeIn(tween(NAV_MS - 60)) },
            popExitTransition = { slideOutVertically(tween(NAV_MS, easing = DECELERATE)) { it / 5 } + fadeOut(tween(NAV_MS / 2)) },
        ) {
            NowPlayingScreen(
                settings.display.nowPlaying,
                onBack = { nav.popBackStack() },
                onOpenQueue = { nav.navigate(Routes.QUEUE) },
                onOpenAlbum = { key, title ->
                    nav.navigate(Routes.browse(Routes.ALBUM_VIEW_ID, 1, listOf(Selection(io.github.oke1943.azsaplayer.settings.ViewKind.ALBUM, key, title))))
                },
                onOpenAlbumArtist = { p ->
                    nav.navigate(Routes.browse(Routes.ALBUM_ARTIST_VIEW_ID, 1, listOf(Selection(io.github.oke1943.azsaplayer.settings.ViewKind.ALBUM_ARTIST, p.id.toString(), p.name))))
                },
            )
        }
        composable(Routes.QUEUE) { QueueScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SEARCH) {
            io.github.oke1943.azsaplayer.ui.browse.SearchScreen(
                settings = settings,
                onBack = { nav.popBackStack() },
                onOpenPerson = { role, p ->
                    val (viewId, kind) = when (role) {
                        io.github.oke1943.azsaplayer.data.Role.ALBUM_ARTIST -> Routes.ALBUM_ARTIST_VIEW_ID to io.github.oke1943.azsaplayer.settings.ViewKind.ALBUM_ARTIST
                        io.github.oke1943.azsaplayer.data.Role.ARTIST -> Routes.ARTIST_VIEW_ID to io.github.oke1943.azsaplayer.settings.ViewKind.ARTIST
                        else -> Routes.COMPOSER_VIEW_ID to io.github.oke1943.azsaplayer.settings.ViewKind.COMPOSER
                    }
                    nav.navigate(Routes.browse(viewId, 1, listOf(Selection(kind, p.id.toString(), p.name))))
                },
                onOpenAlbum = { a ->
                    nav.navigate(Routes.browse(Routes.ALBUM_VIEW_ID, 1, listOf(Selection(io.github.oke1943.azsaplayer.settings.ViewKind.ALBUM, a.key, a.title))))
                },
                onOpenTrackInfo = { nav.navigate(Routes.track(it)) },
                onOpenNowPlaying = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable("track/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            TrackInfoScreen(entry.arguments?.getLong("id") ?: 0, join, onBack = { nav.popBackStack() })
        }
        composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            PlaylistEditScreen(entry.arguments?.getLong("id") ?: 0, join, onBack = { nav.popBackStack() })
        }
        settingsGraph(nav, settings)
    }
}

private fun androidx.navigation.NavGraphBuilder.settingsGraph(nav: NavHostController, settings: AppSettings) {
    val back: () -> Unit = { nav.popBackStack() }
    composable(Routes.SETTINGS) { SettingsHomeScreen(onBack = back, onOpen = { nav.navigate(it) }) }
    composable(Routes.SETTINGS_DISPLAY) {
        DisplaySettingsScreen(settings, onBack = back, onOpenHomeViews = { nav.navigate(Routes.SETTINGS_HOME_VIEWS) })
    }
    composable(Routes.SETTINGS_HOME_VIEWS) {
        HomeViewsScreen(settings, onBack = back, onEditView = { nav.navigate(Routes.viewEdit(it)) })
    }
    composable("settings/view/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
        io.github.oke1943.azsaplayer.ui.settings.ViewEditScreen(settings, entry.arguments?.getString("id") ?: "", onBack = back)
    }
    composable(Routes.SETTINGS_LIBRARY) { LibrarySettingsScreen(settings, onBack = back) }
    composable(Routes.SETTINGS_PLAYBACK) { PlaybackSettingsScreen(settings, onBack = back) }
    composable(Routes.SETTINGS_RG) { ReplayGainSettingsScreen(settings, onBack = back) }
    composable(Routes.SETTINGS_ADVANCED) {
        AdvancedSettingsScreen(
            onBack = back,
            onOpenTagDump = { nav.navigate(Routes.TAG_DUMP) },
            onOpenErrors = { nav.navigate(Routes.SCAN_ERRORS) },
            onOpenLicenses = { nav.navigate(Routes.LICENSES) },
        )
    }
    composable(Routes.LICENSES) { io.github.oke1943.azsaplayer.ui.settings.LicensesScreen(onBack = back) }
    composable(Routes.TAG_DUMP) { TagDumpScreen(onExit = back) }
    composable(Routes.SCAN_ERRORS) { ScanErrorsScreen(onBack = back) }
}

/**
 * 初回起動時の権限の選択（ver.6）。
 * 「音楽ファイルの読み取りのみ」（おすすめ）か「すべてのファイルへのアクセス」かを選んでもらう。
 */
@Composable
private fun StoragePermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val request = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (StorageAccess.hasMediaRead(context)) onGranted() else denied = true
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.perm_body), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)

            androidx.compose.material3.OutlinedCard(Modifier.padding(top = 24.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.perm_media_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.perm_media_body), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                    Button(modifier = Modifier.padding(top = 12.dp), onClick = { request.launch(StorageAccess.mediaPermissions()) }) {
                        Text(stringResource(R.string.perm_media_button))
                    }
                    if (denied) {
                        Text(stringResource(R.string.perm_denied), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.TextButton(onClick = { context.startActivity(StorageAccess.appDetailsIntent(context)) }) {
                            Text(stringResource(R.string.perm_open_app_settings))
                        }
                    }
                }
            }
            androidx.compose.material3.OutlinedCard(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.perm_all_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.perm_all_body), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedButton(
                        modifier = Modifier.padding(top = 12.dp),
                        onClick = { context.startActivity(StorageAccess.allFilesSettingsIntent(context)) },
                    ) { Text(stringResource(R.string.perm_all_button)) }
                }
            }
        }
    }
}
