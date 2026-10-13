package io.github.oke1943.azsaplayer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.oke1943.azsaplayer.library.LibraryScanner
import io.github.oke1943.azsaplayer.ui.common.SettingsScaffold
import java.io.File
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/**
 * ストレージを辿ってフォルダ（または指定拡張子のファイル）を選ぶ画面。
 * 「すべてのファイルへのアクセス」権限があるため、実際のパスで扱える。
 */
@Composable
fun FilePickerDialog(
    title: String,
    pickFolder: Boolean,
    extensions: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPick: (File) -> Unit,
) {
    val context = LocalContext.current
    val roots = remember { LibraryScanner.storageRoots(context) }
    var dir by remember { mutableStateOf<File?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            val current = dir
            val goUp = { dir = if (current == null || roots.contains(current)) null else current.parentFile }
            BackHandler { if (current == null) onDismiss() else goUp() }
            SettingsScaffold(title = current?.path ?: title, onBack = { if (current == null) onDismiss() else goUp() }) {
                Column(Modifier.fillMaxSize()) {
                    if (pickFolder && current != null) {
                        Button(onClick = { onPick(current) }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.picker_choose_folder)) }
                    }
                    val entries = remember(current) {
                        if (current == null) roots else (current.listFiles()?.toList() ?: emptyList())
                            .filter { !it.name.startsWith(".") && (it.isDirectory || (!pickFolder && it.extension.lowercase() in extensions)) }
                            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    }
                    if (entries.isEmpty()) {
                        Text(stringResource(R.string.picker_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn {
                        items(entries, key = { it.path }) { f ->
                            ListItem(
                                modifier = Modifier.clickable { if (f.isDirectory) dir = f else onPick(f) },
                                leadingContent = {
                                    Icon(if (f.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                                },
                                headlineContent = { Text(if (current == null) f.path else f.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}
