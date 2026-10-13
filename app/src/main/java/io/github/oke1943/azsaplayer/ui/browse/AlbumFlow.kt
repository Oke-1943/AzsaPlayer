package io.github.oke1943.azsaplayer.ui.browse

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.settings.AppSettings
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.ui.common.ArtworkImage
import io.github.oke1943.azsaplayer.ui.common.EmptyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import io.github.oke1943.azsaplayer.R
import androidx.compose.ui.res.stringResource

/**
 * Album Flow。ジャケットを立体的に並べ、左右になぞるか下の仮想スクロールホイールを回してアルバムを選ぶ。
 * 中央のジャケット（またはホイールの中央ボタン）でアルバム画面を開く。
 */
@Composable
fun AlbumFlow(
    view: ViewConfig,
    settings: AppSettings,
    onOpenGroup: (GroupEntry) -> Unit,
    onOpenTrackInfo: (Long) -> Unit,
    bottomPadding: Dp,
) {
    val entries = rememberEntries(view, 0, emptyList(), settings)?.filterIsInstance<GroupEntry>()
    if (entries == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (entries.isEmpty()) {
        EmptyMessage(stringResource(R.string.empty_albums))
        return
    }
    val c = LocalContext.current.container
    val playerState by c.player.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val n = entries.size
    val join = settings.display.joinSeparator
    var saved by rememberSaveable { mutableIntStateOf(0) }
    var pos by remember { mutableFloatStateOf(saved.coerceIn(0, n - 1).toFloat()) }
    /** 目標のアルバム（ホイールを回している間は表示位置より先に進む）。 */
    var target by remember { mutableIntStateOf(saved.coerceIn(0, n - 1)) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var menuFor by remember { mutableStateOf<GroupEntry?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(n) {
        if (pos > n - 1) pos = (n - 1).toFloat()
        target = target.coerceIn(0, n - 1)
    }

    fun settleTo(t: Int, velocity: Float = 0f) {
        settleJob?.cancel()
        target = t.coerceIn(0, n - 1)
        val goal = target
        settleJob = scope.launch {
            animate(pos, goal.toFloat(), velocity, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { v, _ -> pos = v }
            saved = goal
        }
    }

    fun step(k: Int) {
        val t = (target + k).coerceIn(0, n - 1)
        if (t != target) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            settleTo(t)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        val wheel = min(maxWidth.value * 0.62f, maxHeight.value * 0.36f).dp
        // ジャケットは「映り込み込み1.3倍＋情報3行＋ホイール」が収まる大きさ
        val cover = min(maxWidth.value * 0.48f, (maxHeight.value - wheel.value - 96f) / 1.3f).coerceAtLeast(80f).dp
        val centerGap = cover * 0.62f
        val sideStep = cover * 0.2f
        val density = LocalDensity.current
        val stepPx = with(density) { sideStep.toPx() }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cover * 1.3f)
                    .clipToBounds()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { dx -> pos = (pos - dx / stepPx).coerceIn(0f, (n - 1).toFloat()) },
                        onDragStarted = { settleJob?.cancel() },
                        onDragStopped = { velocity ->
                            val v = -velocity / stepPx
                            settleTo((pos + v * 0.18f).roundToInt(), v)
                        },
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                val first = floor(pos - 6).toInt().coerceAtLeast(0)
                val last = ceil(pos + 6).toInt().coerceAtMost(n - 1)
                for (i in first..last) {
                    // アルバムごとに枠を固定する（枠の使い回しで画像が入れ替わるとちらつく）
                    key(entries[i].key) {
                        val d = i - pos
                        val ad = abs(d)
                        val x = if (ad <= 1f) centerGap * d else (centerGap + sideStep * (ad - 1f)) * sign(d)
                        val near = min(ad, 1f)
                        CoverItem(
                            entry = entries[i],
                            size = cover,
                            modifier = Modifier
                                .zIndex(-ad)
                                .offset(x = x)
                                .graphicsLayer {
                                    rotationY = -sign(d) * near * 58f
                                    val s = 1f - 0.16f * near
                                    scaleX = s
                                    scaleY = s
                                    cameraDistance = 14f * density.density
                                    alpha = if (ad > 5f) (6f - ad).coerceIn(0f, 1f) else 1f
                                },
                            onClick = { if (ad < 0.1f) onOpenGroup(entries[i]) else settleTo(i) },
                            onLongClick = { if (ad < 0.5f) menuFor = entries[i] },
                        )
                    }
                }
            }
            val current = entries[target.coerceIn(0, n - 1)]
            Text(
                current.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 39.dp),
            )
            Text(
                current.album?.albumArtists?.joinToString(join) { it.name } ?: "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
            Text(
                listOfNotNull(current.year?.toString(), "${target + 1} / $n").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            ScrollWheel(
                size = wheel,
                playing = playerState.isPlaying,
                onRotateSteps = { k -> step(k) },
                onCenter = { onOpenGroup(entries[target]) },
                onMenu = { menuFor = entries[target] },
                onPrevious = { step(-1) },
                onNext = { step(1) },
                onPlayPause = { c.player.togglePlay() },
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    menuFor?.let { e ->
        EntryMenu(entry = e, allTrackIds = emptyList(), inPlaylist = null, onDismiss = { menuFor = null }, onOpenTrackInfo = onOpenTrackInfo)
    }
}

/** ジャケット1枚と、その下の映り込み。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverItem(entry: GroupEntry, size: Dp, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val art = entry.artTrack?.e
    val bg = MaterialTheme.colorScheme.surface
    Column(modifier) {
        ArtworkImage(art, size, corner = 2.dp, modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
        Box(Modifier.size(size, size * 0.3f).clipToBounds()) {
            ArtworkImage(art, size, corner = 2.dp, modifier = Modifier.graphicsLayer { scaleY = -1f; alpha = 0.35f })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.3f), bg))))
        }
    }
}

/**
 * 仮想スクロールホイール。
 * 輪の上で指を回すと選択が動く（時計回りで次）。速く回すほど一度に大きく進む。
 * 上：MENU、左：前、右：次、下：再生／一時停止、中央：決定。
 */
@Composable
private fun ScrollWheel(
    size: Dp,
    playing: Boolean,
    onRotateSteps: (Int) -> Unit,
    onCenter: () -> Unit,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    val button = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    var pressed by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .size(size)
            .pointerInput(Unit) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val outer = this.size.width / 2f
                val inner = outer * 0.38f
                val stepDeg = 22f
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startR = hypot(down.position.x - center.x, down.position.y - center.y)
                    if (startR > outer) return@awaitEachGesture
                    down.consume()
                    var lastAngle = angleOf(down.position, center)
                    var lastTime = down.uptimeMillis
                    var accum = 0f
                    var rotated = 0f
                    val zone = if (startR < inner) "center" else zoneOf(lastAngle)
                    pressed = zone
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val r = hypot(change.position.x - center.x, change.position.y - center.y)
                        if (r < inner * 0.6f) continue // 中心付近は角度が暴れるので無視
                        val a = angleOf(change.position, center)
                        var delta = a - lastAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1)
                        lastAngle = a
                        lastTime = change.uptimeMillis
                        accum += delta
                        rotated += abs(delta)
                        if (rotated > 12f) pressed = null
                        val steps = (accum / stepDeg).toInt()
                        if (steps != 0) {
                            accum -= steps * stepDeg
                            // 速く回すほど大きく進む（大きなライブラリ向け）
                            val speed = abs(delta) / dt // 度/ms
                            val mult = when {
                                speed > 1.4f -> 6
                                speed > 0.8f -> 3
                                else -> 1
                            }
                            onRotateSteps(steps * mult)
                        }
                    }
                    if (rotated <= 12f) {
                        when (zone) {
                            "center" -> onCenter()
                            "top" -> onMenu()
                            "left" -> onPrevious()
                            "right" -> onNext()
                            "bottom" -> onPlayPause()
                        }
                    }
                    pressed = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(ring, r)
            drawCircle(outline, r, style = Stroke(1.dp.toPx()))
            when (pressed) {
                "top" -> Offset(r, r * 0.31f)
                "left" -> Offset(r * 0.31f, r)
                "right" -> Offset(r * 1.69f, r)
                "bottom" -> Offset(r, r * 1.69f)
                else -> null
            }?.let { drawCircle(outline.copy(alpha = 0.5f), r * 0.2f, center = it) }
            drawCircle(if (pressed == "center") outline else button, r * 0.38f)
            drawCircle(outline, r * 0.38f, style = Stroke(1.dp.toPx()))
        }
        val edge = size * 0.155f
        Text("MENU", Modifier.align(Alignment.TopCenter).padding(top = edge - 8.dp), color = label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous_album), tint = label, modifier = Modifier.align(Alignment.CenterStart).padding(start = edge - 12.dp))
        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_album), tint = label, modifier = Modifier.align(Alignment.CenterEnd).padding(end = edge - 12.dp))
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play_pause), tint = label,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = edge - 12.dp),
        )
    }
}

/** 中心から見た角度（度、右が0で時計回りに増える）。 */
private fun angleOf(p: Offset, center: Offset): Float =
    Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat()

/** 押した位置がどのボタンか（上・右・下・左）。 */
private fun zoneOf(angle: Float): String = when {
    angle >= -135f && angle < -45f -> "top"
    angle >= -45f && angle < 45f -> "right"
    angle >= 45f && angle < 135f -> "bottom"
    else -> "left"
}
