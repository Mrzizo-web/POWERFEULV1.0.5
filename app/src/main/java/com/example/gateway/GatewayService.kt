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
import com.example.data.engine.GatewaySyncEngine
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import com.example.util.NetworkUtils

class GatewayService : Service() {

    private var server: GatewayServer? = null
    private var retryEngine: GatewaySyncEngine? = null
    private var serverPort: Int = DEFAULT_PORT

    companion object {
        const val CHANNEL_ID = "gateway_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_GATEWAY"
        const val ACTION_STOP = "ACTION_STOP_GATEWAY"
        const val EXTRA_PORT = "gateway_port"

        @Volatile
        var isServerRunning: Boolean = false
            private set

        fun startGateway(context: Context, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
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
        if (intent?.action == ACTION_STOP) {
            stopGatewayInternal()
            return START_NOT_STICKY
        }
        val requestedPort = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        startServer(requestedPort)
        return if (isServerRunning) START_STICKY else START_NOT_STICKY
    }

    private fun startServer(port: Int) {
        // Promote immediately to foreground before opening sockets.
        startForeground(
            NOTIFICATION_ID,
            buildNotification("بوابة الحوالات (SMS Gateway)", "جاري تشغيل الخادم...")
        )

        if (server?.isRunning == true && serverPort == port) {
            isServerRunning = true
            refreshNotification("الخادم نشط على ${NetworkUtils.getLocalIpAddress()}:$port")
            return
        }

        server?.stop()
        retryEngine?.stopPeriodicRetryLoop()
        server = null
        retryEngine = null
        isServerRunning = false

        val db = AppDatabase.getInstance(applicationContext)
        val syncEngine = GatewaySyncEngine(db)
        val candidate = GatewayServer(
            db = db,
            walletMatchingEngine = WalletMatchingEngine(db),
            port = port,
            gatewaySyncEngine = syncEngine,
            context = applicationContext
        )

        if (candidate.start()) {
            server = candidate
            retryEngine = syncEngine
            serverPort = port
            isServerRunning = true
            refreshNotification("الخادم نشط على ${NetworkUtils.getLocalIpAddress()}:$port")
        } else {
            candidate.stop()
            syncEngine.stopPeriodicRetryLoop()
            isServerRunning = false
            refreshNotification("تعذر تشغيل الخادم؛ تحقق من المنفذ وإعدادات الشبكة")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun stopGatewayInternal() {
        server?.stop()
        server = null
        retryEngine?.stopPeriodicRetryLoop()
        retryEngine = null
        isServerRunning = false
        stopForeground(true)
        stopSelf()
    }

    private fun refreshNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification("بوابة الحوالات (SMS Gateway)", message))
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
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        retryEngine?.stopPeriodicRetryLoop()
        retryEngine = null
        isServerRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val DEFAULT_PORT = 8080
    }
}
