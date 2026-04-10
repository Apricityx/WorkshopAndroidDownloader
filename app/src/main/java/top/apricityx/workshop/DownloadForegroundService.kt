package top.apricityx.workshop

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import com.elvishew.xlog.XLog.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var downloadCenterManager: DownloadCenterManager
    private var observationJob: Job? = null
    private var isInForeground = false

    override fun onCreate() {
        super.onCreate()
        downloadCenterManager = DownloadCenterManager.getInstance(application)
        ensureNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val snapshot = downloadCenterManager.uiState.value.toForegroundNotificationSnapshot()
        if (!snapshot.isActive) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        showNotification(snapshot, promoteToForeground = !isInForeground)
        observeDownloadState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    @OptIn(FlowPreview::class)
    private fun observeDownloadState() {
        if (observationJob?.isActive == true) {
            return
        }

        observationJob = serviceScope.launch {
            downloadCenterManager.uiState
                .map(DownloadCenterUiState::toForegroundNotificationSnapshot)
                .distinctUntilChanged()
                .sample(NOTIFICATION_UPDATE_INTERVAL_MILLIS)
                .collect { snapshot ->
                    if (!snapshot.isActive) {
                        stopForegroundAndSelf()
                        return@collect
                    }
                    showNotification(snapshot, promoteToForeground = !isInForeground)
                }
        }
    }

    private fun showNotification(
        snapshot: DownloadForegroundNotificationSnapshot,
        promoteToForeground: Boolean,
    ) {
        val notification = buildNotification(snapshot)
        if (promoteToForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isInForeground = true
        } else {
            val canPostNotifications =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
            if (canPostNotifications) {
                runCatching {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                }.onFailure { error ->
                    Log.w(WorkshopAppContract.logTag, "Failed to post foreground download notification", error)
                }
            }
        }
    }

    private fun buildNotification(snapshot: DownloadForegroundNotificationSnapshot): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = WorkshopAppContract.openDownloadCenterAction
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle().also { inbox ->
            snapshot.lines.forEach(inbox::addLine)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.text)
            .setSubText(snapshot.subText)
            .setStyle(style)
            .setProgress(snapshot.progressMax, snapshot.progress, snapshot.progressIndeterminate)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stopForegroundAndSelf() {
        observationJob?.cancel()
        observationJob = null
        if (isInForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isInForeground = false
        }
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "后台下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示创意工坊下载进度并保持后台下载"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "download_foreground"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_UPDATE_INTERVAL_MILLIS = 750L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadForegroundService::class.java),
                )
            }.onFailure { error ->
                Log.w(WorkshopAppContract.logTag, "Failed to start foreground download service", error)
            }
        }
    }
}
