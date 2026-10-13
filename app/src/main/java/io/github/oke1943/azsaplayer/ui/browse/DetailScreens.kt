package io.github.oke1943.azsaplayer.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.data.TrackTagEntity
import io.github.oke1943.azsaplayer.library.Template
import io.github.oke1943.azsaplayer.playback.MediaItems
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/** 曲の情報（解釈結果と、ファイルに書かれた全タグ）。 */
@Composable
fun TrackInfoScreen(trackId: Long, join: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    val t = snapshot.tracksById[trackId]
    val tags by produceState(emptyList<TrackTagEntity>(), trackId) { value = c.library.tagsOfTrack(trackId) }

    SettingsScaffold(title = stringResource(R.string.track_info), onBack = onBack) {
        if (t == null) {
            EmptyMessage(stringResource(R.string.track_not_found))
            return@SettingsScaffold
        }
        SelectionContainer {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtworkImage(t.album.artTrack.e, 96.dp)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(t.artists.joinToString(join) { it.name }, color = MaterialTheme.colorScheme.primary)
                        Text(t.e.album, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Info(stringResource(R.string.kind_artist), t.artists.joinToString("\n") { context.getString(R.string.sort_value, it.name, it.sortKey) })
                Info(stringResource(R.string.kind_album_artist), t.effectiveAlbumArtists.joinToString("\n") { context.getString(R.string.sort_value, it.name, it.sortKey) })
                if (t.composers.isNotEmpty()) Info(stringResource(R.string.kind_composer), t.composers.joinToString("\n") { it.name })
                if (t.genres.isNotEmpty()) Info(stringResource(R.string.kind_genre), t.genres.joinToString(join))
                Info(stringResource(R.string.kind_year), t.e.year?.toString() ?: "")
                Info(stringResource(R.string.info_track_disc), "${t.e.trackNo ?: "-"} / ${t.e.discNo ?: "-"}")
                Info(stringResource(R.string.info_length), Template.formatDuration(t.e.durationMs))
                Info(stringResource(R.string.info_format), MediaItems.formatText(t) + if (t.e.playable) "" else stringResource(R.string.info_unplayable_suffix))
                val none = stringResource(R.string.none)
                Info(
                    stringResource(R.string.set_rg),
                    stringResource(
                        R.string.info_rg_values,
                        t.e.rgTrackGain?.let { "%.2f dB".format(it) } ?: none,
                        t.e.rgTrackPeak?.let { "%.6f".format(it) } ?: none,
                        t.e.rgAlbumGain?.let { "%.2f dB".format(it) } ?: none,
                        t.e.rgAlbumPeak?.let { "%.6f".format(it) } ?: none,
                    ),
                )
                Info(stringResource(R.string.info_file), t.e.path)
                Text(stringResource(R.string.info_file_tags), Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                tags.groupBy { it.key }.forEach { (key, rows) ->
                    Text(key, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    rows.sortedBy { it.position }.forEach {
                        Text("[${it.position}] ‹${it.value}›", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
    }
}

/** プレイリストの編集（並べ替え・削除）。 */
@Composable
fun PlaylistEditScreen(playlistId: Long, join: String, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    var rows by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        rows = c.playlists.trackIds(playlistId).mapIndexed { i, id -> "$i:$id" to id }
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows?.toMutableList()?.apply { add(to.index, removeAt(from.index)) }
        dirty = true
    }

    fun save() {
        val ids = rows?.map { it.second } ?: return
        scope.launch { c.playlists.setTracks(playlistId, ids) }
        dirty = false
    }

    SettingsScaffold(
        title = stringResource(R.string.playlist_edit),
        onBack = { if (dirty) save(); onBack() },
        actions = { TextButton(onClick = { save(); onBack() }) { Text(stringResource(R.string.save)) } },
    ) {
        val list = rows ?: return@SettingsScaffold
        if (list.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_tracks))
            return@SettingsScaffold
        }
        LazyColumn(state = listState) {
            itemsIndexed(list, key = { _, r -> r.first }) { index, (key, id) ->
                ReorderableItem(reorder, key = key) { dragging ->
                    val t = snapshot.tracksById[id]
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}, modifier = Modifier.draggableHandle()) { Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder)) }
                        Column(Modifier.weight(1f)) {
                            Text(t?.title ?: stringResource(R.string.not_in_library), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                t?.artists?.joinToString(join) { it.name } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            rows = list.toMutableList().apply { removeAt(index) }
                            dirty = true
                        }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove)) }
                    }
                }
            }
        }
    }
}
