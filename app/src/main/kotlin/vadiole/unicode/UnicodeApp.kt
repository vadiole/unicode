package vadiole.unicode

import android.app.Application

class UnicodeApp : Application() {
    private var _appComponent: AppComponent? = null
    val appComponent: AppComponent get() = requireNotNull(_appComponent) { "app isn not created yet" }

    override fun onCreate() {
        super.onCreate()
        _appComponent = AppComponent(this)
    }
}