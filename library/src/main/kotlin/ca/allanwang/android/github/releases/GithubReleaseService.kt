package ca.allanwang.android.github.releases

import android.app.DownloadManager
import android.app.Notification
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.CallSuper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

abstract class GithubReleaseService : JobService(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = ContextHelper.dispatcher + job

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    val completeDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    downloadComplete()
                }
                DownloadManager.ACTION_NOTIFICATION_CLICKED ->
                    downloadClicked()
            }
        }
    }

    /**
     * Enable to allow for some extra logging.
     * You can set this to your app's BuildConfig.DEBUG
     */
    abstract val debug: Boolean

    abstract val notificationChannelId: String

    fun logd(message: () -> String) {
        if (debug)
            Log.d("GithubReleaseService", message())
    }

    fun logi(message: () -> String) {
        Log.i("GithubReleaseService", message())
    }

    fun loge(message: () -> String) {
        Log.e("GithubReleaseService", message())
    }

    @CallSuper
    override fun onStartJob(params: JobParameters?): Boolean {
        job = Job()

        val filter = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        }

        registerReceiver(receiver, filter)

        launch(CoroutineExceptionHandler { _, _ -> cleanup() }) {
            val asset: GithubAsset = try {
                withContext(Dispatchers.IO) {
                    getAsset()
                }
            } catch (e: Exception) {
                if (e !is CancellationException)
                    loge { "Failed to get github asset: ${e.message}" }
                null
            } ?: return@launch

            val file = getFileLocation(asset) ?: return@launch logd { "Did not get file for ${asset.name}" }

            if (file.isFile && file.length() > 0) return@launch logi { "File already downloaded for ${asset.name}" }

            val uri = fileToUri(file)

        }.invokeOnCompletion { cleanup() }
        return true
    }

    @CallSuper
    open fun cleanup() {
        job.cancel()
        unregisterReceiver(receiver)
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

    abstract fun getFileLocation(asset: GithubAsset): File?

    abstract fun fileToUri(file: File): Uri

    open fun download(asset: GithubAsset, uri: Uri) {
        val downloader = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloader != null) {
            val request = createDownloadAsset(this, asset, uri)
            downloader.enqueue(request)
        } else {
            val notifBuilder = createRedirectNotification(this, notificationChannelId, asset)
        }
    }

    abstract fun downloadComplete()

    abstract fun downloadClicked()

    @CallSuper
    override fun onStopJob(params: JobParameters?): Boolean {
        cleanup()
        return false
    }

    companion object Helper {

        /**
         * Creates a base download request to use for the given [asset] and [uri]
         */
        fun createDownloadAsset(context: Context, asset: GithubAsset, uri: Uri): DownloadManager.Request =
            DownloadManager.Request(uri).apply {
                setTitle(context.getString(R.string.github_release_download_title, asset.name))
                allowScanningByMediaScanner()
            }

        fun createRedirectNotification(context: Context, channelId: String, asset: GithubAsset): Notification.Builder =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }).apply {

            }

        fun installApk(context: Context, uri: Uri) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            context.startActivity(intent)
        }
    }
}