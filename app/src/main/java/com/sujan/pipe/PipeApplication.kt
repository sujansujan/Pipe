package com.sujan.pipe

import android.app.Application
import com.sujan.pipe.service.YouTubeMediaSessionTracker
import com.sujan.pipe.service.YouTubeWatchKeepAliveService

class PipeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        YouTubeMediaSessionTracker.start(this)
        YouTubeWatchKeepAliveService.syncRunningState(this)
    }
}
