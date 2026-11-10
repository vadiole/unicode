package vadiole.unicode.data

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import vadiole.unicode.BuildConfig
import vadiole.unicode.search.CharSearch
import vadiole.unicode.search.SearchIndex
import vadiole.unicode.search.SearchIndexBuilder
import vadiole.unicode.search.SearchMatch
import vadiole.unicode.search.SearchResults
import vadiole.unicode.search.SearchSource
import vadiole.unicode.search.UnicodeDataSource

class UnicodeSearch(private val storage: UnicodeStorage) {
    private val lock = Object()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sources by lazy { arrayOf<SearchSource>(UnicodeDataSource(storage.data(), storage::meanings)) }
    private var thread: Thread? = null
    private var pendingQuery: String? = null
    private var pendingLimit = 0
    private var pendingCallback: ((SearchResults) -> Unit)? = null
    private var generation = 0
    private var shownChanged = false
    private var readyCallbacks: ArrayList<() -> Unit>? = ArrayList()
    @Volatile
    private var shown: IntArray? = null
    @Volatile
    private var engine: CharSearch? = null

    fun prepare() = synchronized(lock) {
        if (thread != null) return
        thread = Thread(::run, "search").apply {
            isDaemon = true
            start()
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
            lock.notifyAll()
        }
    }

    fun cancel() = synchronized(lock) {
        generation++
        pendingQuery = null
        pendingCallback = null
    }

    fun match(results: SearchResults, position: Int): SearchMatch? = engine?.match(results, position)

    fun hasIndex(data: UnicodeData): Boolean {
        val file = File(storage.cacheDir, INDEX_NAME)
        return try {
            file.exists() && file.inputStream().use { SearchIndex.hasKey(it, indexKey(data)) }
        } catch (e: Exception) {
            false
        }
    }

    private fun indexKey(data: UnicodeData) = "${SearchIndex.FORMAT};${data.checksum};${storage.buildKey};${sources.joinToString(",") { it.key }}"

    internal fun createEngine(): CharSearch {
        val start = SystemClock.elapsedRealtime()
        val data = storage.data()
        val key = indexKey(data)
        val file = File(storage.cacheDir, INDEX_NAME)
        var index = try {
            if (file.exists()) SearchIndex.load(file.readBytes(), key) else null
        } catch (e: Exception) {
            null
        }
        val cached = index != null
        if (index == null) {
            val builder = SearchIndexBuilder()
            for (source in sources) {
                builder.beginSource()
                source.emit(builder)
            }
            index = builder.build()
        }
        storage.releaseMeanings()
        val engine = CharSearch(data, index, sources)
        if (BuildConfig.DEBUG) {
            val source = if (cached) "cache" else "build"
            Log.d(TAG, "index ready from $source in ${SystemClock.elapsedRealtime() - start}ms, ${SystemClock.uptimeMillis() - processStart()}ms after process start, ${index.bytes} bytes")
        }
        if (!cached) {
            val temp = File(storage.cacheDir.also { it.mkdirs() }, "$INDEX_NAME.tmp")
            try {
                temp.outputStream().use { index.save(it, key) }
                temp.renameTo(file)
            } catch (e: Exception) {
                temp.delete()
            }
        }
        return engine
    }

    private fun processStart() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else 0L

    private fun run() {
        val engine = createEngine()
        this.engine = engine
        val ready = synchronized(lock) { readyCallbacks.also { readyCallbacks = null } }
        ready?.forEach { mainHandler.post(it) }
        val data = storage.data()
        var warmUp = 0
        while (true) {
            var query: String? = null
            var limit = 0
            var callback: ((SearchResults) -> Unit)? = null
            var current = 0
            var codePoints: IntArray? = null
            var update = false
            synchronized(lock) {
                while (pendingQuery == null && warmUp == WARM_UP.size) lock.wait()
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
            }
            val text = query
            if (text == null) {
                engine.search(WARM_UP[warmUp++], PAGE_SIZE)
                continue
            }
            if (update) {
                engine.shown = codePoints?.let { shown ->
                    val bits = LongArray((data.size + 63) ushr 6)
                    for (codePoint in shown) {
                        val char = data.indexOf(codePoint)
                        if (char >= 0) bits[char ushr 6] = bits[char ushr 6] or (1L shl char)
                    }
                    bits
                }
            }
            val start = SystemClock.elapsedRealtimeNanos()
            val results = engine.search(text, limit)
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
