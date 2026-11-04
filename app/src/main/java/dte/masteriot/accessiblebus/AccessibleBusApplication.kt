package dte.masteriot.accessiblebus

import android.app.Application

class AccessibleBusApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        container = DefaultAppContainer(this)
    }
}
