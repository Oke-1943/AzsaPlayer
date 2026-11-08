package io.github.oke1943.azsaplayer.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.R
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.BrowseEngine
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.PlaylistInfo
import io.github.oke1943.azsaplayer.library.Selection
import io.github.oke1943.azsaplayer.library.SortRules
import io.github.oke1943.azsaplayer.settings.ARTWORK_SIZE
import io.github.oke1943.azsaplayer.settings.AlbumOrder
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.SectionHeader
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * アーティストごとのアルバムのソート順。
 * 「既定」は全体の設定（ホーム > アーティストのアルバムの並び順）に従う。「手動」はドラッグで並べた順。
 */
@Composable
fun AlbumOrderScreen(settings: AppSettings, view: ViewConfig, level: Int, path: List<Selection>, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    val playlists by c.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList<PlaylistInfo>())
    val d = settings.display
    val engine = remember(snapshot, playlists, d.albumOrder, d.artistAlbumOrder, d.customAlbumOrder) {
        BrowseEngine(
            snapshot, SortRules(settings.library.ignoreArticles), playlists, c.library::customValues,
            albumOrder = d.albumOrder, artistAlbumOrder = d.artistAlbumOrder, customAlbumOrder = d.customAlbumOrder,
        )
    }
    val personKey = remember(engine, path) { engine.personKey(path.last()) }
    /** null = 既定（全体の設定に従う）。 */
    var choice by remember { mutableStateOf(d.artistAlbumOrder[personKey]) }
    var albums by remember { mutableStateOf<List<GroupEntry>>(emptyList()) }
    LaunchedEffect(engine) { albums = engine.entries(view, level, path).filterIsInstance<GroupEntry>() }

    val listState = rememberLazyListState()
    val options: List<AlbumOrder?> = listOf<AlbumOrder?>(null) + AlbumOrder.entries
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        // 先頭は見出し・選択肢・余白・見出し、その後ろがアルバム
        val offset = options.size + 3
        val f = from.index - offset
        val t = to.index - offset
        if (f in albums.indices && t in albums.indices) albums = albums.toMutableList().apply { add(t, removeAt(f)) }
    }

    fun save() = scope.launch {
        c.settings.update { s ->
            val dd = s.display
            s.copy(
                display = dd.copy(
                    artistAlbumOrder = if (choice == null) dd.artistAlbumOrder - personKey else dd.artistAlbumOrder + (personKey to choice!!),
                    customAlbumOrder = if (choice == AlbumOrder.MANUAL) dd.customAlbumOrder + (personKey to albums.map { it.key })
                    else dd.customAlbumOrder - personKey,
                ),
            )
        }
        onBack()
    }

    SettingsScaffold(
        title = path.last().title,
        onBack = onBack,
        actions = { TextButton(onClick = { save() }) { Text(stringResource(R.string.save)) } },
    ) {
        LazyColumn(state = listState) {
            item(key = "h:order") { SectionHeader(stringResource(R.string.album_order)) }
            items(options, key = { it?.name ?: "default" }) { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { choice = o }.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice == o, onClick = { choice = o })
                    Text(
                        if (o == null) stringResource(R.string.album_order_follow_default, stringResource(d.albumOrder.labelRes))
                        else stringResource(o.labelRes),
                    )
                }
            }
            item(key = "spacer") { Spacer(Modifier.padding(4.dp)) }
            if (choice == AlbumOrder.MANUAL) {
                item(key = "h:albums") { SectionHeader(stringResource(R.string.kind_album)) }
                items(albums, key = { it.key }) { g ->
                    ReorderableItem(reorder, key = g.key) { dragging ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder))
                            }
                            ArtworkImage(g.artTrack?.e, ARTWORK_SIZE.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                g.year?.let {
                                    Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
