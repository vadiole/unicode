package vadiole.unicode

import android.content.Context

interface AppContextOwner {
    fun getApplicationContext(): Context
}