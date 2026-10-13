package io.github.oke1943.azsaplayer.playback

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 終了後も再生位置とキューを保持するための保存形式（8章）。 */
@Serializable
data class SavedQueue(
    val trackIds: List<Long> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val repeatMode: Int = 0,
    val shuffle: ShuffleMode = ShuffleMode.OFF,
    /** アルバム単位シャッフル前の並び（元に戻すため）。 */
    val originalOrder: List<Long> = emptyList(),
)

@Serializable
enum class ShuffleMode(@androidx.annotation.StringRes val labelRes: Int) {
    OFF(io.github.oke1943.azsaplayer.R.string.shuffle_off),
    TRACKS(io.github.oke1943.azsaplayer.R.string.shuffle_tracks),
    ALBUMS(io.github.oke1943.azsaplayer.R.string.shuffle_albums),
}

class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun load(): SavedQueue? = runCatching { json.decodeFromString(SavedQueue.serializer(), file.readText()) }.getOrNull()

    fun save(q: SavedQueue) {
        runCatching {
            val tmp = File(file.parentFile, "queue.json.tmp")
            tmp.writeText(json.encodeToString(SavedQueue.serializer(), q))
            tmp.renameTo(file)
        }
    }
}
