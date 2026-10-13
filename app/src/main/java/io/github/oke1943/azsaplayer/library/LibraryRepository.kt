package io.github.oke1943.azsaplayer.library

import android.content.Context
import io.github.oke1943.azsaplayer.data.AppDatabase
import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.settings.SettingsRepository
import io.github.oke1943.azsaplayer.tag.TagRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** ライブラリの読み込み・スキャンの窓口。画面はメモリ上のスナップショットを参照する。 */
class LibraryRepository(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val dao = db.libraryDao()
    private val scanner = LibraryScanner(context.applicationContext, db)
    private val scanMutex = Mutex()
    private var scanJob: Job? = null

    private val _snapshot = MutableStateFlow(LibrarySnapshot.EMPTY)
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _scan = MutableStateFlow(ScanProgress())
    val scan: StateFlow<ScanProgress> = _scan.asStateFlow()

    private val customCache = ConcurrentHashMap<String, Map<Long, List<String>>>()

    val scanErrors get() = dao.scanErrors()

    suspend fun reload() {
        _snapshot.value = load()
        customCache.clear()
        _loaded.value = true
    }

    fun startScan(full: Boolean) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch(Dispatchers.IO) {
            if (settings.current().library.folders.isEmpty()) {
                _scan.value = ScanProgress(false, phase = ScanPhase.NO_FOLDERS)
                return@launch
            }
            scanMutex.withLock {
                try {
                    scanner.scan(settings.current().library, full) { _scan.value = it }
                } catch (e: Exception) {
                    _scan.value = ScanProgress(false, phase = ScanPhase.FAILED, detail = e.message ?: "")
                }
                reload()
            }
        }
    }

    /** カスタムビュー用の値（3.3の規則で分割）。初回のみDBから読む。 */
    fun customValues(tag: String): Map<Long, List<String>> = customCache.getOrPut(tag.uppercase()) {
        val seps = kotlinx.coroutines.runBlocking { settings.current().library.splitSeparators }
        kotlinx.coroutines.runBlocking { dao.tagsByKey(tag.uppercase()) }
            .groupBy { it.trackId }
            .mapValues { (_, rows) -> TagRules.split(rows.sortedBy { it.position }.map { it.value }, seps) }
    }

    suspend fun allTagKeys(): List<String> = dao.allTagKeys()

    suspend fun tagsOfTrack(id: Long) = dao.tagsOfTrack(id)

    private suspend fun load(): LibrarySnapshot = loadSnapshot(dao)
}

/** DBからメモリ上のライブラリを組み立てる。 */
suspend fun loadSnapshot(dao: io.github.oke1943.azsaplayer.data.LibraryDao): LibrarySnapshot {
    val persons = dao.allPersons().associate { it.id to LibPerson(it.id, it.name, it.sortKey) }
    val tp = dao.allTrackPersons().groupBy { it.trackId }
    val values = dao.allFieldValues().associateBy { it.id }
    val genres = dao.allTrackFieldValues().filter { it.field == "GENRE" }.groupBy { it.trackId }
    val albums = HashMap<String, LibAlbum>()

    val tracks = dao.allTracks().map { e ->
        val rows = tp[e.id].orEmpty()
        fun role(r: Int) = rows.filter { it.role == r }.sortedBy { it.position }.mapNotNull { persons[it.personId] }
        val t = LibTrack(
            e = e,
            artists = role(Role.ARTIST),
            albumArtists = role(Role.ALBUM_ARTIST),
            composers = role(Role.COMPOSER),
            genres = genres[e.id].orEmpty().sortedBy { it.position }.mapNotNull { values[it.valueId]?.value },
        )
        val album = albums.getOrPut(e.albumKey) { LibAlbum(e.albumKey, e.album, e.albumSort, t.effectiveAlbumArtists) }
        album.tracks += t
        t.album = album
        t
    }
    return LibrarySnapshot(tracks, albums.values.toList(), persons)
}
