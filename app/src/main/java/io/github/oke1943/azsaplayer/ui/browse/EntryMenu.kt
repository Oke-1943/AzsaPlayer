package io.github.oke1943.azsaplayer.ui.browse

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.BrowseEntry
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.PlaylistInfo
import io.github.oke1943.azsaplayer.library.TrackEntry
import io.github.oke1943.azsaplayer.playback.ShuffleMode
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.ui.common.TextInputDialog
import kotlinx.coroutines.launch
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/** 長押しメニュー。主画面に追加ボタンを常設しない代わりに、操作はここに集める（1章 原則2）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryMenu(
    entry: BrowseEntry,
    allTrackIds: List<Long>,
    inPlaylist: Long?,
    onDismiss: () -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val ids: List<Long> = when (entry) {
        is GroupEntry -> entry.tracks.filter { it.e.playable }.sortedWith(
            compareBy({ it.album.titleSort ?: it.album.title }, { it.e.discNo ?: 1 }, { it.e.trackNo ?: Int.MAX_VALUE }),
        ).let { sorted -> if (entry.kind == ViewKind.PLAYLIST) entry.tracks.filter { it.e.playable } else sorted }.map { it.id }
        is TrackEntry -> listOf(entry.track.id)
    }
    var addToPlaylist by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isPlaylistGroup = entry is GroupEntry && entry.kind == ViewKind.PLAYLIST

    fun done() = onDismiss()

    if (!addToPlaylist && !rename && !confirmDelete) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    entry.title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                MenuItem(stringResource(R.string.menu_play)) { c.player.play(ids); done() }
                if (entry is GroupEntry) {
                    MenuItem(stringResource(R.string.menu_shuffle)) { c.player.play(ids, 0, ShuffleMode.TRACKS); done() }
                }
                MenuItem(stringResource(R.string.menu_play_next)) { c.player.playNext(ids); toast(context, context.getString(R.string.toast_play_next)); done() }
                MenuItem(stringResource(R.string.menu_add_to_queue)) { c.player.addToQueue(ids); toast(context, context.getString(R.string.toast_added_to_queue)); done() }
                MenuItem(stringResource(R.string.menu_add_to_playlist)) { addToPlaylist = true }
                if (entry is TrackEntry) {
                    if (inPlaylist != null) {
                        MenuItem(stringResource(R.string.menu_remove_from_playlist)) {
                            scope.launch {
                                val list = c.playlists.trackIds(inPlaylist).toMutableList()
                                if (entry.position in list.indices) list.removeAt(entry.position)
                                c.playlists.setTracks(inPlaylist, list)
                            }
                            done()
                        }
                    }
                    MenuItem(stringResource(R.string.menu_track_info)) { onOpenTrackInfo(entry.track.id); done() }
                }
                if (isPlaylistGroup) {
                    MenuItem(stringResource(R.string.menu_rename)) { rename = true }
                    MenuItem(stringResource(R.string.menu_delete_playlist)) { confirmDelete = true }
                }
            }
        }
    }

    if (addToPlaylist) {
        AddToPlaylistDialog(ids, onDone = onDismiss)
    }
    if (rename && entry is GroupEntry) {
        TextInputDialog(stringResource(R.string.playlist_name), entry.title, onDismiss = onDismiss) { name ->
            if (name.isNotBlank()) scope.launch { c.playlists.rename(entry.key.toLong(), name.trim()) }
            onDismiss()
        }
    }
    if (confirmDelete && entry is GroupEntry) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.menu_delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_message, entry.title)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { c.playlists.delete(entry.key.toLong()) }; onDismiss() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    ListItem(modifier = Modifier.clickable(onClick = onClick), headlineContent = { Text(text) })
}

private fun toast(context: android.content.Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
fun AddToPlaylistDialog(ids: List<Long>, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val playlists by c.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList<PlaylistInfo>())
    var newName by remember { mutableStateOf(false) }

    if (newName) {
        TextInputDialog(stringResource(R.string.new_playlist), "", onDismiss = onDone) { name ->
            if (name.isNotBlank()) scope.launch {
                c.playlists.create(name.trim(), ids)
                toast(context, context.getString(R.string.playlist_created, name.trim()))
            }
            onDone()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.menu_add_to_playlist).trimEnd('…')) },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        modifier = Modifier.clickable { newName = true },
                        headlineContent = { Text(stringResource(R.string.new_playlist_item), color = MaterialTheme.colorScheme.primary) },
                    )
                }
                items(playlists, key = { it.id }) { p ->
                    ListItem(
                        modifier = Modifier.clickable {
                            scope.launch {
                                c.playlists.append(p.id, ids)
                                toast(context, context.getString(R.string.playlist_added, p.name))
                            }
                            onDone()
                        },
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text(stringResource(R.string.tracks_count, p.trackIds.size)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.close)) } },
    )
}
