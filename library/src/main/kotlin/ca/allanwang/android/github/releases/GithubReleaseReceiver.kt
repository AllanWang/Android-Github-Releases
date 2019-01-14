package ca.allanwang.android.github.releases

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A broadcast receiver that listens to a single cycle of the provided download request id.
 * Once the download completes, the receiver will unregister itself and call [onDownloadComplete]
 */
class GithubReleaseReceiver private constructor(val id: Long, override val debug: Boolean, val onDownloadComplete: () -> Unit) :
    BroadcastReceiver(),
    GithubLoggable {

    override val logTag = "GithubReleaseReceiver"
    private val registered = AtomicBoolean(false)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            return
        if (id != intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1))
            return
        logd { "Download complete for id $id" }
        onDownloadComplete()
        unregister(context)
    }

    /**
     * Registers receiver to the application context.
     * This method is idempotent.
     */
    fun register(context: Context) {
        if (registered.getAndSet(true))
            return
        logd { "Registered" }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.applicationContext.registerReceiver(this, filter)
    }

    /**
     * Unregisters receiver from the application context.
     * This method is idempotent.
     */
    fun unregister(context: Context) {
        if (!registered.getAndSet(false))
            return
        context.applicationContext.unregisterReceiver(this)
        logd { "Unregistered" }
    }

    companion object {
        /**
         * Creates a receiver and registers it to eh application context
         */
        fun listen(
            context: Context,
            id: Long,
            debug: Boolean = false,
            onDownloadComplete: () -> Unit
        ): GithubReleaseReceiver {
            val receiver = GithubReleaseReceiver(id, debug, onDownloadComplete)
            receiver.register(context)
            return receiver
        }
    }
}