package io.github.oke1943.azsaplayer.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.oke1943.azsaplayer.tag.TagDump
import io.github.oke1943.azsaplayer.tag.TagLibNative
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

private val AUDIO_EXTENSIONS = setOf(
    "flac", "mp3", "m4a", "mp4", "aac", "alac", "ogg", "oga", "opus",
    "wav", "aif", "aiff", "ape", "wv", "dsf", "dff", "mpc",
)

private fun File.isAudio() = isFile && extension.lowercase() in AUDIO_EXTENSIONS

/**
 * 生タグダンプ（仕様書 3.1）。
 * フォルダを辿ってファイルを選ぶと、TagLibが返す全フィールドを値の区切りが分かる形で表示する。
 */
@Composable
fun TagDumpScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var dir by remember { mutableStateOf<File?>(null) } // null = ストレージ一覧
    var selected by remember { mutableStateOf<File?>(null) }

    val selectedFile = selected
    if (selectedFile != null) {
        BackHandler { selected = null }
        DumpResult(file = selectedFile, onBack = { selected = null })
        return
    }

    val goUp: () -> Unit = {
        val current = dir
        val roots = storageRoots(context)
        dir = if (current == null || roots.any { it == current }) null else current.parentFile
    }
    BackHandler(enabled = dir != null) { goUp() }

    val current = dir
    SettingsScaffold(
        title = current?.name ?: stringResource(R.string.tag_dump),
        onBack = { if (current == null) onExit() else goUp() },
    ) {
        if (current == null) {
            LazyColumn {
                items(storageRoots(context)) { root ->
                    ListItem(
                        modifier = Modifier.clickable { dir = root },
                        headlineContent = { Text(root.path) },
                    )
                }
            }
        } else {
            FolderList(dir = current, onOpenDir = { dir = it }, onOpenFile = { selected = it })
        }
    }
}

@Composable
private fun FolderList(dir: File, onOpenDir: (File) -> Unit, onOpenFile: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bulkStatus by remember(dir) { mutableStateOf<String?>(null) }
    var bulkRunning by remember(dir) { mutableStateOf(false) }
    val entries = remember(dir) {
        (dir.listFiles()?.toList() ?: emptyList())
            .filter { it.isDirectory || it.isAudio() }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Column {
        Text(dir.path, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        TextButton(
            modifier = Modifier.padding(horizontal = 8.dp),
            enabled = !bulkRunning,
            onClick = {
                scope.launch {
                    bulkRunning = true
                    bulkStatus = context.getString(R.string.dump_running, 0)
                    val out = withContext(Dispatchers.IO) {
                        bulkDump(context, dir) { n -> bulkStatus = context.getString(R.string.dump_running, n) }
                    }
                    bulkStatus = context.getString(R.string.dump_saved, out.path)
                    bulkRunning = false
                }
            },
        ) { Text(stringResource(R.string.dump_bulk)) }
        bulkStatus?.let {
            Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        LazyColumn {
            items(entries, key = { it.path }) { f ->
                ListItem(
                    modifier = Modifier.clickable { if (f.isDirectory) onOpenDir(f) else onOpenFile(f) },
                    headlineContent = { Text(if (f.isDirectory) "${f.name}/" else f.name) },
                )
            }
        }
    }
}

@Composable
private fun DumpResult(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    var dump by remember(file) { mutableStateOf<TagDump?>(null) }
    LaunchedEffect(file) {
        dump = withContext(Dispatchers.IO) { TagLibNative.dump(file.path) }
    }

    SettingsScaffold(
        title = file.name,
        onBack = onBack,
        actions = {
            TextButton(
                enabled = dump != null,
                onClick = { dump?.let { copyToClipboard(context, it.toText()) } },
            ) { Text(stringResource(R.string.copy)) }
        },
    ) {
        val d = dump
        if (d == null) {
            Text(stringResource(R.string.reading), Modifier.padding(16.dp))
            return@SettingsScaffold
        }
        SelectionContainer {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    Text(d.path, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
                for (section in d.sections) {
                    item(key = "s:${section.name}") {
                        Text(
                            dumpSectionLabel(section.name),
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(section.fields, key = { "f:${section.name}:${it.key}" }) { field ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(field.key, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            for (r in field.values) {
                                Text(
                                    TagDump.formatValueLine(r),
                                    Modifier.padding(start = 12.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun storageRoots(context: Context): List<File> {
    val sm = context.getSystemService(StorageManager::class.java)
    return sm.storageVolumes.mapNotNull { it.directory }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("tag dump", text))
}

/**
 * フォルダ以下の全音声ファイルをダンプし、アプリ専用フォルダにテキストで保存する。
 * 音楽ファイルには一切書き込まない。
 */
private fun bulkDump(context: Context, dir: File, onProgress: (Int) -> Unit): File {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    val out = File(context.getExternalFilesDir(null), "tagdump-$stamp.txt")
    var count = 0
    out.bufferedWriter(Charsets.UTF_8).use { w ->
        dir.walkTopDown()
            .filter { it.isAudio() }
            .sortedBy { it.path }
            .forEach { f ->
                w.write(TagLibNative.dump(f.path).toText())
                w.write("\n")
                onProgress(++count)
            }
    }
    return out
}

/** ダンプの区分名（英語の識別子）を表示言語の見出しにする。 */
@Composable
private fun dumpSectionLabel(name: String): String = when {
    name == "Summary" -> stringResource(R.string.dump_summary)
    name == "Error" -> stringResource(R.string.dump_error)
    name == "PropertyMap (unsupported)" -> stringResource(R.string.dump_unsupported)
    name == "Complex properties" -> stringResource(R.string.dump_complex)
    name == "Audio properties" -> stringResource(R.string.dump_audio)
    name == "MP4 atoms" -> stringResource(R.string.dump_mp4)
    else -> name
}
