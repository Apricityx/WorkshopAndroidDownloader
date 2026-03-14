package top.apricityx.workshop

import android.app.Application

class WorkshopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppRuntimeLogManager.initialize(this)
    }
}
