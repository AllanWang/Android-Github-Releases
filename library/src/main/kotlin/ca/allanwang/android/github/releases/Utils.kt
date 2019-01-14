package ca.allanwang.android.github.releases

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context

internal val Context.downloadManager: DownloadManager?
    get() = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

internal val Context.notificationManager: NotificationManager?
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager