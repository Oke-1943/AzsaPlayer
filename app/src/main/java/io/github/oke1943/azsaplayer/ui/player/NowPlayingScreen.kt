package io.github.oke1943.azsaplayer.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.oke1943.azsaplayer.R
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.LibPerson
import io.github.oke1943.azsaplayer.library.Template
import io.github.oke1943.azsaplayer.playback.MediaItems
import io.github.oke1943.azsaplayer.playback.ShuffleMode
import io.github.oke1943.azsaplayer.settings.NowPlayingSettings
import io.github.oke1943.azsaplayer.settings.NpItem
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import kotlinx.coroutines.launch

/**
 * 再生中画面（6.4）。表示する要素とその順は設定（再生画面）で決める。
 * 下へスワイプすると、再生中の曲のアルバムの曲リストへ戻る。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    np: NowPlayingSettings,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    /** アルバム画面へ（アルバムキー, アルバム名）。 */
    onOpenAlbum: (String, String) -> Unit,
    /** アーティスト名のタップ：その曲のアルバムアーティストのアルバム一覧へ。 */
    onOpenAlbumArtist: (LibPerson) -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
) {
    val c = LocalContext.current.container
    val state by c.player.state.collectAsStateWithLifecycle()
    val position by c.player.position.collectAsStateWithLifecycle()
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    val item = state.current
    val track = item?.mediaId?.toLongOrNull()?.let { snapshot.tracksById[it] }
    val scope = rememberCoroutineScope()
    // 下へのスワイプで閉じる：指に合わせて動かし、離したら滑らかに下へ抜けるか元に戻る
    var dragY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var pageHeight by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var leaving by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val flick = with(LocalDensity.current) { 500.dp.toPx() } // 素早く払ったときは短くても閉じる（px/秒）
    var menu by remember { mutableStateOf(false) }

    if (menu && track != null && item != null) {
        val albumKey = MediaItems.albumKey(item)
        val albumTitle = item.mediaMetadata.albumTitle?.toString() ?: ""
        io.github.oke1943.azsaplayer.ui.browse.EntryMenu(
            entry = io.github.oke1943.azsaplayer.library.TrackEntry(track, 0),
            allTrackIds = listOf(track.id),
            inPlaylist = null,
            onDismiss = { menu = false },
            onOpenTrackInfo = onOpenTrackInfo,
            nowPlaying = true,
            onOpenAlbum = albumKey?.let { k -> { onOpenAlbum(k, albumTitle) } },
            onOpenArtist = track.effectiveAlbumArtists.map { p -> p.name to { onOpenAlbumArtist(p) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.close)) } },
                actions = {
                    IconButton(onClick = onOpenQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.queue)) }
                    IconButton(onClick = { menu = true }, enabled = track != null) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more)) }
                },
            )
        },
    ) { padding ->
        if (item == null) {
            Box(Modifier.padding(padding)) { EmptyMessage(stringResource(R.string.nothing_playing)) }
            return@Scaffold
        }
        val md = item.mediaMetadata
        val albumKey = MediaItems.albumKey(item)
        val albumTitle = md.albumTitle?.toString() ?: ""
        // スクロールさせない：アートワークは「ほかの要素を置いた残りの高さ」と幅の小さい方に合わせる
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .onSizeChanged { pageHeight = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    translationY = dragY
                    alpha = 1f - (dragY / pageHeight * 1.5f).coerceIn(0f, 1f)
                }
                .draggable(
                    state = androidx.compose.foundation.gestures.rememberDraggableState { dy -> if (!leaving) dragY = (dragY + dy).coerceAtLeast(0f) },
                    orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                    onDragStopped = { velocity ->
                        // アニメーションは画面のスコープで動かす（ジェスチャーの終了で止められないように）
                        if (leaving) return@draggable
                        if (dragY > threshold || velocity > flick) {
                            // 下へのスワイプ：左上の下矢印と同じく、前の画面に戻る。画面はそのまま下へ抜ける
                            leaving = true
                            scope.launch {
                                androidx.compose.animation.core.animate(
                                    dragY, pageHeight,
                                    animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                                ) { v, _ -> dragY = v }
                            }
                            onBack()
                        } else {
                            scope.launch {
                                androidx.compose.animation.core.animate(dragY, 0f, velocity,
                                    androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                                ) { v, _ -> dragY = v }
                            }
                        }
                    },
                )
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hasArtwork = NpItem.ARTWORK in np.items
            if (!hasArtwork) Spacer(Modifier.weight(1f))
            np.items.forEach { element ->
                when (element) {
                    NpItem.ARTWORK -> {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val side = minOf(maxWidth, maxHeight)
                            ArtworkImage(
                                track?.album?.artTrack?.e, side, corner = 8.dp,
                                modifier = Modifier.clickable(enabled = albumKey != null) { onOpenAlbum(albumKey!!, albumTitle) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    NpItem.TITLE -> Line(md.title?.toString() ?: "", MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface, np.marquee)
                    NpItem.ARTIST -> {
                        // 複数アーティストは全員表示（3.4）。色はアルバム名と同じ
                        val albumArtists = track?.effectiveAlbumArtists.orEmpty()
                        var chooseArtist by remember { mutableStateOf(false) }
                        Box {
                            Line(
                                md.artist?.toString() ?: "", MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.onSurfaceVariant, np.marquee,
                                top = 4.dp,
                                modifier = Modifier.clickable(enabled = albumArtists.isNotEmpty()) {
                                    if (albumArtists.size == 1) onOpenAlbumArtist(albumArtists.first()) else chooseArtist = true
                                },
                            )
                            // 共同名義などアルバムアーティストが複数いるときは選んでもらう
                            androidx.compose.material3.DropdownMenu(expanded = chooseArtist, onDismissRequest = { chooseArtist = false }) {
                                albumArtists.forEach { p ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(p.name) }, onClick = { chooseArtist = false; onOpenAlbumArtist(p) })
                                }
                            }
                        }
                    }
                    NpItem.ALBUM -> Line(albumTitle, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant, np.marquee, top = 2.dp)
                    NpItem.FORMAT -> md.extras?.getString(MediaItems.FORMAT)?.let { SmallInfo(it) }
                    NpItem.REPLAYGAIN -> if (state.rgLabel.isNotEmpty()) SmallInfo(state.rgLabel)
                    NpItem.SEEKBAR -> SeekBar(position, state.durationMs) { c.player.seekTo(it) }
                    NpItem.CONTROLS -> Controls(
                        isPlaying = state.isPlaying,
                        shuffle = state.shuffle,
                        repeatMode = state.repeatMode,
                        onShuffle = { c.player.cycleShuffle() },
                        onPrevious = { c.player.previous() },
                        onPlayPause = { c.player.togglePlay() },
                        onNext = { c.player.next() },
                        onRepeat = { c.player.cycleRepeat() },
                    )
                }
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
            if (!hasArtwork) Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SmallInfo(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.SeekBar(position: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    Spacer(Modifier.height(16.dp))
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = durationMs.coerceAtLeast(1)
    // つまみは小さな「○」、溝は細く（ver.5）
    Slider(
        value = dragging ?: (position.toFloat() / dur).coerceIn(0f, 1f),
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { onSeek((it * dur).toLong()) }; dragging = null },
        thumb = {
            Box(
                Modifier.size(if (dragging != null) 20.dp else 14.dp)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
            )
        },
        track = { st ->
            androidx.compose.material3.SliderDefaults.Track(
                sliderState = st,
                modifier = Modifier.height(4.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(Template.formatDuration(dragging?.let { (it * dur).toLong() } ?: position), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(Template.formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    shuffle: ShuffleMode,
    repeatMode: Int,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        // タップごとに オフ → 曲単位 → アルバム単位 → オフ。アルバム単位は円盤の印を付けて区別する
        IconButton(onClick = onShuffle) {
            val tint = if (shuffle == ShuffleMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            Box {
                Icon(Icons.Filled.Shuffle, contentDescription = stringResource(shuffle.labelRes), tint = tint)
                if (shuffle == ShuffleMode.ALBUMS) {
                    Icon(
                        Icons.Filled.Album, contentDescription = null, tint = tint,
                        modifier = Modifier.size(11.dp).align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape),
                    )
                }
            }
        }
        IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous_track), Modifier.size(32.dp)) }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play), Modifier.size(36.dp))
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_track), Modifier.size(32.dp)) }
        IconButton(onClick = onRepeat) {
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.repeat),
                tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 自動スクロールの速さ（Composeの標準 30dp/秒 の約1.3倍）。 */
private val MARQUEE_VELOCITY = 39.dp

/** 1行で表示する。収まらないときは、設定に応じて一方向に流すか末尾を省略する。 */
@Composable
private fun Line(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    marquee: Boolean,
    top: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxWidth().padding(top = top).then(modifier), contentAlignment = Alignment.Center) {
        if (marquee) {
            Text(
                text, style = style, color = color, maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500, velocity = MARQUEE_VELOCITY),
            )
        } else {
            Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
