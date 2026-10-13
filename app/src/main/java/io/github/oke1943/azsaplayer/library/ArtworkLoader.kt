package io.github.oke1943.azsaplayer.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.tag.TagLibNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * アートワーク（7章）：埋め込み画像を優先し、なければ同じフォルダの cover / folder / front（jpg・png）。
 * 縮小画像をディスクとメモリにキャッシュする。
 */
class ArtworkLoader(context: Context) {

    private val dir = File(context.cacheDir, "art").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val decodeLimit = Semaphore(3)

    fun cached(track: TrackEntity, sizePx: Int): Bitmap? = memory.get(key(track, bucket(sizePx)))

    suspend fun load(track: TrackEntity, sizePx: Int): Bitmap? {
        val size = bucket(sizePx)
        val key = key(track, size)
        memory.get(key)?.let { return it }
        if (key in missing) return null
        return withContext(Dispatchers.IO) {
            decodeLimit.withPermit {
                val file = File(dir, "$key.jpg")
                val bmp = if (file.exists()) BitmapFactory.decodeFile(file.path) else {
                    val bytes = sourceBytes(track)
                    bytes?.let { decodeScaled(it, size) }?.also { b ->
                        runCatching { file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
                    }
                }
                if (bmp == null) missing += key else memory.put(key, bmp)
                bmp
            }
        }
    }

    /** 通知・ロック画面用（大きめ）。 */
    fun loadBlocking(track: TrackEntity, sizePx: Int): Bitmap? = kotlinx.coroutines.runBlocking { load(track, sizePx) }

    fun clearMissing() = missing.clear()

    private fun sourceBytes(track: TrackEntity): ByteArray? {
        if (track.hasEmbeddedArt) TagLibNative.picture(track.path)?.let { return it }
        val folder = File(track.path).parentFile ?: return null
        val files = folder.listFiles() ?: return null
        for (base in FOLDER_NAMES) {
            val f = files.firstOrNull { it.isFile && it.nameWithoutExtension.equals(base, true) && it.extension.lowercase() in IMAGE_EXT }
            if (f != null) return runCatching { f.readBytes() }.getOrNull()
        }
        return null
    }

    private fun decodeScaled(bytes: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= size) return decoded
        val scale = size.toFloat() / longest
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun key(t: TrackEntity, size: Int) = "${t.id}_${t.mtime}_$size"

    private fun bucket(px: Int): Int = BUCKETS.firstOrNull { it >= px } ?: BUCKETS.last()

    companion object {
        private val BUCKETS = intArrayOf(96, 192, 384, 768)
        private val FOLDER_NAMES = listOf("cover", "folder", "front")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png")
    }
}
