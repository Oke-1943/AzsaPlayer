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
import io.github.oke1943.azsaplayer.library.Template
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.Layout
import io.github.oke1943.azsaplayer.settings.ListStyle
import io.github.oke1943.azsaplayer.settings.SortBy
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

/** ホームの表示項目（6.1）：任意の数を選び、ドラッグで順序を変える。 */
@Composable
fun HomeViewsScreen(settings: AppSettings, onBack: () -> Unit, onEditView: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var views by remember { mutableStateOf(settings.display.homeViews) }
    LaunchedEffect(settings.display.homeViews) { views = settings.display.homeViews }
    var adding by remember { mutableStateOf(false) }
    var customTagFor by remember { mutableStateOf(false) }

    fun save(list: List<ViewConfig>) {
        views = list
        scope.launch { c.settings.update { it.copy(display = it.display.copy(homeViews = list)) } }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        views = views.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    SettingsScaffold(title = stringResource(R.string.set_home_views), onBack = onBack) {
        Text(
            stringResource(R.string.home_views_hint),
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
            items(views, key = { it.id }) { v ->
                ReorderableItem(reorder, key = v.id) { dragging ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
                            .clickable { onEditView(v.id) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}, modifier = Modifier.draggableHandle(onDragStopped = { save(views) })) {
                            Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(v.displayTitle())
                            Text(
                                v.levels.joinToString(" → ") { context.getString(it.labelRes) }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(enabled = views.size > 1, onClick = { save(views.filter { it.id != v.id }) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }
        }
        ClickRow(stringResource(R.string.add_view)) { adding = true }
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
                            headlineContent = { Text(if (k == ViewKind.CUSTOM) stringResource(R.string.add_view_custom) else stringResource(k.labelRes)) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (customTagFor) {
        TextInputDialog(stringResource(R.string.custom_tag_title), "", hint = stringResource(R.string.custom_tag_hint), onDismiss = { customTagFor = false }) { tag ->
            if (tag.isNotBlank()) save(views + ViewConfig.create(ViewKind.CUSTOM, tag.trim().uppercase()))
            customTagFor = false
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
            TextRow(stringResource(R.string.tab_name), view.title, summary = view.displayTitle(), hint = stringResource(R.string.tab_name_hint)) { v -> save(view.copy(title = v.trim())) }
            if (view.kind == ViewKind.CUSTOM) {
                TextRow(stringResource(R.string.custom_tag_title), view.customTag, hint = stringResource(R.string.custom_tag_existing, tagKeys.take(40).joinToString(" "))) { v ->
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
                    ClickRow(stringResource(R.string.level_remove, stringResource(k.labelRes)), stringResource(R.string.level_remove_example)) {
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
                    // Album Flow 本体は並び順だけを選べる
                    ChoiceRow(stringResource(R.string.sort), listOf(SortBy.ARTIST, SortBy.SORT_KEY, SortBy.YEAR, SortBy.DATE_ADDED), style.sortBy, { context.getString(if (it == SortBy.ARTIST) R.string.sort_album_artist else if (it == SortBy.SORT_KEY) R.string.sort_album_title else it.labelRes) }) { v -> setStyle(level) { it.copy(sortBy = v) } }
                    SwitchRow(stringResource(R.string.descending), style.descending) { v -> setStyle(level) { it.copy(descending = v) } }
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
                val hint = stringResource(R.string.template_hint, Template.FIELDS.joinToString(" ") { "<$it>" })
                TextRow(stringResource(R.string.line1), style.line1, summary = style.line1.ifEmpty { stringResource(R.string.line1_default) }, hint = hint + "\n" + stringResource(R.string.line1_hint)) { v ->
                    setStyle(level) { it.copy(line1 = v) }
                }
                TextRow(stringResource(R.string.line2), style.line2, summary = style.line2.ifEmpty { stringResource(R.string.line2_default) }, hint = hint + "\n" + stringResource(R.string.line2_hint)) { v ->
                    setStyle(level) { it.copy(line2 = v) }
                }
                ChoiceRow(stringResource(R.string.sort), sortOptions(kind), style.sortBy, { context.getString(it.labelRes) }) { v -> setStyle(level) { it.copy(sortBy = v) } }
                SwitchRow(stringResource(R.string.descending), style.descending) { v -> setStyle(level) { it.copy(descending = v) } }
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
