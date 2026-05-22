package com.gzzz.toimage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.gzzz.toimage.data.queue.TaskQueue
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ToimageApp : Application() {

    @Inject
    lateinit var taskQueue: TaskQueue

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 恢复未完成任务：将 pending/running 标记为 failed
        appScope.launch {
            taskQueue.restoreUnfinishedTasks()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "图片生成",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示图片生成进度"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "generation_channel"
    }
}
