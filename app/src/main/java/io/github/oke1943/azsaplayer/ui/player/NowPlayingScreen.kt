package io.github.oke1943.azsaplayer.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.Template
import io.github.oke1943.azsaplayer.playback.MediaItems
import io.github.oke1943.azsaplayer.playback.ShuffleMode
import io.github.oke1943.azsaplayer.settings.NowPlayingSettings
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/** 再生中画面（6.4）。各要素は設定で表示・非表示を切り替えられる。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    np: NowPlayingSettings,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    /** アルバム画面へ（アルバムキー, アルバム名）。 */
    onOpenAlbum: (String, String) -> Unit,
    /** アーティスト名のタップ：その曲のアルバムアーティストのアルバム一覧へ。 */
    onOpenAlbumArtist: (io.github.oke1943.azsaplayer.library.LibPerson) -> Unit,
) {
    val c = LocalContext.current.container
    val state by c.player.state.collectAsStateWithLifecycle()
    val position by c.player.position.collectAsStateWithLifecycle()
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    val item = state.current
    val track = item?.mediaId?.toLongOrNull()?.let { snapshot.tracksById[it] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.close)) } },
                actions = { IconButton(onClick = onOpenQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.queue)) } },
            )
        },
    ) { padding ->
        if (item == null) {
            Box(Modifier.padding(padding)) { EmptyMessage(stringResource(R.string.nothing_playing)) }
            return@Scaffold
        }
        // スクロールさせない：アートワークは「文字・シークバー・ボタンを置いた残りの高さ」と幅の小さい方に合わせる
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (np.showArtwork) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    val albumKey = MediaItems.albumKey(item)
                    ArtworkImage(
                        track?.album?.artTrack?.e, side, corner = 8.dp,
                        modifier = Modifier.clickable(enabled = albumKey != null) {
                            onOpenAlbum(albumKey!!, item.mediaMetadata.albumTitle?.toString() ?: "")
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            val md = item.mediaMetadata
            if (np.showTitle) {
                // 1行で表示し、収まらないときは一方向に流す
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        md.title?.toString() ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500, velocity = MARQUEE_VELOCITY),
                    )
                }
            }
            if (np.showArtist) {
                // 複数アーティストは全員表示し、幅が足りなければ末尾を省略（3.4）
                val albumArtists = track?.effectiveAlbumArtists.orEmpty()
                var chooseArtist by remember { mutableStateOf(false) }
                Box {
                    MarqueeLine(
                        md.artist?.toString() ?: "", MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.primary, top = 4.dp,
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
            if (np.showAlbum) {
                MarqueeLine(md.albumTitle?.toString() ?: "", MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant, top = 2.dp)
            }
            val info = buildList {
                if (np.showFormat) md.extras?.getString(MediaItems.FORMAT)?.let { add(it) }
                if (np.showReplayGain && state.rgLabel.isNotEmpty()) add(state.rgLabel)
            }
            if (info.isNotEmpty()) {
                Text(info.joinToString("　"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
            if (np.showSeekBar) {
                Spacer(Modifier.height(16.dp))
                var dragging by remember { mutableStateOf<Float?>(null) }
                val dur = state.durationMs.coerceAtLeast(1)
                // つまみは小さな「○」、溝は細く（ver.5）
                Slider(
                    value = dragging ?: (position.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { c.player.seekTo((it * dur).toLong()) }; dragging = null },
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
                    Text(Template.formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (np.showControls) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { c.player.cycleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle, contentDescription = stringResource(state.shuffle.labelRes),
                            tint = if (state.shuffle == ShuffleMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { c.player.previous() }) { Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous_track), Modifier.size(32.dp)) }
                    FilledIconButton(onClick = { c.player.togglePlay() }, modifier = Modifier.size(64.dp)) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play), Modifier.size(36.dp))
                    }
                    IconButton(onClick = { c.player.next() }) { Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_track), Modifier.size(32.dp)) }
                    IconButton(onClick = { c.player.cycleRepeat() }) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = stringResource(R.string.repeat),
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (state.shuffle != ShuffleMode.OFF) {
                    Text(stringResource(state.shuffle.labelRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!np.showArtwork) Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 自動スクロールの速さ（Composeの標準 30dp/秒 の約1.3倍）。 */
private val MARQUEE_VELOCITY = 39.dp

/** 1行で表示し、収まらないときは一方向に流す。 */
@Composable
private fun MarqueeLine(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    top: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxWidth().padding(top = top).then(modifier), contentAlignment = Alignment.Center) {
        Text(
            text, style = style, color = color, maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500, velocity = MARQUEE_VELOCITY),
        )
    }
}
