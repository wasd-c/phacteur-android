package app.phacteur.android

import android.app.Application
import app.phacteur.android.data.AppGraph
import app.phacteur.android.notifications.NotificationHelper

class PhacteurApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.from(this)
        NotificationHelper.createChannel(this)
    }
}
