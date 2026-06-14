package com.homehub

import android.app.Application
import com.homehub.core.ServiceLocator

class HomeHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // osmdroid braucht einen User-Agent für die Kachel-Server
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
    }
}
