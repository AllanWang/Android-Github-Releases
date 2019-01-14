package ca.allanwang.android.github.releases

import android.Manifest
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Download container that encapsulates the entire download process.
 * To use this, extend the class and implement the abstract parts.
 * Most of the logic comes out of the box, but you may override certain open components if you wish.
 *
 * To launch the process, simply call [start], and call [stop] if the process should be cancelled.
 * A stopped process can be started again.
 *
 * [context] is the current app context. Only the application context will be used
 *
 * [packageName] is the id of the apk to be installed.
 * This should be set to BuildConfig.APPLICATION_ID.
 * The id is used to specify the notification channel, and it also used to enable installation of unknown apps.
 *
 * [debug] should be enabled to add more logging.
 * This should be set to BuildConfig.DEBUG
 */
abstract class GithubReleaseDownloader(
    context: Context,
    override val debug: Boolean
) : GithubLoggable, CoroutineScope {

    private val started = AtomicBoolean(false)
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job + coroutineErrorHandler

    open val coroutineErrorHandler = CoroutineExceptionHandler { _, throwable ->
        loge(throwable) { "Failed to download" }
        stop()
    }

    val context: Context = context.applicationContext

    open val packageName: String = context.packageName

    /**
     * Channel id for all notifications
     */
    open val notificationChannelId: String = "github.release_${context.packageName}"

    init {
        logd { "Initialized GithubReleaseDownloader for package $packageName" }
    }

    override val logTag = "GithubReleaseDownloader"

    private var receiver: GithubReleaseReceiver? = null

    private fun getDownloadManager(): DownloadManager? =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    private fun getNotificationManager(): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    /**
     * Call when downloader should start checking for new assets
     * This will return immediately, as the downloader is launched with a coroutine
     */
    fun start() {
        if (started.getAndSet(true))
            return loge { "Already started downloader" }
        job = Job()

        launch {
            val asset: GithubAsset = try {
                withContext(Dispatchers.IO) {
                    getAsset()
                }
            } catch (e: Exception) {
                if (e !is CancellationException)
                    loge { "Failed to get github asset: ${e.message}" }
                null
            } ?: return@launch

            setupNotificationChannel()

            val file = getFileLocation(asset)

            if (file != null && file.isFile && file.length() > 0) {
                logd { "File already downloaded for ${asset.name}" }
                onDownloadComplete(asset, Uri.fromFile(file))
            }

            download(asset, if (file != null) Uri.fromFile(file) else null)

        }
    }

    /**
     * Call when the downloader should stop
     * This method is idempotent
     */
    fun stop() {
        if (!started.getAndSet(false))
            return
        logd { "Cleanup" }
        receiver?.unregister(context)
        job.cancel()
    }

    /**
     * Sets up notification channels, which is required for Android O and up.
     * Note that the channel is is [notificationChannelId]
     */
    open fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getNotificationManager() ?: return
        val channel = NotificationChannel(
            notificationChannelId,
            context.getString(R.string.github_release_channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Fetch a new asset to be downloaded.
     * You should ensure that the asset is newer than the one provided before sending it,
     * as the output will be directly mapped to a notification.
     *
     * If an expected error occurs, return null.
     * Otherwise, throw the exception and it will be caught by the caller.
     *
     * This will be called from [Dispatchers.IO]
     */
    @Throws(Exception::class)
    abstract suspend fun getAsset(): GithubAsset?

    /**
     * Returns the desired file location to use,
     * or null for a random location.
     * Note that if a nonnull file is supplied, it must not yet exist,
     * or it will be assumed that the same asset has already been downloaded.
     */
    open fun getFileLocation(asset: GithubAsset): File? = null

    /**
     * Handles a download request.
     * [asset] refers to the github asset that should be downloaded.
     * [destinationUri] refers to the expected file uri to download into.
     * If it's null, then any file location can be used.
     *
     * By default, if the download manager does not exist,
     * a notification will be sent to download the asset directly from a browser
     */
    open fun download(asset: GithubAsset, destinationUri: Uri?) {
        logd { "Downloading asset ${asset.name}" }
        val downloader = getDownloadManager()
        if (downloader != null) {
            val request = createDownloadAsset(asset, destinationUri)
            val id = downloader.enqueue(request)
            receiver = GithubReleaseReceiver.listen(context, id = id, debug = debug) {
                val downloadUri = downloader.getUriForDownloadedFile(id) ?: return@listen
                onDownloadComplete(asset, downloadUri)
            }
        } else {
            logi { "No download service found" }
            val manager = getNotificationManager()
                ?: return loge { "No notification manager found" }
            val notifBuilder = createRedirectNotification(asset)
            manager.notify(notificationChannelId, asset.id, notifBuilder.build())
        }
    }

    /**
     * Base for all notifications.
     * Override to set your own icon and details
     */
    open fun buildNotif(builder: Notification.Builder.() -> Unit): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, notificationChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.apply {
            setSmallIcon(R.drawable.ic_update_white_24dp)
            setOnlyAlertOnce(true)
            setAutoCancel(false)
            setOngoing(true)
        }.apply(builder)

    /**
     * Creates a new download request for the provided [asset] and [destinationUri].
     * Called from [download] if the download manager exists.
     */
    open fun createDownloadAsset(asset: GithubAsset, destinationUri: Uri?): DownloadManager.Request =
        DownloadManager.Request(Uri.parse(asset.browser_download_url)).apply {
            setTitle(context.getString(R.string.github_release_download_title, asset.name))
            allowScanningByMediaScanner()
            setDestinationUri(destinationUri)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        }

    /**
     * Creates a fallback notification when the download manager does not exist.
     * By default, this redirects to the browser.
     * Called from [download] if the download manager does not exist.
     */
    open fun createRedirectNotification(asset: GithubAsset): Notification.Builder =
        buildNotif {
            setContentTitle(context.getString(R.string.github_release_download_title, asset.name))
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW).setData(Uri.parse(asset.browser_download_url)),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            setContentIntent(pendingIntent)
        }

    /**
     * Creates an install notification. At this point, the file is completely downloaded at the [uri].
     * Called from [onDownloadComplete].
     * By default, if the app can install apks, it will prompt an installation.
     * Otherwise, it will open the file.
     */
    open fun createInstallNotification(asset: GithubAsset, uri: Uri): Notification.Builder =
        buildNotif {
            setContentTitle(context.getString(R.string.github_release_downloaded_title, asset.name))
            setContentText(context.getString(R.string.github_release_downloaded_desc))
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                getInstallerIntent(uri),
                0
            )
            setContentIntent(pendingIntent)
        }

    // TODO update intents
    // Log if android o and missing permission
    // Default to opening file
    private fun getInstallerIntent(uri: Uri): Intent {
        val packageManager = context.packageManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && Manifest.permission.REQUEST_INSTALL_PACKAGES in packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS
            ).requestedPermissions
            && !packageManager.canRequestPackageInstalls()
        )
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
        else
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = contentUri(File(uri.path))
            }
    }

    fun fileUri(file: File): Uri = Uri.fromFile(file)

    fun contentUri(file: File): Uri =
        FileProvider.getUriForFile(context, context.getString(R.string.github_release_file_provider_authority), file)

    /**
     * Handles a completed download request.
     * By default, launches a notification to prompt an apk install
     */
    open fun onDownloadComplete(asset: GithubAsset, uri: Uri) {
        logd { "Downloaded asset ${asset.name}" }
        val manager = getNotificationManager()
            ?: return loge { "No notification manager found" }
        val notifBuilder = createInstallNotification(asset, uri)
        manager.notify(notificationChannelId, asset.id, notifBuilder.build())
    }
}