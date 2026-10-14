package io.github.oke1943.azsaplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import io.github.oke1943.azsaplayer.MainActivity
import io.github.oke1943.azsaplayer.R
import io.github.oke1943.azsaplayer.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** ウィジェットに出す内容。アプリが終了していても最後の曲を表示できるよう保存しておく。 */
data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val trackId: Long = -1,
    val playing: Boolean = false,
) {
    companion object {
        private const val PREFS = "widget_state"

        fun load(context: Context): WidgetState {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetState(p.getString("title", "") ?: "", p.getString("artist", "") ?: "", p.getLong("track", -1), p.getBoolean("playing", false))
        }

        fun save(context: Context, s: WidgetState) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", s.title).putString("artist", s.artist).putLong("track", s.trackId).putBoolean("playing", s.playing)
                .apply()
        }
    }
}

/** 標準の再生コントロール（ジャケット・曲名・前／再生・一時停止／次）。 */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
}

/** レコードプレーヤー風。再生中は針がレコードに載る。 */
class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
}

/** ジャケットを大きく見せる正方形。 */
class CoverWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshAsync(context, goAsync())
}

/**
 * ウィジェットのボタン操作。アプリが終了していても動くよう、再生サービスに接続してから操作する
 * （接続すると再生サービスが起動し、保存されたキューが復元される）。
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                c.player.connect()
                withTimeoutOrNull(4000) {
                    while (!c.player.state.value.connected) delay(50)
                    // 起動直後はキューの復元を少し待つ
                    var waited = 0
                    while (c.player.state.value.queue.isEmpty() && waited < 1500) { delay(50); waited += 50 }
                }
                withContext(Dispatchers.Main) {
                    when (action) {
                        Widgets.ACTION_TOGGLE -> c.player.togglePlay()
                        Widgets.ACTION_NEXT -> c.player.next()
                        Widgets.ACTION_PREVIOUS -> c.player.previous()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object Widgets {
    const val ACTION_TOGGLE = "io.github.oke1943.azsaplayer.widget.TOGGLE"
    const val ACTION_NEXT = "io.github.oke1943.azsaplayer.widget.NEXT"
    const val ACTION_PREVIOUS = "io.github.oke1943.azsaplayer.widget.PREVIOUS"

    private const val ACCENT = 0xFF6750A4.toInt()

    /** 再生状態が変わったときに呼ぶ。状態を保存し、置かれているすべてのウィジェットを描き直す。 */
    fun publish(context: Context, state: WidgetState) {
        if (state == WidgetState.load(context)) return
        WidgetState.save(context, state)
        refreshAsync(context, null)
    }

    fun refreshAsync(context: Context, pending: BroadcastReceiver.PendingResult?) {
        val app = context.applicationContext
        app.container.scope.launch(Dispatchers.IO) {
            try {
                refresh(app)
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        fun ids(cls: Class<*>) = manager.getAppWidgetIds(ComponentName(context, cls))
        val player = ids(PlayerWidget::class.java)
        val record = ids(RecordWidget::class.java)
        val cover = ids(CoverWidget::class.java)
        if (player.isEmpty() && record.isEmpty() && cover.isEmpty()) return

        val state = WidgetState.load(context)
        val art = loadArt(context, state.trackId, 384)
        if (player.isNotEmpty()) manager.updateAppWidget(player, playerViews(context, state, art))
        if (record.isNotEmpty()) manager.updateAppWidget(record, recordViews(context, state, art))
        if (cover.isNotEmpty()) manager.updateAppWidget(cover, coverViews(context, state, art))
    }

    private suspend fun loadArt(context: Context, trackId: Long, size: Int): Bitmap? {
        if (trackId < 0) return null
        val c = context.container
        if (!c.library.loaded.value) c.library.reload()
        val track = c.library.snapshot.value.tracksById[trackId] ?: return null
        return c.artwork.load(track.album.artTrack.e, size)
    }

    private fun title(context: Context, s: WidgetState) = s.title.ifEmpty { context.getString(R.string.nothing_playing) }

    private fun playerViews(context: Context, s: WidgetState, art: Bitmap?) = RemoteViews(context.packageName, R.layout.widget_player).apply {
        if (art != null) setImageViewBitmap(R.id.widget_art, WidgetArt.rounded(art, 192, 24f)) else setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        setTextViewText(R.id.widget_title, title(context, s))
        setTextViewText(R.id.widget_artist, s.artist)
        setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        setContentDescription(R.id.widget_play, context.getString(if (s.playing) R.string.pause else R.string.play))
        setOnClickPendingIntent(R.id.widget_play, action(context, ACTION_TOGGLE))
        setOnClickPendingIntent(R.id.widget_next, action(context, ACTION_NEXT))
        setOnClickPendingIntent(R.id.widget_prev, action(context, ACTION_PREVIOUS))
        setOnClickPendingIntent(R.id.widget_root, openApp(context))
    }

    private fun recordViews(context: Context, s: WidgetState, art: Bitmap?) = RemoteViews(context.packageName, R.layout.widget_record).apply {
        setImageViewBitmap(R.id.widget_record, WidgetArt.turntable(art, s.playing, ACCENT))
        setContentDescription(R.id.widget_record, context.getString(if (s.playing) R.string.pause else R.string.play))
        setTextViewText(R.id.widget_title, title(context, s))
        setTextViewText(R.id.widget_artist, s.artist)
        setOnClickPendingIntent(R.id.widget_record, action(context, ACTION_TOGGLE))
        setOnClickPendingIntent(R.id.widget_text, openApp(context))
    }

    private fun coverViews(context: Context, s: WidgetState, art: Bitmap?) = RemoteViews(context.packageName, R.layout.widget_cover).apply {
        setImageViewBitmap(R.id.widget_cover, WidgetArt.cover(art, ACCENT))
        setTextViewText(R.id.widget_title, title(context, s))
        setTextViewText(R.id.widget_artist, s.artist)
        setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        setContentDescription(R.id.widget_play, context.getString(if (s.playing) R.string.pause else R.string.play))
        setOnClickPendingIntent(R.id.widget_play, action(context, ACTION_TOGGLE))
        setOnClickPendingIntent(R.id.widget_cover, openApp(context))
    }

    private fun action(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
