package io.github.oke1943.azsaplayer.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/** アートワーク。読み込み中・画像なしは控えめな枠だけを表示する。 */
@Composable
fun ArtworkImage(track: TrackEntity?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 4.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val loader = context.container.artwork
    // 曲が替わったら同じフレームのうちにその曲の画像（キャッシュ）へ切り替える。
    // 前の曲の画像を1フレームでも残すと、一覧や Album Flow でちらつきになる
    val state = remember(track?.id, track?.mtime, px) { mutableStateOf(track?.let { loader.cached(it, px) }) }
    LaunchedEffect(track?.id, track?.mtime, px) {
        if (track != null && state.value == null) state.value = loader.load(track, px)
    }
    val bitmap = state.value
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.MusicNote, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun ClickRow(title: String, summary: String? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = summary?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, summary: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/** 選択肢から1つ選ぶ行。タップするとダイアログを開く。 */
@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickRow(title, label(selected)) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(options) { o ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = o == selected) { onSelect(o); open = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == selected, onClick = { onSelect(o); open = false })
                            Spacer(Modifier.width(8.dp))
                            Text(label(o))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** 文字列を入力する行。 */
@Composable
fun TextRow(title: String, value: String, summary: String = value, hint: String = "", onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickRow(title, summary.ifEmpty { stringResource(R.string.not_set) }) { open = true }
    if (open) TextInputDialog(title, value, hint, onDismiss = { open = false }) { onChange(it); open = false }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hint.isNotEmpty()) Text(hint, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 数値をスライダーで選ぶ行。指を離したときに確定する。 */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(current), style = MaterialTheme.typography.bodyMedium)
        }
        val steps = ((range.endInclusive - range.start) / step).toInt() - 1
        Slider(
            value = current,
            onValueChange = { current = (Math.round(it / step) * step) },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}

@Composable
fun Divider() = HorizontalDivider()

@Composable
fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** タブの名前。空なら種類の名前を表示言語で出す（ver.6）。 */
fun io.github.oke1943.azsaplayer.settings.ViewConfig.displayTitle(res: android.content.res.Resources): String =
    title.ifEmpty { if (kind == io.github.oke1943.azsaplayer.settings.ViewKind.CUSTOM && customTag.isNotEmpty()) customTag else res.getString(kind.labelRes) }

@Composable
fun io.github.oke1943.azsaplayer.settings.ViewConfig.displayTitle(): String = displayTitle(LocalContext.current.resources)
