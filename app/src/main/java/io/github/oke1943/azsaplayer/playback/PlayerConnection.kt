package io.github.oke1943.azsaplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 画面に見せる再生状態。 */
data class PlayerUiState(
    val connected: Boolean = false,
    val current: MediaItem? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<MediaItem> = emptyList(),
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: ShuffleMode = ShuffleMode.OFF,
    val rgLabel: String = "",
    val error: String? = null,
)

/** 画面側からPlaybackServiceを操作する窓口（MediaController）。 */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
                override fun onDisconnected(controller: MediaController) {
                    this@PlayerConnection.controller = null
                    _state.value = PlayerUiState()
                }
            })
            .buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
        }, MoreExecutors.directExecutor())

        scope.launch(Dispatchers.Main) {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(250)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val extras = c.sessionExtras
        _state.value = PlayerUiState(
            connected = true,
            current = c.currentMediaItem,
            currentIndex = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            durationMs = c.duration.coerceAtLeast(0),
            queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) },
            repeatMode = c.repeatMode,
            shuffle = runCatching { ShuffleMode.valueOf(extras.getString(PlaybackService.EXTRA_SHUFFLE) ?: "") }
                .getOrDefault(if (c.shuffleModeEnabled) ShuffleMode.TRACKS else ShuffleMode.OFF),
            rgLabel = extras.getString(PlaybackService.EXTRA_RG_LABEL) ?: "",
            error = extras.getString(PlaybackService.EXTRA_ERROR),
        )
        _position.value = c.currentPosition
    }

    private fun items(ids: List<Long>) = ids.map { MediaItem.Builder().setMediaId(it.toString()).build() }

    /** 曲の並びを再生する。[shuffle] 指定時は曲単位シャッフルにする。 */
    fun play(ids: List<Long>, startIndex: Int = 0, shuffle: ShuffleMode? = null) {
        val c = controller ?: return
        if (ids.isEmpty()) return
        if (shuffle != null) setShuffle(ShuffleMode.OFF)
        c.setMediaItems(items(ids), startIndex.coerceIn(0, ids.size - 1), 0)
        c.prepare()
        c.play()
        if (shuffle != null && shuffle != ShuffleMode.OFF) {
            setShuffle(shuffle)
            if (shuffle == ShuffleMode.TRACKS) c.seekToDefaultPosition(0)
        }
    }

    fun playNext(ids: List<Long>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(ids)
        c.addMediaItems(c.currentMediaItemIndex + 1, items(ids))
    }

    fun addToQueue(ids: List<Long>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(ids)
        c.addMediaItems(items(ids))
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun skipTo(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }
    fun move(from: Int, to: Int) { controller?.moveMediaItem(from, to) }
    fun remove(index: Int) { controller?.removeMediaItem(index) }
    fun clearQueue() { controller?.clearMediaItems() }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun cycleShuffle() {
        setShuffle(
            when (_state.value.shuffle) {
                ShuffleMode.OFF -> ShuffleMode.TRACKS
                ShuffleMode.TRACKS -> ShuffleMode.ALBUMS
                ShuffleMode.ALBUMS -> ShuffleMode.OFF
            },
        )
    }

    fun setShuffle(mode: ShuffleMode) {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SET_SHUFFLE, Bundle.EMPTY),
            Bundle().apply { putString(PlaybackService.ARG_MODE, mode.name) },
        )
    }
}
