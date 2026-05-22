package com.gzzz.toimage.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gzzz.toimage.MainActivity
import com.gzzz.toimage.R
import com.gzzz.toimage.ToimageApp
import com.gzzz.toimage.data.queue.TaskQueue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GenerationService : Service() {

    @Inject
    lateinit var taskQueue: TaskQueue

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                taskQueue.cancel()
                stopSelf()
            }
            ACTION_START -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: "生成中"
                startForeground(NOTIFICATION_ID, buildNotification(prompt))
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(prompt: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GenerationService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ToimageApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("正在生成图片")
            .setContentText(prompt.take(50))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_launcher, "取消", cancelIntent)
            .setProgress(0, 0, true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.gzzz.toimage.START_GENERATION"
        const val ACTION_CANCEL = "com.gzzz.toimage.CANCEL_GENERATION"
        const val EXTRA_PROMPT = "prompt"

        fun start(context: Context, prompt: String) {
            val intent = Intent(context, GenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROMPT, prompt)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GenerationService::class.java)
            context.stopService(intent)
        }
    }
}
