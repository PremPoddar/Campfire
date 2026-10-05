package com.prempoddar.campfire

import android.app.Application

class CampfireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
