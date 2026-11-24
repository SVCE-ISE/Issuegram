package com.svce.issuegram

import android.app.Application
import android.os.StrictMode
import com.svce.issuegram.di.AppContainer
import com.svce.issuegram.di.DefaultAppContainer

class IssuegramApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        appContainer = DefaultAppContainer(this)
    }

    companion object {
        lateinit var instance: IssuegramApplication
            private set
    }
}
