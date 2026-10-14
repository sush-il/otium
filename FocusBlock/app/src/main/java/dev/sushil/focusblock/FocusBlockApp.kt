package dev.sushil.focusblock

import android.app.Application

class FocusBlockApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
