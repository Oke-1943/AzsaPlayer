package io.github.oke1943.azsaplayer.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.oke1943.azsaplayer.MainActivity
import io.github.oke1943.azsaplayer.container
import io.github.oke1943.azsaplayer.data.PlayHistoryEntity
import io.github.oke1943.azsaplayer.settings.ReplayGainSettings
import io.github.oke1943.azsaplayer.widget.WidgetState
import io.github.oke1943.azsaplayer.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap

/**
 * バックグラウンド再生（第2章・8章）。
 * ロック画面・通知・物理ボタン・イヤホンのボタンはMediaSessionが受け付ける。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var queueStore: QueueStore

    @Volatile private var rgSettings = ReplayGainSettings()
    @Volatile private var shuffleTracks = false
    @Volatile private var joinSeparator = "; "
    private var shuffleMode = ShuffleMode.OFF
    /** アルバム単位シャッフル前の並び（キュー内の各MediaItem → 元の位置）。 */
    private var originalOrder: IdentityHashMap<MediaItem, Int>? = null
    private var restored = false
    private var lastError: String? = null

    override fun attachBaseContext(base: Context) {
        // 通知・エラー表示の文言もアプリ内で選んだ言語にする
        super.attachBaseContext(io.github.oke1943.azsaplayer.AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        val c = applicationContext.container
        queueStore = QueueStore(this)

        // 音質重視：デコード結果は32ビット浮動小数点のまま、リプレイゲイン以外の加工をせずに出力する
        val renderers = PureRenderersFactory(this) { context ->
            GainAudioSink(
                DefaultAudioSink.Builder(context).setEnableFloatOutput(true).build(),
                resolver = ::resolveGain,
                settings = { rgSettings },
            )
        }
        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus= */ true,
            )
            // イヤホンが抜けたら一時停止
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEvents())

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setBitmapLoader(ArtBitmapLoader())
            .setSessionActivity(openApp)
            .build()

        scope.launch {
            c.settings.settings.collect { s ->
                rgSettings = s.replayGain
                joinSeparator = s.display.joinSeparator
                publishExtras()
            }
        }
        scope.launch {
            if (!c.library.loaded.value) withContext(Dispatchers.IO) { c.library.reload() }
            if (c.settings.current().playback.resumeOnStart) restoreQueue()
            restored = true
        }
        handler.post(savePositionTick)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    /** ホーム画面ウィジェットに今の曲と再生状態を伝える（ver.7）。 */
    private fun publishWidget(playing: Boolean = player.playWhenReady) {
        val item = player.currentMediaItem
        val m = item?.mediaMetadata
        Widgets.publish(
            this,
            WidgetState(
                title = m?.title?.toString().orEmpty(),
                artist = m?.artist?.toString().orEmpty(),
                trackId = item?.mediaId?.toLongOrNull() ?: -1,
                playing = playing && item != null,
            ),
        )
    }

    override fun onDestroy() {
        publishWidget(playing = false)
        saveQueue()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---- リプレイゲイン ----

    /** 再生スレッドから呼ばれる。タイムラインは不変なので安全に読める。 */
    private fun resolveGain(timeline: Timeline, periodUid: Any?): GainContext? {
        if (periodUid == null || timeline.isEmpty) return null
        val period = Timeline.Period()
        val index = timeline.getIndexOfPeriod(periodUid)
        if (index == C.INDEX_UNSET) return null
        timeline.getPeriod(index, period)
        val w = period.windowIndex
        val window = Timeline.Window()
        fun itemAt(i: Int): MediaItem? = if (i == C.INDEX_UNSET) null else timeline.getWindow(i, window).mediaItem
        val shuffle = shuffleTracks
        val current = itemAt(w) ?: return null
        val info = MediaItems.replayGain(current)
        val key = MediaItems.albumKey(current)
        val prev = MediaItems.albumKey(itemAt(timeline.getPreviousWindowIndex(w, Player.REPEAT_MODE_OFF, shuffle)))
        val next = MediaItems.albumKey(itemAt(timeline.getNextWindowIndex(w, Player.REPEAT_MODE_OFF, shuffle)))
        return GainContext(info, ReplayGainCalc.isAlbumContext(shuffle, key, prev, next))
    }

    /** 再生中画面に出す現在曲の補正量・シャッフル状態などをコントローラへ伝える。 */
    private fun publishExtras() {
        val label = if (player.mediaItemCount == 0) "" else {
            val idx = player.currentMediaItemIndex
            val tl = player.currentTimeline
            val cur = player.currentMediaItem
            fun keyAt(i: Int) = if (i == C.INDEX_UNSET || tl.isEmpty) null else MediaItems.albumKey(tl.getWindow(i, Timeline.Window()).mediaItem)
            val ctx = !tl.isEmpty && ReplayGainCalc.isAlbumContext(
                player.shuffleModeEnabled, MediaItems.albumKey(cur),
                keyAt(tl.getPreviousWindowIndex(idx, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)),
                keyAt(tl.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)),
            )
            cur?.let { ReplayGainCalc.decide(MediaItems.replayGain(it), rgSettings, ctx).label(resources) } ?: ""
        }
        session.setSessionExtras(Bundle().apply {
            putString(EXTRA_RG_LABEL, label)
            putString(EXTRA_SHUFFLE, shuffleMode.name)
            putString(EXTRA_ERROR, lastError)
        })
    }

    // ---- シャッフル（曲単位・アルバム単位） ----

    private fun setShuffleMode(mode: ShuffleMode) {
        if (mode == shuffleMode) return
        if (shuffleMode == ShuffleMode.ALBUMS) restoreOriginalOrder()
        when (mode) {
            ShuffleMode.OFF -> player.shuffleModeEnabled = false
            ShuffleMode.TRACKS -> player.shuffleModeEnabled = true
            ShuffleMode.ALBUMS -> {
                player.shuffleModeEnabled = false
                shuffleByAlbum()
            }
        }
        shuffleMode = mode
        publishExtras()
        saveQueue()
    }

    /** 現在のアルバムを先頭に残し、ほかのアルバムを無作為に並べ替える。曲の順はアルバム内で保つ。 */
    private fun shuffleByAlbum() {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        if (items.size < 2) return
        originalOrder = IdentityHashMap<MediaItem, Int>().apply { items.forEachIndexed { i, m -> put(m, i) } }
        val current = player.currentMediaItem
        val groups = LinkedHashMap<String, MutableList<MediaItem>>()
        for (m in items) groups.getOrPut(MediaItems.albumKey(m) ?: m.mediaId) { ArrayList() } += m
        val currentKey = current?.let { MediaItems.albumKey(it) ?: it.mediaId }
        val first = groups[currentKey].orEmpty()
        val rest = groups.filterKeys { it != currentKey }.values.shuffled()
        reorderTo(first + rest.flatten())
    }

    private fun restoreOriginalOrder() {
        val order = originalOrder ?: return
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        reorderTo(items.sortedBy { order[it] ?: Int.MAX_VALUE })
        originalOrder = null
    }

    /** 再生を止めずに並びを変える（moveMediaItemの繰り返し）。 */
    private fun reorderTo(target: List<MediaItem>) {
        for ((to, item) in target.withIndex()) {
            var from = -1
            for (i in to until player.mediaItemCount) if (player.getMediaItemAt(i) === item) { from = i; break }
            if (from > to) player.moveMediaItem(from, to)
        }
    }

    // ---- キューの保存・復元 ----

    private val savePositionTick = object : Runnable {
        override fun run() {
            if (player.isPlaying) saveQueue()
            handler.postDelayed(this, 10_000)
        }
    }

    private fun saveQueue() {
        if (!restored) return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        val original = originalOrder?.let { order ->
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }.sortedBy { order[it] ?: Int.MAX_VALUE }
                .mapNotNull { it.mediaId.toLongOrNull() }
        } ?: emptyList()
        queueStore.save(
            SavedQueue(ids, player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0), player.repeatMode, shuffleMode, original),
        )
    }

    private fun restoreQueue() {
        val saved = queueStore.load() ?: return
        if (saved.trackIds.isEmpty() || player.mediaItemCount > 0) return
        val items = resolve(saved.trackIds)
        if (items.isEmpty()) return
        player.setMediaItems(items, saved.index.coerceIn(0, items.size - 1), saved.positionMs)
        player.repeatMode = saved.repeatMode
        player.shuffleModeEnabled = saved.shuffle == ShuffleMode.TRACKS
        shuffleMode = saved.shuffle
        if (saved.shuffle == ShuffleMode.ALBUMS && saved.originalOrder.isNotEmpty()) {
            val remaining = items.toMutableList()
            val order = IdentityHashMap<MediaItem, Int>()
            saved.originalOrder.forEachIndexed { i, id ->
                val m = remaining.firstOrNull { it.mediaId == id.toString() } ?: return@forEachIndexed
                remaining.remove(m)
                order[m] = i
            }
            originalOrder = order
        }
        player.prepare()
        publishExtras()
    }

    private fun resolve(ids: List<Long>): List<MediaItem> {
        val c = applicationContext.container
        val lib = c.library.snapshot.value
        return ids.mapNotNull { lib.tracksById[it] }.filter { it.e.playable }.map { MediaItems.build(it, joinSeparator) }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                shuffleTracks = player.shuffleModeEnabled
                publishExtras()
                saveQueue()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) saveQueue()
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                publishWidget()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastError = null
            val id = mediaItem?.mediaId?.toLongOrNull() ?: return
            val c = applicationContext.container
            c.scope.launch { c.db.historyDao().insert(PlayHistoryEntity(trackId = id, playedAt = System.currentTimeMillis())) }
        }

        override fun onPlayerError(error: PlaybackException) {
            lastError = getString(io.github.oke1943.azsaplayer.R.string.playback_error, player.currentMediaItem?.mediaMetadata?.title ?: "", error.errorCodeName)
            publishExtras()
            // 次の曲があれば進める
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SET_SHUFFLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val ids = mediaItems.mapNotNull { it.mediaId.toLongOrNull() }
            return Futures.immediateFuture(resolve(ids))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_SET_SHUFFLE) {
                val mode = runCatching { ShuffleMode.valueOf(args.getString(ARG_MODE) ?: "") }.getOrNull()
                if (mode != null) setShuffleMode(mode)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = queueStore.load()
            val items = saved?.let { resolve(it.trackIds) }.orEmpty()
            if (saved == null || items.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException())
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, saved.index.coerceIn(0, items.size - 1), saved.positionMs),
            )
        }
    }

    /** 通知・ロック画面のアートワーク。musicapp-art://track/<id> をキャッシュ付きで読む。 */
    private inner class ArtBitmapLoader : BitmapLoader {
        override fun supportsMimeType(mimeType: String) = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
            return if (bmp != null) Futures.immediateFuture(bmp) else Futures.immediateFailedFuture(IllegalArgumentException())
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            val c = applicationContext.container
            val id = uri.lastPathSegment?.toLongOrNull()
            c.scope.launch(Dispatchers.IO) {
                val track = id?.let { c.library.snapshot.value.tracksById[it] }
                val bmp = track?.let { c.artwork.load(it.e, 512) }
                if (bmp != null) future.set(bmp) else future.setException(IllegalStateException("no artwork"))
            }
            return future
        }
    }

    companion object {
        const val CMD_SET_SHUFFLE = "musicapp.SET_SHUFFLE"
        const val ARG_MODE = "mode"
        const val EXTRA_RG_LABEL = "rg.label"
        const val EXTRA_SHUFFLE = "shuffle.mode"
        const val EXTRA_ERROR = "error"
    }
}
