package com.svce.issuegram

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.svce.issuegram.di.AppContainer
import com.svce.issuegram.di.DefaultAppContainer

class IssuegramApplication :
    Application(),
    ImageLoaderFactory {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            // Note: Google Maps Dynamite internally accesses WindowManager from Application Context,
            // which triggers benign StrictMode visual service warnings. We suppress those from crashing/spamming.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        appContainer = DefaultAppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizePercent(0.02)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    companion object {
        lateinit var instance: IssuegramApplication
            private set
    }
}
