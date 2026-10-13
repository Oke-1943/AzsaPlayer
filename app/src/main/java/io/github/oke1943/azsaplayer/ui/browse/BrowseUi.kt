package io.github.oke1943.azsaplayer.ui.browse

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.BrowseEngine
import io.github.oke1943.azsaplayer.library.BrowseEntry
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.PlaylistInfo
import io.github.oke1943.azsaplayer.library.Selection
import io.github.oke1943.azsaplayer.library.SortRules
import io.github.oke1943.azsaplayer.library.Template
import io.github.oke1943.azsaplayer.library.TrackEntry
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.Layout
import io.github.oke1943.azsaplayer.settings.ListStyle
import io.github.oke1943.azsaplayer.settings.SortBy
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.oke1943.azsaplayer.library.BrowseLabels
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/** 一覧の中身を計算する（重い処理はバックグラウンドで）。 */
@Composable
fun rememberEntries(view: ViewConfig, level: Int, path: List<Selection>, settings: AppSettings): List<BrowseEntry>? {
    val c = LocalContext.current.container
    val labels = LocalBrowseLabels.current
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    val playlists by c.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList<PlaylistInfo>())
    val entries by produceState<List<BrowseEntry>?>(null, snapshot, playlists, view, level, path, settings.library.ignoreArticles) {
        value = withContext(Dispatchers.Default) {
            BrowseEngine(snapshot, SortRules(settings.library.ignoreArticles), playlists, c.library::customValues, labels)
                .entries(view, level, path)
        }
    }
    return entries
}

/** ビューの1段分の一覧（6.3）。 */
@Composable
fun BrowseList(
    view: ViewConfig,
    level: Int,
    path: List<Selection>,
    settings: AppSettings,
    onOpenGroup: (GroupEntry) -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** 一覧の先頭に置く見出し（アルバム画面のジャケットなど）。一覧と一緒にスクロールする。 */
    header: (@Composable () -> Unit)? = null,
    /** 曲の左にトラック番号を出す（アルバム画面）。 */
    showTrackNumbers: Boolean = false,
) {
    val context = LocalContext.current
    val c = context.container
    val entries = rememberEntries(view, level, path, settings)
    val style = view.styleAt(level)
    val join = settings.display.joinSeparator
    val playerState by c.player.state.collectAsStateWithLifecycle()
    val currentId = playerState.current?.mediaId
    var menuFor by remember { mutableStateOf<BrowseEntry?>(null) }
    val inPlaylist = path.lastOrNull()?.kind == io.github.oke1943.azsaplayer.settings.ViewKind.PLAYLIST

    if (entries == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (entries.isEmpty()) {
        EmptyMessage(stringResource(emptyMessage(view, level, c.library.loaded.value)))
        return
    }

    val trackIds = remember(entries) { entries.filterIsInstance<TrackEntry>().map { it.track.id } }
    val onClick: (BrowseEntry) -> Unit = { e ->
        when (e) {
            is GroupEntry -> onOpenGroup(e)
            is TrackEntry -> {
                if (!e.track.e.playable) {
                    Toast.makeText(context, context.getString(R.string.unplayable_format, e.track.e.codec), Toast.LENGTH_SHORT).show()
                } else {
                    val playable = entries.filterIsInstance<TrackEntry>().filter { it.track.e.playable }
                    c.player.play(playable.map { it.track.id }, playable.indexOfFirst { it === e }.coerceAtLeast(0))
                }
            }
        }
    }

    val indexLetters = remember(entries, style, settings.library.ignoreArticles) {
        if (!style.showIndex || entries.size < 25 || style.sortBy !in setOf(SortBy.SORT_KEY, SortBy.NAME)) null
        else {
            val rules = SortRules(settings.library.ignoreArticles)
            entries.map { rules.indexChar(if (style.sortBy == SortBy.NAME) it.title else it.sortText) }
        }
    }

    val headerCount = if (header != null) 1 else 0
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (style.layout == Layout.GRID) {
                val gridState = rememberLazyGridState()
                val scope = rememberCoroutineScope()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(style.gridColumns.coerceIn(1, 6)),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 8.dp, end = 8.dp, top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (header != null) {
                        item(key = "header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
                    }
                    itemsIndexed(entries, key = { _, e -> e.key }) { _, e ->
                        GridCell(e, style, join, currentId, onClick = { onClick(e) }, onLongClick = { menuFor = e })
                    }
                }
                if (indexLetters != null) {
                    FastScroller(indexLetters, contentPadding.calculateBottomPadding()) { i -> scope.launch { gridState.scrollToItem(i + headerCount) } }
                }
            } else {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                    if (header != null) item(key = "header") { header() }
                    itemsIndexed(entries, key = { _, e -> e.key }) { _, e ->
                        val number = if (showTrackNumbers && e is TrackEntry) e.track.e.trackNo else null
                        ListRow(e, style, join, currentId, number, showTrackNumbers, onClick = { onClick(e) }, onLongClick = { menuFor = e })
                    }
                }
                if (indexLetters != null) {
                    FastScroller(indexLetters, contentPadding.calculateBottomPadding()) { i -> scope.launch { listState.scrollToItem(i + headerCount) } }
                }
            }
        }
    }

    menuFor?.let { e ->
        EntryMenu(
            entry = e,
            allTrackIds = trackIds,
            inPlaylist = if (inPlaylist) path.last().key.toLongOrNull() else null,
            onDismiss = { menuFor = null },
            onOpenTrackInfo = onOpenTrackInfo,
        )
    }
}

private fun emptyMessage(view: ViewConfig, level: Int, loaded: Boolean): Int = when {
    !loaded -> R.string.loading
    view.levels.getOrNull(level) == io.github.oke1943.azsaplayer.settings.ViewKind.PLAYLIST ->
        R.string.empty_playlists
    else -> R.string.empty_library
}

private fun line1(e: BrowseEntry, style: ListStyle, join: String, labels: BrowseLabels): String =
    if (style.line1.isBlank()) e.title else Template.render(style.line1, e, join, labels).ifEmpty { e.title }

private fun line2(e: BrowseEntry, style: ListStyle, join: String, labels: BrowseLabels): String? =
    if (style.line2.isBlank()) null else Template.render(style.line2, e, join, labels).ifEmpty { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    e: BrowseEntry,
    style: ListStyle,
    join: String,
    currentId: String?,
    number: Int?,
    numberColumn: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isCurrent = e is TrackEntry && e.track.id.toString() == currentId
    val unplayable = e is TrackEntry && !e.track.e.playable
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = if (style.showArtwork) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (numberColumn) {
            Text(
                number?.toString() ?: "", Modifier.width(36.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (style.showArtwork) {
            val track = when (e) {
                is GroupEntry -> e.artTrack
                is TrackEntry -> e.track.album.artTrack
            }
            ArtworkImage(track?.e, style.artworkSize.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                line1(e, style, join, LocalBrowseLabels.current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    unplayable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            )
            line2(e, style, join, LocalBrowseLabels.current)?.let {
                Text(
                    it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (unplayable) {
            Text(
                stringResource(R.string.unplayable), Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    e: BrowseEntry,
    style: ListStyle,
    join: String,
    currentId: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isCurrent = e is TrackEntry && e.track.id.toString() == currentId
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        if (style.showArtwork) {
            var widthPx by remember { mutableStateOf(0) }
            val track = when (e) {
                is GroupEntry -> e.artTrack
                is TrackEntry -> e.track.album.artTrack
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1f).onSizeChanged { widthPx = it.width }) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val sizeDp = with(density) { widthPx.toDp() }
                if (widthPx > 0) ArtworkImage(track?.e, sizeDp, corner = 6.dp)
            }
        }
        Text(
            line1(e, style, join, LocalBrowseLabels.current), Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        line2(e, style, join, LocalBrowseLabels.current)?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val LATIN_LABELS = ('A'..'Z').map { it.toString() }
private val KANA_LABELS = listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ")

/** 索引に並べる文字：「#」とA〜Zは常に、かなの行と「漢」は一覧にあるときだけ（並び順と同じ順）。 */
internal fun indexLabels(present: Collection<String>): List<String> =
    listOf("#") + LATIN_LABELS + KANA_LABELS.filter { it in present } + listOf("漢").filter { it in present }

/** 索引の文字 → 一覧の位置。その文字の項目がなければ、索引上で最も近い文字の先頭へ。 */
internal fun jumpTarget(label: String, labels: List<String>, letters: List<String>): Int {
    val first = HashMap<String, Int>()
    letters.forEachIndexed { i, l -> first.putIfAbsent(l, i) }
    first[label]?.let { return it }
    val pos = labels.indexOf(label)
    val nearest = first.keys.minByOrNull { k ->
        val d = labels.indexOf(k) - pos
        if (d >= 0) d * 2 else -d * 2 + 1 // 同じ距離なら後ろ側を優先
    } ?: return 0
    return first.getValue(nearest)
}

/**
 * 早送りスクロール（6.3）。右端に索引を常に全部表示し、画面の高さに合わせて文字を縮める。
 * なぞっている間は指の横に現在の文字を大きく表示する。
 */
@Composable
private fun BoxScope.FastScroller(letters: List<String>, bottomPadding: androidx.compose.ui.unit.Dp, onJump: (Int) -> Unit) {
    val labels = remember(letters) { indexLabels(letters.toSet()) }
    var active by remember { mutableStateOf<String?>(null) }
    var touchY by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(1) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    fun pick(y: Float) {
        touchY = y
        val i = ((y / heightPx) * labels.size).toInt().coerceIn(0, labels.size - 1)
        val l = labels[i]
        if (l != active) {
            active = l
            onJump(jumpTarget(l, labels, letters))
        }
    }

    BoxWithConstraints(
        Modifier
            .align(Alignment.TopEnd)
            // ミニプレーヤーの裏に隠れないよう、一覧の下余白の分だけ縮める
            .padding(bottom = bottomPadding)
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(labels) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    active = null
                }
            },
    ) {
        val cell = maxHeight / labels.size
        val fontSize = with(density) { minOf(12.sp.toPx(), cell.toPx() * 0.8f).toSp() }
        Column(Modifier.fillMaxHeight()) {
            labels.forEach { l ->
                // 高さは均等割り（固定の高さを積み上げると丸め誤差で末尾がはみ出す）
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        l,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        color = if (l == active) MaterialTheme.colorScheme.primary
                        else if (l in letters) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        fontWeight = if (l == active) FontWeight.Bold else null,
                    )
                }
            }
        }
    }

    // 指の横に出す現在の文字
    active?.let { l ->
        val bubble = 64.dp
        val y = with(density) { (touchY.toDp() - bubble / 2).coerceAtLeast(0.dp) }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 40.dp)
                .offset(y = y)
                .size(bubble)
                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(l, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** 一覧で使う言語依存の文言。AppRootで表示言語に合わせて用意する。 */
val LocalBrowseLabels = androidx.compose.runtime.staticCompositionLocalOf { BrowseLabels() }

@Composable
fun rememberBrowseLabels(): BrowseLabels {
    val res = LocalContext.current.resources
    return remember(res) {
        BrowseLabels(
            noAlbum = res.getString(R.string.browse_no_album),
            noYear = res.getString(R.string.browse_no_year),
            decade = { res.getString(R.string.browse_decade, it) },
            tracks = { res.getString(R.string.tracks_count, it) },
        )
    }
}
