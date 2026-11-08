package io.github.oke1943.azsaplayer.library

import android.content.Context
import android.media.MediaCodecList
import android.os.storage.StorageManager
import androidx.room.withTransaction
import io.github.oke1943.azsaplayer.data.AppDatabase
import io.github.oke1943.azsaplayer.data.FieldValueEntity
import io.github.oke1943.azsaplayer.data.PersonEntity
import io.github.oke1943.azsaplayer.data.ScanErrorEntity
import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.data.TrackFieldValueEntity
import io.github.oke1943.azsaplayer.data.TrackPersonEntity
import io.github.oke1943.azsaplayer.data.TrackTagEntity
import io.github.oke1943.azsaplayer.settings.LibrarySettings
import io.github.oke1943.azsaplayer.tag.RawTags
import io.github.oke1943.azsaplayer.tag.TagLibNative
import io.github.oke1943.azsaplayer.tag.TagRules
import java.io.File

/** スキャンの段階。文言は画面側で表示言語に合わせて出す。 */
enum class ScanPhase { IDLE, FINDING, READING, FINISHING, DONE, NO_FOLDERS, FAILED }

data class ScanProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val phase: ScanPhase = ScanPhase.IDLE,
    /** 失敗したときの詳細（例外のメッセージ）。 */
    val detail: String = "",
)

/**
 * フォルダを再帰的に走査してDBに登録する（7章）。
 * 2回目以降は更新日時と大きさを比べ、変わったファイルだけを読み直す。
 */
class LibraryScanner(private val context: Context, private val db: AppDatabase) {

    private val dao = db.libraryDao()

    suspend fun scan(settings: LibrarySettings, full: Boolean, onProgress: (ScanProgress) -> Unit) {
        onProgress(ScanProgress(true, phase = ScanPhase.FINDING))
        // 「音楽ファイルの読み取りのみ」では、Androidのメディア一覧に登録済みのファイルしかフォルダに見えない。
        // 先に指定フォルダを登録させてから一覧を作る（未登録の曲が読み込まれない問題の対策）。
        if (!io.github.oke1943.azsaplayer.StorageAccess.hasAllFiles()) registerWithMediaStore(settings.folders)
        val files = collectFiles(settings)
        val existing = dao.fileStamps().associateBy { it.path }

        val targets = files.filter { f ->
            val old = existing[f.path]
            full || old == null || old.size != f.length() || old.mtime != f.lastModified()
        }
        val seen = files.mapTo(HashSet()) { it.path }
        val removed = existing.values.filter { it.path !in seen }.map { it.id }
        if (removed.isNotEmpty()) dao.deleteTracksFully(removed)

        val persons = HashMap<String, Long>().apply { dao.allPersons().forEach { put(it.nameKey, it.id) } }
        val values = HashMap<String, Long>().apply { dao.allFieldValues().forEach { put(it.field + "\u0000" + it.valueKey, it.id) } }
        val errors = ArrayList<ScanErrorEntity>()
        val now = System.currentTimeMillis()

        targets.chunked(100).forEachIndexed { chunkIndex, chunk ->
            // 1曲の読み取りに失敗しても、残りの曲の取り込みは続ける
            val read = chunk.map { f ->
                f to try { TagLibNative.read(f.path) } catch (e: Exception) { RawTags.error(e.message ?: e.javaClass.simpleName) }
            }
            for ((file, tags) in read) {
                if (tags.error != null) {
                    errors += ScanErrorEntity(file.path, tags.error, now)
                    continue
                }
                try {
                    db.withTransaction { store(file, tags, settings, existing[file.path]?.let { it.id to it.dateAdded }, now, persons, values) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("LibraryScanner", "store failed: ${file.path}", e)
                    errors += ScanErrorEntity(file.path, e.message ?: e.javaClass.simpleName, now)
                    // 取り消された書き込みの分を捨てて、DBの内容から作り直す
                    persons.clear(); dao.allPersons().forEach { persons[it.nameKey] = it.id }
                    values.clear(); dao.allFieldValues().forEach { values[it.field + "\u0000" + it.valueKey] = it.id }
                }
            }
            onProgress(ScanProgress(true, (chunkIndex * 100 + chunk.size), targets.size, ScanPhase.READING))
        }

        onProgress(ScanProgress(true, targets.size, targets.size, ScanPhase.FINISHING))
        db.withTransaction {
            dao.deleteOrphanPersons()
            dao.deleteOrphanFieldValues()
            recomputePersonSortKeys()
            recomputeAlbums()
            if (full) dao.clearScanErrors()
            dao.insertScanErrors(errors)
        }
        onProgress(ScanProgress(false, targets.size, targets.size, ScanPhase.DONE))
    }

    private suspend fun store(
        file: File,
        tags: RawTags,
        settings: LibrarySettings,
        old: Pair<Long, Long>?,
        now: Long,
        persons: HashMap<String, Long>,
        values: HashMap<String, Long>,
    ) {
        val parsed = TrackParser.parse(file.path, tags, settings)
        val rg = parsed.replayGain
        val entity = TrackEntity(
            id = old?.first ?: 0,
            path = file.path,
            folder = file.parent ?: "",
            size = file.length(),
            mtime = file.lastModified(),
            dateAdded = old?.second ?: now,
            title = parsed.title,
            titleSort = parsed.titleSort,
            album = parsed.album,
            albumSort = parsed.albumSort,
            albumKey = parsed.albumKey,
            artistSortText = parsed.artistSortText,
            albumArtistSortText = parsed.albumArtistSortText,
            year = parsed.year,
            date = parsed.date,
            trackNo = parsed.trackNo,
            trackTotal = parsed.trackTotal,
            discNo = parsed.discNo,
            discTotal = parsed.discTotal,
            durationMs = tags.lengthMs,
            codec = tags.codec,
            sampleRate = tags.sampleRate,
            bitsPerSample = tags.bitsPerSample,
            channels = tags.channels,
            bitrate = tags.bitrate,
            playable = Playability.isPlayable(tags.codec),
            hasEmbeddedArt = tags.hasPicture,
            rgTrackGain = rg.trackGain,
            rgTrackPeak = rg.trackPeak,
            rgAlbumGain = rg.albumGain,
            rgAlbumPeak = rg.albumPeak,
        )
        val upserted = dao.upsertTrack(entity)
        val id = if (entity.id != 0L) entity.id else upserted

        dao.deleteTrackPersons(listOf(id))
        dao.deleteTrackFieldValues(listOf(id))
        dao.deleteTrackTags(listOf(id))

        dao.insertTrackPersons(parsed.persons.map { ref ->
            val key = TagRules.identityKey(ref.name)
            val personId = persons[key] ?: dao.insertPerson(PersonEntity(name = ref.name, nameKey = key, sortKey = ref.name))
                .also { persons[key] = it }
            TrackPersonEntity(id, ref.role, ref.position, personId, ref.sortValue)
        })
        dao.insertTrackFieldValues(parsed.fieldValues.flatMap { (field, list) ->
            list.mapIndexed { i, v ->
                val vk = TagRules.identityKey(v)
                val mapKey = field + "\u0000" + vk
                val valueId = values[mapKey] ?: dao.insertFieldValue(FieldValueEntity(field = field, value = v, valueKey = vk))
                    .also { values[mapKey] = it }
                TrackFieldValueEntity(id, field, i, valueId)
            }
        })
        dao.insertTrackTags(tags.properties.flatMap { (k, list) -> list.mapIndexed { i, v -> TrackTagEntity(id, k, i, v) } })
    }

    /**
     * 人物のソートキー（4.2）：ライブラリ全体で対応づいたソート値のうち最も多いもの。
     * 同数のときはパス順で先の曲の値（毎回同じ結果になるように）。ソート値がなければ表示名。
     */
    private suspend fun recomputePersonSortKeys() {
        val best = dao.personSortCandidates().groupBy { it.personId }.mapValues { (_, c) -> pickSortKey(c) }
        for (p in dao.allPersons()) {
            val key = best[p.id] ?: p.name
            if (key != p.sortKey) dao.setPersonSortKey(p.id, key)
        }
    }

    private suspend fun recomputeAlbums() {
        val tracks = dao.allTracks()
        val persons = dao.allPersons().associateBy { it.id }
        val tp = dao.allTrackPersons().groupBy { it.trackId }
        dao.clearAlbums()
        dao.insertAlbums(tracks.groupBy { it.albumKey }.map { (key, list) ->
            val first = list.first()
            val aa = tp[first.id].orEmpty().filter { it.role == io.github.oke1943.azsaplayer.data.Role.ALBUM_ARTIST }
                .ifEmpty { tp[first.id].orEmpty().filter { it.role == io.github.oke1943.azsaplayer.data.Role.ARTIST } }
                .sortedBy { it.position }.mapNotNull { persons[it.personId]?.name }
            io.github.oke1943.azsaplayer.data.AlbumEntity(
                albumKey = key,
                title = first.album,
                titleSort = first.albumSort,
                albumArtist = aa.joinToString("; "),
                year = list.mapNotNull { it.year }.minOrNull(),
                trackCount = list.size,
                artTrackId = (list.firstOrNull { it.hasEmbeddedArt } ?: first).id,
            )
        })
    }

    /** 指定フォルダ以下をAndroidのメディア一覧に登録させ、終わるまで待つ（時間がかかりすぎたら先へ進む）。 */
    private suspend fun registerWithMediaStore(folders: List<String>) {
        if (folders.isEmpty()) return
        kotlinx.coroutines.withTimeoutOrNull(10 * 60_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                var left = folders.size
                android.media.MediaScannerConnection.scanFile(context, folders.toTypedArray(), null) { _, _ ->
                    synchronized(this@LibraryScanner) {
                        left--
                        if (left == 0 && cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                }
            }
        }
    }

    private fun collectFiles(settings: LibrarySettings): List<File> {
        // 指定したフォルダだけを読む（ver.2）
        val roots = settings.folders.map { File(it) }
        val excluded = settings.excludedFolders.map { File(it).absolutePath.trimEnd('/') }
        val out = ArrayList<File>()
        val visited = HashSet<String>()

        fun walk(dir: File, depth: Int) {
            if (depth > 32) return
            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return
            if (!visited.add(canonical)) return
            val path = dir.absolutePath.trimEnd('/')
            if (excluded.any { path == it || path.startsWith("$it/") }) return
            val children = dir.listFiles() ?: return
            if (settings.respectNomedia && children.any { it.name == ".nomedia" }) return
            for (c in children) {
                if (c.name.startsWith(".")) continue
                if (c.isDirectory) {
                    // アプリ専用領域（/Android/data など）は対象外
                    if (depth == 0 && c.name == "Android") continue
                    walk(c, depth + 1)
                } else if (c.extension.lowercase() in AUDIO_EXTENSIONS) {
                    out += c
                }
            }
        }
        roots.forEach { walk(it, 0) }
        return out
    }

    companion object {
        val AUDIO_EXTENSIONS = setOf(
            "flac", "mp3", "m4a", "mp4", "aac", "alac", "ogg", "oga", "opus",
            "wav", "aif", "aiff", "aifc", "ape", "wv", "dsf", "dff", "mpc",
        )

        fun storageRoots(context: Context): List<File> =
            context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }
    }
}

/**
 * 人物のソートキーの選び方（4.2）：最も多く対応づいたソート値。同数ならパス順で先の曲の値。
 */
fun pickSortKey(candidates: List<io.github.oke1943.azsaplayer.data.PersonSortCandidate>): String? =
    candidates.sortedWith(compareByDescending<io.github.oke1943.azsaplayer.data.PersonSortCandidate> { it.count }.thenBy { it.firstPath })
        .firstOrNull()?.sortValue

/** 再生できる形式の判定（第2章）。初版はExoPlayer標準で再生できる形式のみ。 */
object Playability {
    private val alacSupported: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("audio/alac", ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    fun isPlayable(codec: String): Boolean = when (codec) {
        "MP3", "FLAC", "Vorbis", "Opus", "AAC", "WAV" -> true
        "ALAC" -> alacSupported
        else -> false
    }
}
