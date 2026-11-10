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
            else -> throw IllegalArgumentException("Unknown method $method, expected capture or verify")
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

    private fun liveEngine(): SearchEngine {
        val search = UnicodeApp.unicodeStorage.search.createEngine()
        return SearchEngine { query, limit ->
            val results = search.search(query, limit)
            SearchHits(results.codePoints, results.total)
        }
    }

    private fun asset(context: Context, name: String) = context.assets.open("search/$name").bufferedReader().use { it.readText() }

    private fun output(context: Context, name: String) = File(File(context.filesDir, "search").also { it.mkdirs() }, name)

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
