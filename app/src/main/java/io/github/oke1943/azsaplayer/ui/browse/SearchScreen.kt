package io.github.oke1943.azsaplayer.ui.browse

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.library.BrowseEntry
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.LibAlbum
import io.github.oke1943.azsaplayer.library.LibPerson
import io.github.oke1943.azsaplayer.library.LibTrack
import io.github.oke1943.azsaplayer.library.SearchIndex
import io.github.oke1943.azsaplayer.library.SearchResults
import io.github.oke1943.azsaplayer.library.SortRules
import io.github.oke1943.azsaplayer.library.TrackEntry
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import io.github.oke1943.azsaplayer.ui.common.SectionHeader
import io.github.oke1943.azsaplayer.ui.player.MiniPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/**
 * 検索画面。入力するそばから、アーティスト・アルバム・曲を絞り込む。
 * [onOpenPerson]：(役割, 人物) で、その人のアルバム一覧を開く。
 */
@Composable
fun SearchScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenPerson: (Int, LibPerson) -> Unit,
    onOpenAlbum: (LibAlbum) -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val index by produceState<SearchIndex?>(null, snapshot, settings.library.ignoreArticles) {
        value = withContext(Dispatchers.Default) { SearchIndex(snapshot, SortRules(settings.library.ignoreArticles)) }
    }
    var results by remember { mutableStateOf(SearchResults.EMPTY) }
    LaunchedEffect(query, index) {
        val idx = index ?: return@LaunchedEffect
        delay(120) // 打鍵ごとに走らせすぎない
        results = withContext(Dispatchers.Default) { idx.search(query) }
    }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<BrowseEntry?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    val join = settings.display.joinSeparator

    Scaffold(
        topBar = {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                TextField(
                    value = query,
                    onValueChange = { query = it; expanded = null },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear)) }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }
        },
        bottomBar = { MiniPlayer(onOpen = onOpenNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                index == null -> EmptyMessage(stringResource(R.string.preparing))
                query.isBlank() -> EmptyMessage(stringResource(R.string.search_help))
                results.isEmpty -> EmptyMessage(stringResource(R.string.search_no_match, query))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    section(context.getString(R.string.kind_artist), results.people, 5, expanded, { expanded = it }) { p ->
                        val role = index!!.roleOf(p)
                        val art = snapshot.tracks.firstOrNull { t -> t.people(role).any { it.id == p.id } }?.album?.artTrack
                        ResultRow(art?.e, p.name, stringResource(roleLabel(role)), onClick = { keyboard?.hide(); onOpenPerson(role, p) })
                    }
                    section(context.getString(R.string.kind_album), results.albums, 5, expanded, { expanded = it }) { a ->
                        ResultRow(
                            a.artTrack.e, a.title.ifEmpty { stringResource(R.string.browse_no_album) }, a.albumArtists.joinToString(join) { it.name },
                            onClick = { keyboard?.hide(); onOpenAlbum(a) },
                            onLongClick = { menuFor = GroupEntry(ViewKind.ALBUM, a.key, a.title, a.titleSort ?: a.title, a.tracks, album = a) },
                        )
                    }
                    section(context.getString(R.string.kind_track), results.tracks, 20, expanded, { expanded = it }) { t ->
                        ResultRow(
                            t.album.artTrack.e, t.title, listOf(t.artists.joinToString(join) { it.name }, t.e.album).filter { it.isNotEmpty() }.joinToString(" — "),
                            dim = !t.e.playable,
                            onClick = {
                                keyboard?.hide()
                                if (!t.e.playable) {
                                    Toast.makeText(context, context.getString(R.string.unplayable_format, t.e.codec), Toast.LENGTH_SHORT).show()
                                } else {
                                    // その曲からアルバムを続けて再生する（ギャップレス・アルバムゲインが自然に効く）
                                    val album = albumOrder(t.album)
                                    c.player.play(album.map { it.id }, album.indexOf(t).coerceAtLeast(0))
                                }
                            },
                            onLongClick = { menuFor = TrackEntry(t, 0) },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { e ->
        EntryMenu(entry = e, allTrackIds = emptyList(), inPlaylist = null, onDismiss = { menuFor = null }, onOpenTrackInfo = onOpenTrackInfo)
    }
}

private fun albumOrder(album: LibAlbum): List<LibTrack> =
    album.tracks.filter { it.e.playable }.sortedWith(compareBy({ it.e.discNo ?: 1 }, { it.e.trackNo ?: Int.MAX_VALUE }))

private fun roleLabel(role: Int) = when (role) {
    Role.ALBUM_ARTIST -> R.string.kind_album_artist
    Role.ARTIST -> R.string.kind_artist
    else -> R.string.kind_composer
}

/** 見出し＋先頭 [preview] 件。「すべて表示」で全件に広げる。 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<T>,
    preview: Int,
    expanded: String?,
    onExpand: (String?) -> Unit,
    row: @Composable (T) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h:$title") { SectionHeader("$title（${list.size}${if (list.size >= 200) "+" else ""}）") }
    val shown = if (expanded == title) list else list.take(preview)
    items(shown.size, key = { "$title:$it" }) { i -> row(shown[i]) }
    if (list.size > preview) {
        item(key = "m:$title") {
            TextButton(onClick = { onExpand(if (expanded == title) null else title) }, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(if (expanded == title) R.string.close else R.string.show_all))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(art: TrackEntity?, title: String, subtitle: String, dim: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(art, io.github.oke1943.azsaplayer.settings.ARTWORK_SIZE.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                color = if (dim) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
