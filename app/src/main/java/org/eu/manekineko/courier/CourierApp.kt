package org.eu.manekineko.courier

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class CourierApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName

        val tileCacheDir = File(cacheDir, "osmdroid/tiles")
        if (!tileCacheDir.exists()) {
            tileCacheDir.mkdirs()
        }
        osmConfig.osmdroidTileCache = tileCacheDir
        osmConfig.tileFileSystemCacheMaxBytes = 200L * 1024 * 1024
        osmConfig.tileFileSystemCacheTrimBytes = 150L * 1024 * 1024
        osmConfig.expirationOverrideDuration = 30L * 24 * 60 * 60 * 1000
        osmConfig.tileDownloadMaxQueueSize = 20
        osmConfig.tileDownloadThreads = 4
    }
}
