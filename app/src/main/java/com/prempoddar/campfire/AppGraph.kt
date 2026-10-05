package com.prempoddar.campfire

import android.app.Application
import com.prempoddar.campfire.host.SettingsRepository

object AppGraph {
    private lateinit var app: Application

    fun init(application: Application) {
        app = application
    }

    val settings by lazy { SettingsRepository(app) }
}
