package com.example.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import com.example.util.NetworkUtils

class GatewayService : Service() {

    private var server: GatewayServer? = null

    companion object {
        const val CHANNEL_ID = "gateway_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_GATEWAY"
        const val ACTION_STOP = "ACTION_STOP_GATEWAY"

        fun startGateway(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopGateway(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                server?.stop()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startServer()
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        val db = AppDatabase.getInstance(applicationContext)
        val engine = WalletMatchingEngine(db)

        if (server == null) {
            server = GatewayServer(db, engine, port = 8080)
        }

        server?.start()

        val ip = NetworkUtils.getLocalIpAddress()
        val notification = buildNotification("بوابة الحوالات (SMS Gateway) نشطة", "الخادم يستمع على: http://$ip:8080")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POWER FEUL SMS Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة تشغيل خادم الحوالات وبوابة الرسائل عبر الشبكة المحلية"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
