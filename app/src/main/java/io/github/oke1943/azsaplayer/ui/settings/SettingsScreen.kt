package io.github.oke1943.azsaplayer.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.oke1943.azsaplayer.AppIcons
import io.github.oke1943.azsaplayer.AppLocale
import io.github.oke1943.azsaplayer.R
import io.github.oke1943.azsaplayer.StorageAccess
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.data.ScanErrorEntity
import io.github.oke1943.azsaplayer.library.ScanPhase
import io.github.oke1943.azsaplayer.library.ScanProgress
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.PathRule
import io.github.oke1943.azsaplayer.settings.ReplayGainMode
import io.github.oke1943.azsaplayer.settings.StartTab
import io.github.oke1943.azsaplayer.settings.ThemeMode
import io.github.oke1943.azsaplayer.tag.RawTags
import io.github.oke1943.azsaplayer.ui.Routes
import io.github.oke1943.azsaplayer.ui.common.ChoiceRow
import io.github.oke1943.azsaplayer.ui.common.ClickRow
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import io.github.oke1943.azsaplayer.ui.common.SectionHeader
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import io.github.oke1943.azsaplayer.ui.common.SliderRow
import io.github.oke1943.azsaplayer.ui.common.SwitchRow
import io.github.oke1943.azsaplayer.ui.common.TextRow
import io.github.oke1943.azsaplayer.ui.common.displayTitle
import io.github.oke1943.azsaplayer.ui.theme.ACCENT_COLORS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 設定の大分類（6.5）：表示・ライブラリ・再生・リプレイゲイン・詳細。 */
@Composable
fun SettingsHomeScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ClickRow(stringResource(R.string.set_display), stringResource(R.string.set_display_summary)) { onOpen(Routes.SETTINGS_DISPLAY) }
            ClickRow(stringResource(R.string.set_library), stringResource(R.string.set_library_summary)) { onOpen(Routes.SETTINGS_LIBRARY) }
            ClickRow(stringResource(R.string.set_playback), stringResource(R.string.set_playback_summary)) { onOpen(Routes.SETTINGS_PLAYBACK) }
            ClickRow(stringResource(R.string.set_rg), stringResource(R.string.set_rg_summary)) { onOpen(Routes.SETTINGS_RG) }
            ClickRow(stringResource(R.string.set_advanced), stringResource(R.string.set_advanced_summary)) { onOpen(Routes.SETTINGS_ADVANCED) }
        }
    }
}

@Composable
private fun rememberUpdater(): ((AppSettings) -> AppSettings) -> Unit {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    return { f -> scope.launch { c.settings.update(f) } }
}

@Composable
fun DisplaySettingsScreen(settings: AppSettings, onBack: () -> Unit, onOpenHomeViews: () -> Unit) {
    val context = LocalContext.current
    val update = rememberUpdater()
    val d = settings.display
    SettingsScaffold(title = stringResource(R.string.set_display), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.set_appearance))
            val language = remember { AppLocale.saved(context) }
            ChoiceRow(stringResource(R.string.set_language), AppLocale.CHOICES, language, { languageLabel(context, it) }) { v ->
                if (v != language) {
                    AppLocale.save(context, v)
                    (context as? Activity)?.recreate()
                }
            }
            ChoiceRow(stringResource(R.string.set_theme), ThemeMode.entries, d.theme, { themeLabel(context, it) }) { v -> update { it.copy(display = it.display.copy(theme = v)) } }
            Text(stringResource(R.string.set_accent), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            LazyRow(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(ACCENT_COLORS) { color ->
                    val selected = color == d.accentColor
                    Spacer(Modifier.width(4.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(color))
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { update { it.copy(display = it.display.copy(accentColor = color)) } },
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            SliderRow(stringResource(R.string.set_font_size), d.fontScale, 0.8f..1.4f, 0.05f, { "%.0f%%".format(it * 100) }) { v ->
                update { it.copy(display = it.display.copy(fontScale = v)) }
            }
            AppIconPicker()

            SectionHeader(stringResource(R.string.set_home))
            ClickRow(
                stringResource(R.string.set_home_views),
                d.homeViews.map { it.displayTitle(context.resources) }.joinToString(" · ") + "\n" + stringResource(R.string.set_home_views_summary),
            ) { onOpenHomeViews() }
            d.homeViews.forEach { v ->
                SwitchRow(stringResource(R.string.set_show_artwork_in, v.displayTitle(context.resources)), v.styleAt(0).showArtwork) { on ->
                    update { st ->
                        st.copy(display = st.display.copy(homeViews = st.display.homeViews.map { x ->
                            if (x.id != v.id) x else x.copy(styles = x.levels.indices.map { i ->
                                if (i == 0) x.styleAt(0).copy(showArtwork = on) else x.styleAt(i)
                            })
                        }))
                    }
                }
            }
            SwitchRow(stringResource(R.string.set_show_search), d.showSearchButton) { v -> update { it.copy(display = it.display.copy(showSearchButton = v)) } }
            ChoiceRow(
                stringResource(R.string.set_start_tab), StartTab.entries, d.startTab,
                { context.getString(if (it == StartTab.FIRST) R.string.set_start_tab_first else R.string.set_start_tab_last) },
            ) { v -> update { it.copy(display = it.display.copy(startTab = v)) } }
            TextRow(
                stringResource(R.string.set_join_separator), d.joinSeparator,
                summary = stringResource(R.string.set_join_separator_summary, d.joinSeparator),
            ) { v -> if (v.isNotEmpty()) update { it.copy(display = it.display.copy(joinSeparator = v)) } }

            SectionHeader(stringResource(R.string.set_now_playing_items))
            val np = d.nowPlaying
            fun setNp(f: (io.github.oke1943.azsaplayer.settings.NowPlayingSettings) -> io.github.oke1943.azsaplayer.settings.NowPlayingSettings) =
                update { it.copy(display = it.display.copy(nowPlaying = f(it.display.nowPlaying))) }
            SwitchRow(stringResource(R.string.np_artwork), np.showArtwork) { v -> setNp { it.copy(showArtwork = v) } }
            SwitchRow(stringResource(R.string.np_title), np.showTitle) { v -> setNp { it.copy(showTitle = v) } }
            SwitchRow(stringResource(R.string.np_artist), np.showArtist) { v -> setNp { it.copy(showArtist = v) } }
            SwitchRow(stringResource(R.string.np_album), np.showAlbum) { v -> setNp { it.copy(showAlbum = v) } }
            SwitchRow(stringResource(R.string.np_format), np.showFormat, stringResource(R.string.np_format_summary)) { v -> setNp { it.copy(showFormat = v) } }
            SwitchRow(stringResource(R.string.np_rg), np.showReplayGain, stringResource(R.string.np_rg_summary)) { v -> setNp { it.copy(showReplayGain = v) } }
            SwitchRow(stringResource(R.string.np_seekbar), np.showSeekBar) { v -> setNp { it.copy(showSeekBar = v) } }
            SwitchRow(stringResource(R.string.np_controls), np.showControls) { v -> setNp { it.copy(showControls = v) } }
            Spacer(Modifier.padding(16.dp))
        }
    }
}

/** アプリアイコンの選択（ver.7）。実物と同じアダプティブアイコンを並べ、タップで選ぶ（反映はアプリを離れたとき）。 */
@Composable
private fun AppIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    Text(stringResource(R.string.app_icon), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        for (option in AppIcons.OPTIONS) {
            val selected = option == current
            val bitmap = remember(option) { context.getDrawable(option.icon)!!.toBitmap(144, 144).asImageBitmap() }
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                    .clickable {
                        if (!selected) {
                            AppIcons.choose(context, option)
                            current = option
                        }
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap, contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(
                    stringResource(option.label), Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        stringResource(R.string.app_icon_note), Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun languageLabel(context: android.content.Context, lang: String) = when (lang) {
    AppLocale.JAPANESE -> "日本語"
    AppLocale.ENGLISH -> "English"
    else -> context.getString(R.string.set_language_system)
}

private fun themeLabel(context: android.content.Context, t: ThemeMode) = context.getString(
    when (t) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.BLACK -> R.string.theme_black
    },
)

/** スキャンの状態の文言。 */
fun scanStatusText(context: android.content.Context, scan: ScanProgress, trackCount: Int): String {
    val count = context.getString(R.string.scan_track_count, trackCount)
    return when (scan.phase) {
        ScanPhase.FINDING -> context.getString(R.string.scan_finding)
        ScanPhase.READING -> context.getString(R.string.scan_reading) + " ${scan.done}/${scan.total}"
        ScanPhase.FINISHING -> context.getString(R.string.scan_finishing)
        ScanPhase.NO_FOLDERS -> count + "\n" + context.getString(R.string.scan_no_folders)
        ScanPhase.FAILED -> count + "\n" + context.getString(R.string.scan_failed, scan.detail)
        else -> count
    }
}

@Composable
fun LibrarySettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val update = rememberUpdater()
    val l = settings.library
    val scan by c.library.scan.collectAsStateWithLifecycle()
    val snapshot by c.library.snapshot.collectAsStateWithLifecycle()
    var pickFolder by remember { mutableStateOf<String?>(null) } // "include" / "exclude"
    var pickM3u by remember { mutableStateOf(false) }
    var addRule by remember { mutableStateOf(false) }
    var needsRescan by remember { mutableStateOf(false) }
    var allFiles by remember { mutableStateOf(StorageAccess.hasAllFiles()) }
    LifecycleResumeEffect(Unit) {
        allFiles = StorageAccess.hasAllFiles()
        onPauseOrDispose { }
    }

    fun importM3u(name: String, bytes: ByteArray, dir: String?) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { c.playlists.importM3u(name, bytes, dir) }
            val msg = context.getString(R.string.m3u_imported, name, r.matched) +
                if (r.unmatched.isNotEmpty()) context.getString(R.string.m3u_unmatched, r.unmatched.size) else ""
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    // 「音楽ファイルの読み取りのみ」ではM3Uファイルが見えないことがあるので、Android標準のファイル選択を使う
    val openM3u = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) cur.getString(0) else null
                }
            }?.substringBeforeLast('.') ?: "Playlist"
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return@launch
            importM3u(name, bytes, null)
        }
    }

    SettingsScaffold(title = stringResource(R.string.set_library), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.set_music_folders))
            if (l.folders.isEmpty()) {
                Text(
                    stringResource(R.string.set_music_folders_empty),
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            l.folders.forEach { f ->
                RemovableRow(f) { update { it.copy(library = it.library.copy(folders = it.library.folders - f)) }; needsRescan = true }
            }
            ClickRow(stringResource(R.string.set_add_folder)) { pickFolder = "include" }

            SectionHeader(stringResource(R.string.set_scan))
            Text(scanStatusText(context, scan, snapshot.tracks.size), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.set_scan_hint),
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                androidx.compose.material3.Button(enabled = !scan.running && l.folders.isNotEmpty(), onClick = { c.library.startScan(full = needsRescan); needsRescan = false }) {
                    Text(stringResource(R.string.set_scan_button))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = !scan.running && l.folders.isNotEmpty(), onClick = { c.library.startScan(full = true); needsRescan = false }) {
                    Text(stringResource(R.string.set_rescan_all))
                }
            }
            if (needsRescan) {
                Text(stringResource(R.string.set_needs_rescan), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionHeader(stringResource(R.string.set_file_access))
            ClickRow(
                stringResource(if (allFiles) R.string.access_all_files else R.string.access_media_only),
                stringResource(if (allFiles) R.string.access_all_files_switch else R.string.access_media_only_switch),
            ) {
                context.startActivity(StorageAccess.allFilesSettingsIntent(context))
            }

            SectionHeader(stringResource(R.string.set_excluded_folders))
            l.excludedFolders.forEach { f ->
                RemovableRow(f) { update { it.copy(library = it.library.copy(excludedFolders = it.library.excludedFolders - f)) }; needsRescan = true }
            }
            ClickRow(stringResource(R.string.set_add_excluded)) { pickFolder = "exclude" }
            SwitchRow(stringResource(R.string.set_respect_nomedia), l.respectNomedia, stringResource(R.string.set_respect_nomedia_summary)) { v ->
                update { it.copy(library = it.library.copy(respectNomedia = v)) }; needsRescan = true
            }

            SectionHeader(stringResource(R.string.set_tag_rules))
            TextRow(
                stringResource(R.string.set_split_separators), l.splitSeparators.joinToString(" "),
                summary = l.splitSeparators.joinToString(" ") { "“$it”" },
                hint = stringResource(R.string.set_split_separators_hint),
            ) { v ->
                val list = v.split(' ').filter { it.isNotBlank() }.ifEmpty { listOf(";") }
                update { it.copy(library = it.library.copy(splitSeparators = list)) }; needsRescan = true
            }
            TextRow(
                stringResource(R.string.set_multi_fields), l.multiValueFields.joinToString(" "),
                hint = stringResource(R.string.set_multi_fields_hint),
            ) { v ->
                val list = v.split(' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                update { it.copy(library = it.library.copy(multiValueFields = list)) }; needsRescan = true
            }
            SwitchRow(stringResource(R.string.set_ignore_articles), l.ignoreArticles, stringResource(R.string.set_ignore_articles_summary)) { v ->
                update { it.copy(library = it.library.copy(ignoreArticles = v)) }
            }

            SectionHeader(stringResource(R.string.kind_playlist))
            ClickRow(stringResource(R.string.set_import_m3u), stringResource(R.string.set_import_m3u_summary)) {
                if (allFiles) pickM3u = true
                else openM3u.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "*/*"))
            }
            Text(stringResource(R.string.set_path_rules), Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            l.playlistPathRules.forEach { r ->
                RemovableRow("${r.from}  →  ${r.to}") { update { it.copy(library = it.library.copy(playlistPathRules = it.library.playlistPathRules - r)) } }
            }
            ClickRow(stringResource(R.string.set_add_rule), stringResource(R.string.set_add_rule_summary)) { addRule = true }
            Spacer(Modifier.padding(16.dp))
        }
    }

    pickFolder?.let { mode ->
        FilePickerDialog(
            stringResource(if (mode == "include") R.string.picker_scan_folder else R.string.picker_excluded_folder),
            pickFolder = true, onDismiss = { pickFolder = null },
        ) { f ->
            update {
                if (mode == "include") it.copy(library = it.library.copy(folders = (it.library.folders + f.path).distinct()))
                else it.copy(library = it.library.copy(excludedFolders = (it.library.excludedFolders + f.path).distinct()))
            }
            needsRescan = true
            pickFolder = null
        }
    }
    if (pickM3u) {
        FilePickerDialog(stringResource(R.string.picker_playlist_file), pickFolder = false, extensions = setOf("m3u", "m3u8"), onDismiss = { pickM3u = false }) { f ->
            pickM3u = false
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { f.readBytes() }
                importM3u(f.nameWithoutExtension, bytes, f.parent)
            }
        }
    }
    if (addRule) PathRuleDialog(onDismiss = { addRule = false }) { rule ->
        update { it.copy(library = it.library.copy(playlistPathRules = it.library.playlistPathRules + rule)) }
        addRule = false
    }
}

@Composable
private fun RemovableRow(text: String, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove)) } },
    )
}

@Composable
private fun PathRuleDialog(onDismiss: () -> Unit, onAdd: (PathRule) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.path_rule_title)) },
        text = {
            Column {
                OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.path_rule_from)) }, singleLine = true)
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.path_rule_to)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = from.isNotBlank(), onClick = { onAdd(PathRule(from.trim(), to.trim())) }) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PlaybackSettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val update = rememberUpdater()
    SettingsScaffold(title = stringResource(R.string.set_playback), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SwitchRow(stringResource(R.string.set_resume), settings.playback.resumeOnStart) { v ->
                update { it.copy(playback = it.playback.copy(resumeOnStart = v)) }
            }
            Text(
                stringResource(R.string.set_playback_note),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ReplayGainSettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val update = rememberUpdater()
    val rg = settings.replayGain
    SettingsScaffold(title = stringResource(R.string.set_rg), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.rg_which_gain))
            ReplayGainMode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { update { it.copy(replayGain = it.replayGain.copy(mode = m)) } }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = rg.mode == m, onClick = { update { it.copy(replayGain = it.replayGain.copy(mode = m)) } })
                    Text(stringResource(modeLabel(m)), style = MaterialTheme.typography.bodyLarge)
                }
            }
            SectionHeader(stringResource(R.string.rg_adjust))
            SliderRow(stringResource(R.string.rg_preamp_tagged), rg.preampTagged, -15f..15f, 0.5f, { "%+.1f dB".format(it) }) { v ->
                update { it.copy(replayGain = it.replayGain.copy(preampTagged = v)) }
            }
            SliderRow(stringResource(R.string.rg_preamp_untagged), rg.preampUntagged, -15f..15f, 0.5f, { "%+.1f dB".format(it) }) { v ->
                update { it.copy(replayGain = it.replayGain.copy(preampUntagged = v)) }
            }
            SwitchRow(stringResource(R.string.rg_prevent_clipping), rg.preventClipping, stringResource(R.string.rg_prevent_clipping_summary)) { v ->
                update { it.copy(replayGain = it.replayGain.copy(preventClipping = v)) }
            }
            SwitchRow(stringResource(R.string.rg_show_on_now_playing), settings.display.nowPlaying.showReplayGain) { v ->
                update { it.copy(display = it.display.copy(nowPlaying = it.display.nowPlaying.copy(showReplayGain = v))) }
            }
            Text(
                stringResource(R.string.rg_note),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun modeLabel(m: ReplayGainMode) = when (m) {
    ReplayGainMode.OFF -> R.string.rg_mode_off
    ReplayGainMode.TRACK -> R.string.rg_mode_track
    ReplayGainMode.ALBUM -> R.string.rg_mode_album
    ReplayGainMode.AUTO -> R.string.rg_mode_auto
}

@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit, onOpenTagDump: () -> Unit, onOpenErrors: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = c.settings.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            }.isSuccess
            Toast.makeText(context, context.getString(if (ok) R.string.export_done else R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }
                c.settings.importJson(text)
            }
            Toast.makeText(
                context,
                if (result.isSuccess) context.getString(R.string.import_done) else context.getString(R.string.import_failed, result.exceptionOrNull()?.message ?: ""),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    SettingsScaffold(title = stringResource(R.string.set_advanced), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ClickRow(stringResource(R.string.tag_dump), stringResource(R.string.tag_dump_summary), onOpenTagDump)
            ClickRow(stringResource(R.string.scan_errors), stringResource(R.string.scan_errors_summary), onOpenErrors)
            SectionHeader(stringResource(R.string.set_manage_settings))
            ClickRow(stringResource(R.string.export_settings)) { export.launch("azsaplayer-settings.json") }
            ClickRow(stringResource(R.string.import_settings)) { import.launch(arrayOf("application/json", "text/plain", "*/*")) }
            SectionHeader(stringResource(R.string.set_about))
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            }
            ListItem(headlineContent = { Text(stringResource(R.string.version)) }, supportingContent = { Text(version) })
            ClickRow(stringResource(R.string.licenses), stringResource(R.string.licenses_summary), onOpenLicenses)
        }
    }
}

@Composable
fun ScanErrorsScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val errors by c.library.scanErrors.collectAsStateWithLifecycle(initialValue = emptyList<ScanErrorEntity>())
    SettingsScaffold(title = stringResource(R.string.scan_errors), onBack = onBack) {
        if (errors.isEmpty()) {
            EmptyMessage(stringResource(R.string.scan_errors_none))
            return@SettingsScaffold
        }
        LazyColumn {
            items(errors, key = { it.path }) { e ->
                val reason = when (e.reason) {
                    RawTags.ERROR_OPEN_FAILED -> stringResource(R.string.error_open_failed)
                    RawTags.ERROR_NO_RESULT -> stringResource(R.string.error_no_result)
                    else -> e.reason
                }
                ListItem(
                    headlineContent = { Text(File(e.path).name) },
                    supportingContent = { Text("$reason\n${e.path}") },
                )
            }
        }
    }
}

/** オープンソースライセンス（assets/licenses.txt）。 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember { runCatching { context.assets.open("licenses.txt").bufferedReader().readText() }.getOrDefault("") }
    SettingsScaffold(title = stringResource(R.string.licenses), onBack = onBack) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                text,
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}
