package com.example.lab_week_08

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NotificationService : Service() {

    // Notification builder untuk foreground service
    private lateinit var notificationBuilder: NotificationCompat.Builder

    // Handler untuk menjalankan proses di thread terpisah
    private lateinit var serviceHandler: Handler

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Buat notifikasi foreground
        notificationBuilder = startForegroundServiceNotification()

        // Buat HandlerThread agar kerja berat jalan di thread terpisah
        val handlerThread = HandlerThread("SecondThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
    }

    // Membuat dan memulai foreground service dengan notifikasi
    private fun startForegroundServiceNotification(): NotificationCompat.Builder {
        val pendingIntent = getPendingIntent()
        val channelId = createNotificationChannel()
        val builder = getNotificationBuilder(pendingIntent, channelId)

        // Jalankan service di foreground agar notifikasi muncul
        startForeground(NOTIFICATION_ID, builder.build())
        return builder
    }

    private fun getPendingIntent(): PendingIntent {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flag
        )
    }

    private fun createNotificationChannel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "001"
            val channelName = "001 Channel"
            val channelPriority = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(channelId, channelName, channelPriority)

            val service = requireNotNull(
                ContextCompat.getSystemService(this, NotificationManager::class.java)
            )

            service.createNotificationChannel(channel)
            channelId
        } else {
            "" // API < 26 tidak perlu channel
        }
    }

    private fun getNotificationBuilder(
        pendingIntent: PendingIntent,
        channelId: String
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Second worker process is done")
            .setContentText("Check it out!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("Second worker process is done, check it out!")
            .setOngoing(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val returnValue = super.onStartCommand(intent, flags, startId)

        // Dapatkan channel ID dari intent
        val id = intent?.getStringExtra(EXTRA_ID)
            ?: throw IllegalStateException("Channel ID must be provided")

        // Jalankan pekerjaan berat di background thread
        serviceHandler.post {
            // Menjalankan countdown 10 detik di notifikasi
            countDownFromTenToZero(notificationBuilder)

            // Kirim hasil kembali ke LiveData (MainActivity bisa observe)
            notifyCompletion(id)

            // Hentikan notifikasi & service
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return returnValue
    }

    // Fungsi untuk update countdown di notifikasi
    private fun countDownFromTenToZero(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        for (i in 10 downTo 0) {
            Thread.sleep(1000L)
            notificationBuilder
                .setContentText("$i seconds until last warning")
                .setSilent(true)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    // Kirim hasil (Id) kembali ke LiveData di Main Thread
    private fun notifyCompletion(id: String) {
        Handler(Looper.getMainLooper()).post {
            mutableID.value = id
        }
    }

    companion object {
        const val NOTIFICATION_ID = 0xCA7
        const val EXTRA_ID = "Id"

        // LiveData untuk melacak ID notifikasi
        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }
}