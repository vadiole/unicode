package vadiole.unicode

import android.animation.Animator
import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import vadiole.unicode.data.CharObj
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.Nav
import vadiole.unicode.ui.NavigationView
import vadiole.unicode.ui.common.SearchBar
import vadiole.unicode.ui.common.TopBar
import vadiole.unicode.ui.settings.AboutScreen
import vadiole.unicode.ui.settings.SettingsScreen
import vadiole.unicode.ui.table.TableController
import vadiole.unicode.ui.table.TableScreen
import vadiole.unicode.ui.table.TableView
import vadiole.unicode.ui.table.dock.DockController

internal class ColorPrototypeSnapshot private constructor(
    private val row: Int,
    private val offset: Int,
    private val title: String,
    private val search: Boolean,
    private val query: String,
    private val selection: Int,
    private val keyboard: Boolean,
    private val screen: String?,
    private val screenScroll: Int,
    private val resultScroll: android.os.Parcelable?,
    private val details: Int?,
    private val deleted: String,
) {
    fun restore(activity: Activity) {
        val navigation = activity.field<NavigationView>("navigationViewNullable")
        val table = navigation.field<TableScreen>("tableScreen")
        val dock = navigation.field<DockController>("dockController")
        dock.javaClass.getDeclaredField("deleted").apply { isAccessible = true }.set(dock, deleted)
        val controller = navigation.field<TableController>("tableController")
        val view = table.field<TableView>("tableView")
        val deadline = SystemClock.uptimeMillis() + 10000
        fun ready() {
            if (activity.isDestroyed) return
            if (controller.blocks.isEmpty() || view.adapter?.itemCount == 0) {
                if (SystemClock.uptimeMillis() < deadline) view.postDelayed({ ready() }, 16)
                else Log.w("ColorPrototype", "Timed out restoring table position")
                return
            }
            view.post {
                if (activity.isDestroyed) return@post
                view.scrollToPositionTop(row.coerceAtLeast(0), offset)
                table.field<TopBar>("topBar").setTitle(title)
                if (search) {
                    val bar = table.field<SearchBar>("searchBar")
                    bar.expand()
                    val field = bar.field<EditText>("searchField")
                    field.setText(query)
                    field.setSelection(selection.coerceIn(0, query.length))
                    val results = table.field<RecyclerView>("searchResultView")
                    fun restoreResults() {
                        if (activity.isDestroyed) return
                        if (table.field<Job?>("searchJob")?.isActive == true) {
                            if (SystemClock.uptimeMillis() < deadline) results.postDelayed({ restoreResults() }, 16)
                            else Log.w("ColorPrototype", "Timed out restoring search position")
                        } else {
                            results.layoutManager?.onRestoreInstanceState(resultScroll)
                        }
                    }
                    results.post { restoreResults() }
                    if (!keyboard) {
                        field.clearFocus()
                        field.postDelayed({
                            if (!activity.isDestroyed) activity.getSystemService(InputMethodManager::class.java)
                                .hideSoftInputFromWindow(field.windowToken, 0)
                        }, 100)
                    }
                }
                val nav = navigation.field<Nav>("nav")
                if (screen == SettingsScreen::class.java.name || screen == AboutScreen::class.java.name) {
                    navigation.javaClass.getDeclaredMethod("showSettings").apply { isAccessible = true }.invoke(navigation)
                    nav.field<Animator?>("transition")?.end()
                    if (screen == AboutScreen::class.java.name) {
                        navigation.field<SettingsScreen.Delegate>("settingsDelegate").onAbout()
                        nav.field<Animator?>("transition")?.end()
                    }
                    val current = nav.current()
                    current?.post { current.findScroll()?.scrollTo(0, screenScroll) }
                }
                details?.let { navigation.showDetailsBottomSheet(CodePoint(it), skipAnimation = true) }
            }
        }
        ready()
    }

    companion object {
        fun capture(activity: Activity): ColorPrototypeSnapshot {
            val navigation = activity.field<NavigationView>("navigationViewNullable")
            val table = navigation.field<TableScreen>("tableScreen")
            val view = table.field<TableView>("tableView")
            val bar = table.field<SearchBar>("searchBar")
            val field = bar.field<EditText>("searchField")
            val current = navigation.field<Nav>("nav").current()
            val detailsOpen = navigation.field<Boolean>("isDetailsOpenOrOpening")
            val details = if (detailsOpen) navigation.detailsSheet?.field<CharObj?>("charObj")?.codePointRaw else null
            return ColorPrototypeSnapshot(
                view.firstVisibleRow,
                view.firstVisibleRowOffset,
                table.field<TopBar>("topBar").field<TextView>("titleView").text.toString(),
                table.isSearchVisible(),
                field.text.toString(),
                field.selectionStart,
                ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                current?.javaClass?.name,
                current?.findScroll()?.scrollY ?: 0,
                table.field<RecyclerView>("searchResultView").layoutManager?.onSaveInstanceState(),
                details,
                navigation.field<DockController>("dockController").field<String>("deleted"),
            )
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.field(name: String): T = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T

private fun View.findScroll(): ScrollView? {
    if (this is ScrollView) return this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).findScroll()?.let { return it }
    return null
}
