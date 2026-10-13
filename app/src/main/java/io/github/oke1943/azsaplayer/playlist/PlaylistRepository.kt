package io.github.oke1943.azsaplayer.playlist

import io.github.oke1943.azsaplayer.data.AppDatabase
import io.github.oke1943.azsaplayer.data.PlaylistEntity
import io.github.oke1943.azsaplayer.library.LibraryRepository
import io.github.oke1943.azsaplayer.library.PlaylistInfo
import io.github.oke1943.azsaplayer.settings.PathRule
import io.github.oke1943.azsaplayer.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** プレイリスト（アプリ内DBにのみ保存。M3Uへの書き出しは行わない）。 */
class PlaylistRepository(
    db: AppDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private val dao = db.playlistDao()

    val playlists: Flow<List<PlaylistInfo>> = combine(dao.playlists(), dao.allEntries()) { lists, entries ->
        val byList = entries.groupBy { it.playlistId }
        lists.map { p -> PlaylistInfo(p.id, p.name, byList[p.id].orEmpty().sortedBy { it.position }.map { it.trackId }) }
    }

    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
        if (trackIds.isNotEmpty()) dao.replaceTracks(id, trackIds, now)
        return id
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    suspend fun append(id: Long, trackIds: List<Long>) {
        dao.replaceTracks(id, dao.trackIds(id) + trackIds, System.currentTimeMillis())
    }

    suspend fun setTracks(id: Long, trackIds: List<Long>) = dao.replaceTracks(id, trackIds, System.currentTimeMillis())

    suspend fun trackIds(id: Long) = dao.trackIds(id)

    data class ImportResult(val playlistId: Long, val matched: Int, val unmatched: List<String>)

    /**
     * M3U/M3U8の読み込み（8章）。[sourceDir] はプレイリストファイルのあるフォルダ（相対パスの基準）。
     */
    suspend fun importM3u(name: String, bytes: ByteArray, sourceDir: String?): ImportResult {
        val rules = settings.current().library.playlistPathRules
        val text = decode(bytes)
        val entries = M3u.parse(text)
        val snapshot = library.snapshot.value
        val byPath = HashMap<String, Long>()
        val bySuffix = HashMap<String, MutableList<Long>>()
        for (t in snapshot.tracks) {
            byPath[t.e.path.lowercase()] = t.id
            bySuffix.getOrPut(M3u.suffix(t.e.path)) { ArrayList() } += t.id
        }
        val matched = ArrayList<Long>()
        val unmatched = ArrayList<String>()
        for (raw in entries) {
            val path = M3u.resolve(raw, rules, sourceDir)
            val id = byPath[path.lowercase()] ?: bySuffix[M3u.suffix(path)]?.singleOrNull()
            if (id != null) matched += id else unmatched += raw
        }
        val id = create(name, matched)
        return ImportResult(id, matched.size, unmatched)
    }

    private fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString()
        } catch (_: CharacterCodingException) {
            // 日本語版Windowsで書き出された .m3u はShift_JIS（CP932）のことがある
            String(body, Charset.forName("windows-31j"))
        }
    }
}

/** M3Uの解析とパスの置き換え（純粋なロジック）。 */
object M3u {
    fun parse(text: String): List<String> = text.lineSequence()
        .map { it.trim().removePrefix("\uFEFF") }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

    /**
     * パスの先頭部分を規則で置き換える（例：`D:\Music\` → `/storage/XXXX-XXXX/Music/`）。
     * 大文字小文字と区切り文字（\ と /）の違いは無視する。相対パスは [sourceDir] から解決する。
     */
    fun resolve(raw: String, rules: List<PathRule>, sourceDir: String?): String {
        val p = raw.replace('\\', '/')
        for (r in rules) {
            val from = r.from.replace('\\', '/')
            if (from.isNotEmpty() && p.startsWith(from, ignoreCase = true)) {
                val to = r.to.replace('\\', '/')
                return joinPath(to, p.substring(from.length))
            }
        }
        val isAbsolute = p.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(p)
        return if (!isAbsolute && sourceDir != null) normalize(joinPath(sourceDir, p)) else p
    }

    fun suffix(path: String): String = path.replace('\\', '/').lowercase().split('/').takeLast(3).joinToString("/")

    private fun joinPath(a: String, b: String) = a.trimEnd('/') + "/" + b.trimStart('/')

    private fun normalize(path: String): String {
        val out = ArrayList<String>()
        for (seg in path.split('/')) when (seg) {
            "." -> {}
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            else -> out += seg
        }
        return out.joinToString("/")
    }
}
