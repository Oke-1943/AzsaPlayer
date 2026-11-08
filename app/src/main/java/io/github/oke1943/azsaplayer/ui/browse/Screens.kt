package io.github.oke1943.azsaplayer.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.BrowseEngine
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.PlaylistInfo
import io.github.oke1943.azsaplayer.library.Selection
import io.github.oke1943.azsaplayer.library.SortRules
import io.github.oke1943.azsaplayer.playback.ShuffleMode
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.StartTab
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.ui.player.MiniPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.oke1943.azsaplayer.ui.common.displayTitle
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/**
 * ホーム（6章）。初期状態はアルバムアーティスト・アルバム・プレイリストの3タブ、
 * 下部にミニプレーヤー、右上に設定への入口。それ以外の要素は置かない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    lastTab: String?,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGroup: (ViewConfig, GroupEntry) -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val c = LocalContext.current.container
    val views = settings.display.homeViews.ifEmpty { ViewConfig.DEFAULT_HOME }
    val initial = remember {
        if (settings.display.startTab == StartTab.LAST) views.indexOfFirst { it.id == lastTab }.coerceAtLeast(0) else 0
    }
    val pager = rememberPagerState(initialPage = initial) { views.size }
    val scope = rememberCoroutineScope()
    val scan by c.library.scan.collectAsStateWithLifecycle()

    // Album Flow のタブを開いた回数（別のタブから移ってきたとき・起動時だけ数える。アルバムから戻ったときは数えない）
    var flowVisit by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastPage by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(pager, views) {
        snapshotFlow { pager.currentPage }.collect { page ->
            views.getOrNull(page)?.let { c.settings.setLastTab(it.id) }
            if (page != lastPage) {
                if (views.getOrNull(page)?.kind == ViewKind.ALBUM_FLOW) flowVisit++
                lastPage = page
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (views.size > 1) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = pager.currentPage.coerceIn(0, views.size - 1),
                            modifier = Modifier.weight(1f),
                            edgePadding = 8.dp,
                            divider = {},
                        ) {
                            views.forEachIndexed { i, v ->
                                Tab(
                                    selected = pager.currentPage == i,
                                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                                    text = { Text(v.displayTitle(), maxLines = 1) },
                                )
                            }
                        }
                    } else {
                        // ビューが1つだけのときはタブ列を出さない（6.1）
                        Box(Modifier.weight(1f))
                    }
                    if (settings.display.showSearchButton) {
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search)) }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) }
                }
                if (scan.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = { MiniPlayer(onOpen = onOpenNowPlaying) },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            beyondViewportPageCount = 0,
        ) { page ->
            val view = views[page]
            if (view.kind == ViewKind.ALBUM_FLOW) {
                AlbumFlow(
                    view = view, settings = settings,
                    onOpenGroup = { onOpenGroup(view, it) },
                    onOpenTrackInfo = onOpenTrackInfo,
                    bottomPadding = maxOf(padding.calculateBottomPadding(), navBarBottom()),
                    visit = flowVisit,
                )
            } else {
                BrowseList(
                    view = view, level = 0, path = emptyList(), settings = settings,
                    onOpenGroup = { onOpenGroup(view, it) },
                    onOpenTrackInfo = onOpenTrackInfo,
                    contentPadding = PaddingValues(bottom = maxOf(padding.calculateBottomPadding(), navBarBottom())),
                )
            }
        }
    }
}

/** ビューの2段目以降（例：アルバムアーティスト → アルバム → 曲）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    settings: AppSettings,
    viewId: String,
    level: Int,
    path: List<Selection>,
    onBack: () -> Unit,
    onOpenGroup: (ViewConfig, GroupEntry) -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onEditPlaylist: (Long) -> Unit,
    /** アーティストのアルバムの並び順を編集する（ver.0.6.2）。 */
    onEditAlbumOrder: () -> Unit = {},
) {
    val c = LocalContext.current.container
    // 仮のビュー（再生画面や検索から開く）は、ホームにある同じ種類のビューの形式を借りる
    val view = io.github.oke1943.azsaplayer.ui.resolveView(settings, viewId)
    if (view == null) { onBack(); return }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    // プレイリストへの追加：一括（addIds）と、チェックボックスで選ぶ選択モード（selection）
    var addIds by remember { mutableStateOf<List<Long>?>(null) }
    var selection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<List<String>?>(null) }
    val playlists by c.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList<PlaylistInfo>())

    val personAlbums = view.levels.getOrNull(level) == ViewKind.ALBUM && path.lastOrNull()?.kind in io.github.oke1943.azsaplayer.library.PERSON_KINDS
    // 1枚のアルバムの曲一覧（アルバム単位のシャッフルは意味がない）
    val singleAlbum = path.lastOrNull()?.kind == ViewKind.ALBUM && view.levels.getOrNull(level) == ViewKind.TRACK

    suspend fun pathTracks(): List<Long> = withContext(Dispatchers.Default) {
        val engine = BrowseEngine(
            c.library.snapshot.value, SortRules(settings.library.ignoreArticles), playlists, c.library::customValues,
            albumOrder = settings.display.albumOrder, artistAlbumOrder = settings.display.artistAlbumOrder,
            customAlbumOrder = settings.display.customAlbumOrder,
        )
        val tracks = engine.tracksFor(view, path).filter { it.e.playable }
        if (personAlbums) {
            // アーティストの全曲：画面に並んでいるアルバムの順（カスタム順を含む）→ ディスク・トラック番号順
            val inPath = tracks.map { it.id }.toHashSet()
            engine.entries(view, level, path).filterIsInstance<GroupEntry>().flatMap { g ->
                g.tracks.filter { it.id in inPath }.sortedWith(compareBy({ it.e.discNo ?: 1 }, { it.e.trackNo ?: Int.MAX_VALUE }))
            }.map { it.id }
        } else if (path.lastOrNull()?.kind == ViewKind.PLAYLIST) tracks.map { it.id }
        else tracks.sortedWith(compareBy({ it.album.year ?: Int.MAX_VALUE }, { it.album.titleSort ?: it.album.title }, { it.e.discNo ?: 1 }, { it.e.trackNo ?: Int.MAX_VALUE }))
            .map { it.id }
    }

    val entries = rememberEntries(view, level, path, settings)
    androidx.activity.compose.BackHandler(enabled = selection != null) { selection = null }
    addIds?.let { ids -> AddToPlaylistDialog(ids, onDone = { addIds = null; selection = null }) }

    Scaffold(
        topBar = {
            val sel = selection
            if (sel != null) {
                val all = entries.orEmpty()
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, sel.size)) },
                    navigationIcon = { IconButton(onClick = { selection = null }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) } },
                    actions = {
                        IconButton(onClick = { selection = if (sel.size == all.size) emptyList() else all.map { it.key } }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                        IconButton(enabled = sel.isNotEmpty(), onClick = {
                            val keys = sel.toSet()
                            addIds = all.filter { it.key in keys }.flatMap { entryTrackIds(it) }.distinct()
                        }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.menu_add_to_playlist)) }
                    },
                )
                return@Scaffold
            }
            TopAppBar(
                title = { Text(path.lastOrNull()?.title ?: view.displayTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.play_all)) }, onClick = { menu = false; scope.launch { c.player.play(pathTracks()) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_shuffle)) }, onClick = { menu = false; scope.launch { c.player.play(pathTracks(), 0, ShuffleMode.TRACKS) } })
                        if (!singleAlbum) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.shuffle_by_album)) }, onClick = { menu = false; scope.launch { c.player.play(pathTracks(), 0, ShuffleMode.ALBUMS) } })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_queue)) }, onClick = { menu = false; scope.launch { c.player.addToQueue(pathTracks()) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.add_all_to_playlist)) }, onClick = { menu = false; scope.launch { addIds = pathTracks() } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.select_to_add)) }, onClick = { menu = false; selection = emptyList() })
                        if (personAlbums) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.album_order)) }, onClick = { menu = false; onEditAlbumOrder() })
                        }
                        val playlistId = path.lastOrNull()?.takeIf { it.kind == ViewKind.PLAYLIST }?.key?.toLongOrNull()
                        if (playlistId != null && playlistId != io.github.oke1943.azsaplayer.playlist.SMART_ID) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.reorder_edit)) }, onClick = { menu = false; onEditPlaylist(playlistId) })
                        }
                    }
                },
            )
        },
        bottomBar = { MiniPlayer(onOpen = onOpenNowPlaying) },
    ) { padding ->
        val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
        val album = path.lastOrNull()?.takeIf { it.kind == ViewKind.ALBUM }?.let { snapshot.albumsByKey[it.key] }
        val trackLevel = view.levels.getOrNull(level) == ViewKind.TRACK
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            BrowseList(
                view = view, level = level, path = path, settings = settings,
                onOpenGroup = { onOpenGroup(view, it) },
                onOpenTrackInfo = onOpenTrackInfo,
                contentPadding = PaddingValues(bottom = maxOf(padding.calculateBottomPadding(), navBarBottom())),
                header = when {
                    album != null && trackLevel -> {
                        {
                            AlbumHeader(
                                album, settings.display.joinSeparator,
                                onPlay = { scope.launch { c.player.play(pathTracks()) } },
                                onShuffle = { scope.launch { c.player.play(pathTracks(), 0, ShuffleMode.TRACKS) } },
                            )
                        }
                    }
                    else -> null
                },
                showTrackNumbers = album != null && trackLevel && view.styleAt(level).sortBy == io.github.oke1943.azsaplayer.settings.SortBy.TRACK_NUMBER,
                selected = selection?.toSet(),
                onToggleSelect = { e -> selection = selection?.let { if (e.key in it) it - e.key else it + e.key } },
            )
        }
    }
}

@Composable
fun SmallLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** ミニプレーヤーがないときも、一覧の末尾と索引がナビゲーションバーに隠れないようにする。 */
@Composable
private fun navBarBottom() =
    androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** アルバム画面の見出し：ジャケットと基本情報、再生ボタン。 */
@Composable
private fun AlbumHeader(album: io.github.oke1943.azsaplayer.library.LibAlbum, join: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val tracks = album.tracks
    val genres = tracks.flatMap { it.genres }.distinct()
    val meta = listOfNotNull(album.year?.toString(), genres.joinToString(join).ifEmpty { null }).joinToString(" · ")
    val total = io.github.oke1943.azsaplayer.library.Template.formatDuration(tracks.sumOf { it.e.durationMs })
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            io.github.oke1943.azsaplayer.ui.common.ArtworkImage(album.artTrack.e, 128.dp, corner = 6.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    album.title.ifEmpty { stringResource(R.string.browse_no_album) }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    album.albumArtists.joinToString(join) { it.name }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Text(stringResource(R.string.tracks_and_duration, tracks.size, total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = onPlay) {
                Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.play), Modifier.padding(start = 4.dp))
            }
            IconButton(onClick = onShuffle) { Icon(androidx.compose.material.icons.Icons.Filled.Shuffle, contentDescription = stringResource(R.string.menu_shuffle)) }
        }
        androidx.compose.material3.HorizontalDivider()
    }
}

/** 一覧の項目に含まれる再生できる曲（アルバムなどはディスク・トラック番号順）。 */
private fun entryTrackIds(e: io.github.oke1943.azsaplayer.library.BrowseEntry): List<Long> = when (e) {
    is GroupEntry -> if (e.kind == ViewKind.PLAYLIST) e.tracks.filter { it.e.playable }.map { it.id }
        else e.tracks.filter { it.e.playable }.sortedWith(compareBy({ it.album.titleSort ?: it.album.title }, { it.e.discNo ?: 1 }, { it.e.trackNo ?: Int.MAX_VALUE })).map { it.id }
    is io.github.oke1943.azsaplayer.library.TrackEntry -> if (e.track.e.playable) listOf(e.track.id) else emptyList()
}
