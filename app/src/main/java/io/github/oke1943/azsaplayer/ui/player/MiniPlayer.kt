package io.github.oke1943.azsaplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/** 画面下部のミニプレーヤー。何も再生していないときは表示しない。 */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val c = LocalContext.current.container
    val state by c.player.state.collectAsStateWithLifecycle()
    val position by c.player.position.collectAsStateWithLifecycle()
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    val item = state.current ?: return
    val track = item.mediaId.toLongOrNull()?.let { snapshot.tracksById[it] }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(track?.album?.artTrack?.e, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        item.mediaMetadata.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { c.player.togglePlay() }) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play))
                }
                IconButton(onClick = { c.player.next() }) { Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_track)) }
            }
        }
    }
}
