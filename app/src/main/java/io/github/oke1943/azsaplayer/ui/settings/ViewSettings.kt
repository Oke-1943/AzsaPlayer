package io.github.oke1943.azsaplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.Layout
import io.github.oke1943.azsaplayer.settings.ListStyle
import io.github.oke1943.azsaplayer.settings.SortBy
import io.github.oke1943.azsaplayer.settings.AlbumOrder
import io.github.oke1943.azsaplayer.settings.NpItem
import io.github.oke1943.azsaplayer.settings.StartTab
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.ui.common.ChoiceRow
import io.github.oke1943.azsaplayer.ui.common.ClickRow
import io.github.oke1943.azsaplayer.ui.common.SectionHeader
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import io.github.oke1943.azsaplayer.ui.common.SliderRow
import io.github.oke1943.azsaplayer.ui.common.SwitchRow
import io.github.oke1943.azsaplayer.ui.common.TextInputDialog
import io.github.oke1943.azsaplayer.ui.common.TextRow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.oke1943.azsaplayer.ui.common.displayTitle
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/**
 * ホームの設定：タブ（ドラッグで並べ替え・×で外す・＋で追加）と、起動時のタブ・検索ボタン・アルバムの並び順。
 * 再生画面の設定（[NowPlayingItemsScreen]）も同じ操作方式にそろえている。
 */
@Composable
fun HomeViewsScreen(settings: AppSettings, onBack: () -> Unit, onEditView: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val update = rememberUpdater()
    val d = settings.display
    var views by remember { mutableStateOf(d.homeViews) }
    LaunchedEffect(d.homeViews) { views = d.homeViews }
    var adding by remember { mutableStateOf(false) }
    var customTagFor by remember { mutableStateOf(false) }
    var pendingOrder by remember { mutableStateOf<AlbumOrder?>(null) }

    fun save(list: List<ViewConfig>) {
        views = list
        scope.launch { c.settings.update { it.copy(display = it.display.copy(homeViews = list)) } }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        // 先頭の見出しの分だけ添字がずれる
        val f = from.index - 1
        val t = to.index - 1
        if (f in views.indices && t in views.indices) views = views.toMutableList().apply { add(t, removeAt(f)) }
    }

    SettingsScaffold(title = stringResource(R.string.set_home), onBack = onBack) {
        LazyColumn(state = listState) {
            item(key = "h:tabs") { SectionHeader(stringResource(R.string.set_home_views)) }
            items(views, key = { it.id }) { v ->
                ReorderableItem(reorder, key = v.id) { dragging ->
                    OrderRow(
                        title = v.displayTitle(),
                        subtitle = v.levels.joinToString(" → ") { context.getString(it.labelRes) },
                        dragging = dragging,
                        dragHandle = Modifier.draggableHandle(onDragStopped = { save(views) }),
                        removable = views.size > 1,
                        onRemove = { save(views.filter { it.id != v.id }) },
                        onClick = { onEditView(v.id) },
                    )
                }
            }
            item(key = "add") { ClickRow(stringResource(R.string.add_view)) { adding = true } }
            item(key = "h:other") { SectionHeader(stringResource(R.string.set_home_other)) }
            item(key = "start") {
                ChoiceRow(
                    stringResource(R.string.set_start_tab), StartTab.entries, d.startTab,
                    { context.getString(if (it == StartTab.FIRST) R.string.set_start_tab_first else R.string.set_start_tab_last) },
                ) { v -> update { it.copy(display = it.display.copy(startTab = v)) } }
            }
            item(key = "search") {
                SwitchRow(stringResource(R.string.set_show_search), d.showSearchButton) { v -> update { it.copy(display = it.display.copy(showSearchButton = v)) } }
            }
            item(key = "order") {
                ChoiceRow(stringResource(R.string.album_order_default), AlbumOrder.entries - AlbumOrder.MANUAL, d.albumOrder, { context.getString(it.labelRes) }) { v ->
                    if (d.artistAlbumOrder.isEmpty()) update { it.copy(display = it.display.copy(albumOrder = v)) } else pendingOrder = v
                }
            }
        }
    }

    // 一括変更：アーティストごとの設定を残すか、すべて既定にそろえるか
    pendingOrder?.let { order ->
        AlertDialog(
            onDismissRequest = { pendingOrder = null },
            title = { Text(stringResource(order.labelRes)) },
            text = {
                Column {
                    ListItem(
                        modifier = Modifier.clickable {
                            update { it.copy(display = it.display.copy(albumOrder = order)) }
                            pendingOrder = null
                        },
                        headlineContent = { Text(stringResource(R.string.album_order_keep_custom)) },
                    )
                    ListItem(
                        modifier = Modifier.clickable {
                            update { it.copy(display = it.display.copy(albumOrder = order, artistAlbumOrder = emptyMap(), customAlbumOrder = emptyMap())) }
                            pendingOrder = null
                        },
                        headlineContent = { Text(stringResource(R.string.album_order_reset_custom, d.artistAlbumOrder.size)) },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { pendingOrder = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.add_view_title)) },
            text = {
                LazyColumn {
                    items(ViewKind.entries) { k ->
                        ListItem(
                            modifier = Modifier.clickable {
                                adding = false
                                if (k == ViewKind.CUSTOM) customTagFor = true else save(views + ViewConfig.create(k))
                            },
                            headlineContent = { Text(stringResource(k.labelRes)) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (customTagFor) {
        TextInputDialog(stringResource(R.string.custom_tag_title), "", onDismiss = { customTagFor = false }) { tag ->
            if (tag.isNotBlank()) save(views + ViewConfig.create(ViewKind.CUSTOM, tag.trim().uppercase()))
            customTagFor = false
        }
    }
}

/** 再生画面の設定：表示する項目（ホームのタブと同じ操作）と、長い値の自動スクロール。 */
@Composable
fun NowPlayingItemsScreen(settings: AppSettings, onBack: () -> Unit) {
    val update = rememberUpdater()
    var items by remember { mutableStateOf(settings.display.nowPlaying.items) }
    LaunchedEffect(settings.display.nowPlaying.items) { items = settings.display.nowPlaying.items }
    var adding by remember { mutableStateOf(false) }

    fun save(list: List<NpItem>) {
        items = list
        update { it.copy(display = it.display.copy(nowPlaying = it.display.nowPlaying.copy(items = list))) }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - 1
        val t = to.index - 1
        if (f in items.indices && t in items.indices) items = items.toMutableList().apply { add(t, removeAt(f)) }
    }
    val hidden = NpItem.entries.filter { it !in items }

    SettingsScaffold(title = stringResource(R.string.set_now_playing), onBack = onBack) {
        LazyColumn(state = listState) {
            item(key = "h:items") { SectionHeader(stringResource(R.string.set_now_playing_items)) }
            items(items, key = { it.name }) { item ->
                ReorderableItem(reorder, key = item.name) { dragging ->
                    OrderRow(
                        title = stringResource(item.labelRes),
                        dragging = dragging,
                        dragHandle = Modifier.draggableHandle(onDragStopped = { save(items) }),
                        removable = true,
                        onRemove = { save(items - item) },
                    )
                }
            }
            if (hidden.isNotEmpty()) item(key = "add") { ClickRow(stringResource(R.string.add_item)) { adding = true } }
            item(key = "h:other") { SectionHeader(stringResource(R.string.set_home_other)) }
            item(key = "marquee") {
                SwitchRow(stringResource(R.string.np_marquee), settings.display.nowPlaying.marquee) { v ->
                    update { it.copy(display = it.display.copy(nowPlaying = it.display.nowPlaying.copy(marquee = v))) }
                }
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.add_item)) },
            text = {
                LazyColumn {
                    items(hidden) { k ->
                        ListItem(
                            modifier = Modifier.clickable { adding = false; save(items + k) },
                            headlineContent = { Text(stringResource(k.labelRes)) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** 並べ替えできる1行（つまみ・名前・削除ボタン）。 */
@Composable
private fun OrderRow(
    title: String,
    subtitle: String? = null,
    dragging: Boolean,
    dragHandle: Modifier,
    removable: Boolean,
    onRemove: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}, modifier = dragHandle) {
            Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder))
        }
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(enabled = removable, onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
        }
    }
}

/** ビュー1つの編集：名前・階層（6.2）・各段の一覧形式（6.3）。 */
@Composable
fun ViewEditScreen(settings: AppSettings, viewId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val view = settings.display.homeViews.firstOrNull { it.id == viewId }
    if (view == null) { onBack(); return }
    var addLevel by remember { mutableStateOf(false) }
    val tagKeys by produceState(emptyList<String>()) { value = c.library.allTagKeys() }

    fun save(v: ViewConfig) = scope.launch {
        c.settings.update { s -> s.copy(display = s.display.copy(homeViews = s.display.homeViews.map { if (it.id == viewId) v else it })) }
    }

    fun setStyle(level: Int, f: (ListStyle) -> ListStyle) {
        val styles = view.levels.indices.map { i -> view.styleAt(i) }.toMutableList()
        styles[level] = f(styles[level])
        save(view.copy(styles = styles))
    }

    SettingsScaffold(title = view.displayTitle(), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextRow(stringResource(R.string.tab_name), view.title, summary = view.displayTitle()) { v -> save(view.copy(title = v.trim())) }
            if (view.kind == ViewKind.CUSTOM) {
                TextRow(stringResource(R.string.custom_tag_title), view.customTag, hint = tagKeys.take(40).joinToString(" ")) { v ->
                    if (v.isNotBlank()) save(view.copy(customTag = v.trim().uppercase()))
                }
            }

            SectionHeader(stringResource(R.string.levels_title))
            Text(
                view.levels.joinToString("  →  ") { if (it == ViewKind.CUSTOM) view.customTag else context.getString(it.labelRes) },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge,
            )
            // 先頭（ビューの種類）と末尾（曲）以外の段は省いたり追加したりできる
            view.levels.forEachIndexed { i, k ->
                if (i in 1 until view.levels.size - 1) {
                    ClickRow(stringResource(R.string.level_remove, stringResource(k.labelRes))) {
                        save(view.copy(levels = view.levels.filterIndexed { j, _ -> j != i }, styles = view.levels.indices.filter { it != i }.map { view.styleAt(it) }))
                    }
                }
            }
            if (view.levels.size >= 2 && view.levels.first() !in setOf(ViewKind.PLAYLIST) && view.kind != ViewKind.ALBUM_FLOW) {
                ClickRow(stringResource(R.string.level_add)) { addLevel = true }
            }

            view.levels.forEachIndexed { level, kind ->
                val style = view.styleAt(level)
                SectionHeader(stringResource(R.string.level_header, level + 1, if (kind == ViewKind.CUSTOM) view.customTag else stringResource(kind.labelRes)))
                if (view.kind == ViewKind.ALBUM_FLOW && level == 0) {
                    // Album Flow 本体は並び順だけを選べる。ランダムは開くたびに並びが変わる
                    ChoiceRow(
                        stringResource(R.string.sort), listOf(SortBy.ARTIST, SortBy.SORT_KEY, SortBy.YEAR, SortBy.DATE_ADDED, SortBy.RANDOM), style.sortBy,
                        { context.getString(if (it == SortBy.ARTIST) R.string.sort_album_artist else if (it == SortBy.SORT_KEY) R.string.sort_album_title else it.labelRes) },
                    ) { v -> setStyle(level) { it.copy(sortBy = v) } }
                    if (style.sortBy != SortBy.RANDOM) {
                        SwitchRow(stringResource(R.string.descending), style.descending) { v -> setStyle(level) { it.copy(descending = v) } }
                    }
                    return@forEachIndexed
                }
                ChoiceRow(stringResource(R.string.layout), Layout.entries, style.layout, { context.getString(if (it == Layout.LIST) R.string.layout_list else R.string.layout_grid) }) { v -> setStyle(level) { it.copy(layout = v) } }
                if (style.layout == Layout.GRID) {
                    SliderRow(stringResource(R.string.grid_columns), style.gridColumns.toFloat(), 1f..6f, 1f, { context.getString(R.string.grid_columns_value, it.toInt()) }) { v -> setStyle(level) { it.copy(gridColumns = v.toInt()) } }
                }
                SwitchRow(stringResource(R.string.artwork), style.showArtwork) { v -> setStyle(level) { it.copy(showArtwork = v) } }
                if (style.showArtwork && style.layout == Layout.LIST) {
                    SliderRow(stringResource(R.string.artwork_size), style.artworkSize.toFloat(), 32f..120f, 4f, { "${it.toInt()}dp" }) { v -> setStyle(level) { it.copy(artworkSize = v.toInt()) } }
                }
                TextRow(stringResource(R.string.line1), style.line1, summary = style.line1.ifEmpty { "<Name>" }) { v ->
                    setStyle(level) { it.copy(line1 = v) }
                }
                TextRow(stringResource(R.string.line2), style.line2, summary = style.line2.ifEmpty { "—" }) { v ->
                    setStyle(level) { it.copy(line2 = v) }
                }
                // 人物の下のアルバムの並びは「ホーム > アーティストのアルバムの並び順」とアーティストごとのカスタム順で決まる
                val personAlbums = kind == ViewKind.ALBUM && view.levels.getOrNull(level - 1) in io.github.oke1943.azsaplayer.library.PERSON_KINDS
                if (!personAlbums) {
                    ChoiceRow(stringResource(R.string.sort), sortOptions(kind), style.sortBy, { context.getString(it.labelRes) }) { v -> setStyle(level) { it.copy(sortBy = v) } }
                    SwitchRow(stringResource(R.string.descending), style.descending) { v -> setStyle(level) { it.copy(descending = v) } }
                }
                SwitchRow(stringResource(R.string.fast_scroll_index), style.showIndex) { v -> setStyle(level) { it.copy(showIndex = v) } }
            }
            Spacer(Modifier.padding(24.dp))
        }
    }

    if (addLevel) {
        val candidates = listOf(ViewKind.ALBUM, ViewKind.ALBUM_ARTIST, ViewKind.ARTIST, ViewKind.COMPOSER, ViewKind.GENRE, ViewKind.YEAR, ViewKind.DECADE, ViewKind.FOLDER)
            .filter { it !in view.levels }
        AlertDialog(
            onDismissRequest = { addLevel = false },
            title = { Text(stringResource(R.string.level_add_title)) },
            text = {
                LazyColumn {
                    items(candidates) { k ->
                        ListItem(modifier = Modifier.clickable {
                            val at = view.levels.size - 1
                            val levels = view.levels.toMutableList().apply { add(at, k) }
                            val styles = view.levels.indices.map { view.styleAt(it) }.toMutableList().apply { add(at, ViewConfig.defaultStyle(k)) }
                            save(view.copy(levels = levels, styles = styles))
                            addLevel = false
                        }, headlineContent = { Text(stringResource(k.labelRes)) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addLevel = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun sortOptions(kind: ViewKind): List<SortBy> = when (kind) {
    ViewKind.TRACK, ViewKind.RECENT -> listOf(SortBy.TRACK_NUMBER, SortBy.SORT_KEY, SortBy.NAME, SortBy.ARTIST, SortBy.ALBUM, SortBy.YEAR, SortBy.DATE_ADDED)
    else -> listOf(SortBy.SORT_KEY, SortBy.NAME, SortBy.YEAR, SortBy.DATE_ADDED, SortBy.COUNT)
}
