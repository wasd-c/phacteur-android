package app.phacteur.android.data

import android.content.Context

class AppGraph private constructor(context: Context) {
    val secureStorage = SecureStorage(context)
    val cookieStore = SessionCookieStore(secureStorage)
    val api = PhacteurApi(cookieStore)

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun from(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance = it }
        }
    }
}
