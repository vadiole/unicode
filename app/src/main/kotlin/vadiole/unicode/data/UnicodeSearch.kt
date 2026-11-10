package vadiole.unicode.data

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.InputStream
import vadiole.unicode.BuildConfig
import vadiole.unicode.search.AnnotationSource
import vadiole.unicode.search.CharSearch
import vadiole.unicode.search.SearchIndex
import vadiole.unicode.search.SearchResults
import vadiole.unicode.search.SearchSource
import vadiole.unicode.search.UnicodeDataSource

class UnicodeSearch(private val storage: UnicodeStorage) {
    private val lock = Object()
    private val buildLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var thread: Thread? = null
    private var pendingQuery: String? = null
    private var pendingLimit = 0
    private var pendingCallback: ((SearchResults) -> Unit)? = null
    private var activeQuery: String? = null
    private var activeLimit = 0
    private var activeCallback: ((SearchResults) -> Unit)? = null
    private var generation = 0
    private var shownChanged = false
    private var unsaved: (() -> Unit)? = null
    private var readyCallbacks: ArrayList<() -> Unit>? = ArrayList()
    private var request = 0
    private var requestedKey: String? = null
    @Volatile
    private var shown: IntArray? = null
    @Volatile
    private var engine: CharSearch? = null
    @Volatile
    var annotations: Annotations? = null
        private set

    fun prepare() = synchronized(lock) {
        if (thread != null) return
        thread = Thread(::run, "search").apply {
            isDaemon = true
            start()
        }
    }

    fun ensure(stream: InputStream) {
        val key = try {
            Annotations.readKey(stream) ?: Annotations.NONE
        } catch (e: Exception) {
            Annotations.NONE
        }
        val id = synchronized(lock) {
            if (key == requestedKey) {
                0
            } else {
                requestedKey = key
                ++request
            }
        }
        if (id == 0) {
            stream.close()
            return
        }
        Thread({ build(id, key, stream) }, "search-index").start()
    }

    private fun build(id: Int, key: String, stream: InputStream) {
        val annotations = try {
            stream.use { if (key == Annotations.NONE) null else Annotations.read(key, it) }
        } catch (e: Exception) {
            null
        }
        synchronized(buildLock) {
            if (synchronized(lock) { id != request }) return
            unsaved = null
            val engine = createEngine(annotations, cached = true)
            val ready = synchronized(lock) {
                if (id != request) return
                this.annotations = annotations
                this.engine = engine
                if (activeQuery != null) {
                    pendingQuery = activeQuery
                    pendingLimit = activeLimit
                    pendingCallback = activeCallback
                }
                lock.notifyAll()
                readyCallbacks.also { readyCallbacks = null }
            }
            ready?.forEach { mainHandler.post(it) }
            unsaved?.invoke()
        }
    }

    fun whenReady(callback: () -> Unit) {
        synchronized(lock) {
            readyCallbacks?.let {
                it.add(callback)
                return
            }
        }
        callback()
    }

    fun setShown(codePoints: IntArray?) = synchronized(lock) {
        shown = codePoints
        shownChanged = true
    }

    fun isShown(codePoint: Int): Boolean {
        val shown = shown ?: return storage.data().indexOf(codePoint) >= 0
        return shown.binarySearch(codePoint) >= 0
    }

    fun search(query: String, limit: Int, callback: (SearchResults) -> Unit) {
        prepare()
        synchronized(lock) {
            generation++
            pendingQuery = query
            pendingLimit = limit
            pendingCallback = callback
            activeQuery = query
            activeLimit = limit
            activeCallback = callback
            lock.notifyAll()
        }
    }

    fun cancel() = synchronized(lock) {
        generation++
        pendingQuery = null
        pendingCallback = null
        activeQuery = null
        activeCallback = null
    }

    fun hasIndex(data: UnicodeData): Boolean {
        val file = File(storage.cacheDir, INDEX_NAME)
        return try {
            file.exists() && file.inputStream().use { SearchIndex.hasKeyPrefix(it, indexKey(data, "")) }
        } catch (e: Exception) {
            false
        }
    }

    private fun indexKey(data: UnicodeData, sources: String) = "${SearchIndex.FORMAT};${data.checksum};${storage.buildKey};$sources"

    internal fun createEngine(annotations: Annotations?, cached: Boolean): CharSearch {
        val start = SystemClock.elapsedRealtime()
        val data = storage.data()
        val english = UnicodeDataSource(data, storage::meanings)
        val sources = if (annotations == null) arrayOf<SearchSource>(english) else arrayOf(english, AnnotationSource(data, annotations))
        val key = indexKey(data, SearchIndex.sourcesKey(sources))
        val file = File(storage.cacheDir, INDEX_NAME)
        var index = try {
            if (cached && file.exists()) SearchIndex.load(file.readBytes(), key) else null
        } catch (e: Exception) {
            null
        }
        val loaded = index != null
        if (index == null) index = SearchIndex.build(sources)
        storage.releaseMeanings()
        val engine = CharSearch(data, index, sources)
        if (BuildConfig.DEBUG) {
            val source = if (loaded) "cache" else "build"
            Log.d(TAG, "index ${SearchIndex.sourcesKey(sources)} ready from $source in ${SystemClock.elapsedRealtime() - start}ms, ${SystemClock.uptimeMillis() - processStart()}ms after process start, ${index.bytes} bytes")
        }
        if (cached && !loaded) unsaved = { save(index, key) }
        return engine
    }

    private fun save(index: SearchIndex, key: String) {
        val temp = File(storage.cacheDir.also { it.mkdirs() }, "$INDEX_NAME.tmp")
        try {
            temp.outputStream().use { index.save(it, key) }
            temp.renameTo(File(storage.cacheDir, INDEX_NAME))
        } catch (e: Exception) {
            temp.delete()
        }
    }

    private fun processStart() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else 0L

    private fun run() {
        val data = storage.data()
        var engine: CharSearch? = null
        var bits: LongArray? = null
        var warmUp = 0
        while (true) {
            var query: String? = null
            var limit = 0
            var callback: ((SearchResults) -> Unit)? = null
            var current = 0
            var codePoints: IntArray? = null
            var update = false
            val search = synchronized(lock) {
                while (this.engine == null || (pendingQuery == null && warmUp == WARM_UP.size && this.engine === engine)) lock.wait()
                if (pendingQuery != null) {
                    query = pendingQuery
                    limit = pendingLimit
                    callback = pendingCallback
                    current = generation
                    pendingQuery = null
                    pendingCallback = null
                    update = shownChanged
                    shownChanged = false
                    codePoints = shown
                }
                requireNotNull(this.engine)
            }
            if (search !== engine) {
                engine = search
                warmUp = 0
            }
            val text = query
            if (text == null) {
                search.search(WARM_UP[warmUp++], PAGE_SIZE)
                continue
            }
            if (update) {
                bits = codePoints?.let { shown ->
                    val bits = LongArray((data.size + 63) ushr 6)
                    for (codePoint in shown) {
                        val char = data.indexOf(codePoint)
                        if (char >= 0) bits[char ushr 6] = bits[char ushr 6] or (1L shl char)
                    }
                    bits
                }
            }
            search.shown = bits
            val start = SystemClock.elapsedRealtimeNanos()
            val results = search.search(text, limit)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "search \"$text\" limit $limit: ${(SystemClock.elapsedRealtimeNanos() - start) / 1000}us, ${results.total} results")
            }
            val deliver = callback
            mainHandler.post {
                if (synchronized(lock) { current == generation }) deliver?.invoke(results)
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 64
        private const val TAG = "UnicodeSearch"
        private const val INDEX_NAME = "search"
        private val WARM_UP = arrayOf("ar", "st", "co", "e", "a", "s")
    }
}
