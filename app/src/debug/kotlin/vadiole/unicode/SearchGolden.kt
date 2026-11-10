package vadiole.unicode

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CountDownLatch
import vadiole.unicode.search.SearchEngine
import vadiole.unicode.search.SearchHits
import vadiole.unicode.search.SearchSuite
import vadiole.unicode.search.SuiteFiles

class SearchGolden : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = requireNotNull(context)
        val created = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { created.countDown() }
        created.await()
        val result = when (method) {
            "capture" -> capture(context)
            "verify" -> verify(context)
            "time" -> time(context)
            "memory" -> memory()
            else -> throw IllegalArgumentException("Unknown method $method, expected capture, verify, time or memory")
        }
        return Bundle().apply { putString("result", result) }
    }

    private fun capture(context: Context): String {
        val engine = liveEngine()
        val queries = SearchSuite.parseQueries(asset(context, "queries.txt")).distinctBy { it.text }
        val goldens = queries.map { SearchSuite.capture(engine, it.text) }
        val text = goldens.joinToString("") { SearchSuite.format(it) + "\n" }
        output(context, "goldens.txt").writeText(text)
        return "captured ${goldens.size} queries, ${goldens.count { it.error != null }} errors"
    }

    private fun verify(context: Context): String {
        val report = SearchSuite.verify(liveEngine(), SuiteFiles.load { asset(context, it) })
        output(context, "verify.txt").writeText(report.text)
        return report.summary
    }

    private fun time(context: Context): String {
        val search = UnicodeApp.unicodeStorage.search.createEngine()
        val queries = SearchSuite.parseQueries(asset(context, "queries.txt")).distinctBy { it.text }
        val times = LongArray(queries.size)
        var slowest = 0
        repeat(TIME_ROUNDS) {
            queries.forEachIndexed { index, query ->
                val start = System.nanoTime()
                search.search(query.text, SearchSuite.FIRST_LIMIT)
                val elapsed = System.nanoTime() - start
                if (it == 0 || elapsed < times[index]) times[index] = elapsed
            }
        }
        for (index in times.indices) if (times[index] > times[slowest]) slowest = index
        val sorted = times.sortedArray()
        return "queries ${times.size}, best of $TIME_ROUNDS: median ${sorted[sorted.size / 2] / 1000}us, p99 ${sorted[sorted.size * 99 / 100] / 1000}us, " +
                "worst ${times[slowest] / 1000}us for ${SearchSuite.escape(queries[slowest].text).take(40)}"
    }

    private fun memory(): String {
        val storage = UnicodeApp.unicodeStorage
        val base = usedHeap()
        storage.data()
        val data = usedHeap()
        val search = storage.search.createEngine()
        search.search("a", SearchSuite.FIRST_LIMIT)
        val index = usedHeap()
        val start = System.nanoTime()
        val meanings = storage.meanings()
        val loaded = (System.nanoTime() - start) / 1000000
        val all = usedHeap()
        return "java heap: data ${data - base}, index and working arrays ${index - data}, meanings ${all - index} (${meanings.size} loaded in ${loaded}ms)"
    }

    private fun usedHeap(): Long {
        val runtime = Runtime.getRuntime()
        repeat(3) {
            runtime.gc()
            Thread.sleep(100)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun liveEngine(): SearchEngine {
        val search = UnicodeApp.unicodeStorage.search.createEngine()
        return SearchEngine { query, limit ->
            val results = search.search(query, limit)
            SearchHits(results.codePoints, results.total)
        }
    }

    private fun asset(context: Context, name: String) = context.assets.open("search/$name").bufferedReader().use { it.readText() }

    private fun output(context: Context, name: String) = File(File(context.filesDir, "search").also { it.mkdirs() }, name)

    private companion object {
        const val TIME_ROUNDS = 5
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
