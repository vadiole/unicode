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
    private var thread: Thread? = null
    private var pendingQuery: String? = null
    private var pendingLimit = 0
    private var pendingCallback: ((SearchResults) -> Unit)? = null
    private var generation = 0
    private var shownChanged = false
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

    fun createEngine(): CharSearch {
        val start = SystemClock.elapsedRealtime()
        val data = storage.data()
        val sources = arrayOf<SearchSource>(UnicodeDataSource(data, storage::meanings))
        val key = "${data.version};${BuildConfig.VERSION_CODE};${sources.joinToString(",") { it.key }}"
        val file = File(storage.cacheDir, INDEX_NAME)
        var index = try {
            if (file.exists()) file.inputStream().buffered(1 shl 16).use { SearchIndex.load(it, key, file.length()) } else null
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
            storage.releaseMeanings()
            val temp = File(storage.cacheDir.also { it.mkdirs() }, "$INDEX_NAME.tmp")
            try {
                temp.outputStream().buffered(1 shl 16).use { index.save(it, key) }
                temp.renameTo(file)
            } catch (e: Exception) {
                temp.delete()
            }
        }
        val engine = CharSearch(data, index, sources)
        engine.search(WARM_UP_QUERY, PAGE_SIZE)
        if (BuildConfig.DEBUG) {
            val source = if (cached) "cache" else "build"
            Log.d(TAG, "index ready from $source in ${SystemClock.elapsedRealtime() - start}ms, ${SystemClock.uptimeMillis() - processStart()}ms after process start, ${index.bytes} bytes")
        }
        return engine
    }

    private fun processStart() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else 0L

    private fun run() {
        val engine = createEngine()
        this.engine = engine
        val data = storage.data()
        while (true) {
            val query: String
            val limit: Int
            val callback: (SearchResults) -> Unit
            val current: Int
            synchronized(lock) {
                while (pendingQuery == null) lock.wait()
                query = pendingQuery!!
                limit = pendingLimit
                callback = pendingCallback!!
                current = generation
                pendingQuery = null
                pendingCallback = null
                if (shownChanged) {
                    shownChanged = false
                    engine.shown = shown?.let { codePoints ->
                        val bits = LongArray((data.size + 63) ushr 6)
                        for (codePoint in codePoints) {
                            val char = data.indexOf(codePoint)
                            if (char >= 0) bits[char ushr 6] = bits[char ushr 6] or (1L shl char)
                        }
                        bits
                    }
                }
            }
            val start = SystemClock.elapsedRealtimeNanos()
            val results = engine.search(query, limit)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "search \"$query\" limit $limit: ${(SystemClock.elapsedRealtimeNanos() - start) / 1000}us, ${results.total} results")
            }
            mainHandler.post {
                if (synchronized(lock) { current == generation }) callback(results)
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 64
        private const val TAG = "UnicodeSearch"
        private const val INDEX_NAME = "search"
        private const val WARM_UP_QUERY = "a e"
    }
}
