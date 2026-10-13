package io.github.oke1943.azsaplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

private data class QueueRow(val uid: String, val item: MediaItem)

/** 再生キュー（8章）：表示・ドラッグで並べ替え・削除・タップでその曲へ。 */
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val state by c.player.state.collectAsStateWithLifecycle()
    var rows by remember { mutableStateOf(emptyList<QueueRow>()) }
    var dragFrom by remember { mutableStateOf(-1) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(state.queue) {
        if (dragFrom >= 0) return@LaunchedEffect
        val seen = HashMap<String, Int>()
        rows = state.queue.map { m ->
            val n = seen.merge(m.mediaId, 1, Int::plus)!!
            QueueRow("${m.mediaId}#$n", m)
        }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LaunchedEffect(Unit) { if (state.currentIndex > 2) listState.scrollToItem(state.currentIndex - 2) }

    SettingsScaffold(
        title = stringResource(R.string.queue_title, state.queue.size),
        onBack = onBack,
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.clear_queue)) }, onClick = { menu = false; c.player.clearQueue() })
            }
        },
    ) {
        if (rows.isEmpty()) {
            EmptyMessage(stringResource(R.string.queue_empty))
            return@SettingsScaffold
        }
        LazyColumn(state = listState) {
            itemsIndexed(rows, key = { _, r -> r.uid }) { index, row ->
                ReorderableItem(reorder, key = row.uid) { dragging ->
                    val isCurrent = index == state.currentIndex && dragFrom < 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
                            .clickable { c.player.skipTo(index) }
                            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                onDragStarted = { dragFrom = index },
                                onDragStopped = {
                                    val to = rows.indexOfFirst { it.uid == row.uid }
                                    if (dragFrom >= 0 && to >= 0 && to != dragFrom) c.player.move(dragFrom, to)
                                    dragFrom = -1
                                },
                            ),
                        ) { Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder)) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.item.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                            )
                            Text(
                                row.item.mediaMetadata.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { c.player.remove(index) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_from_queue)) }
                    }
                }
            }
        }
    }
}
