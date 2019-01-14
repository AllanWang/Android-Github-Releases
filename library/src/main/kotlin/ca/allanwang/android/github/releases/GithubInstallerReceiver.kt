package ca.allanwang.android.github.releases

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class GithubInstallerReceiver : BroadcastReceiver(), GithubLoggable {

    override var debug: Boolean = false

    override val logTag: String = "GithubInstallerReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION)
            return

        debug = intent.getBooleanExtra(DEBUG, false)

        val notifId = intent.getIntExtra(NOTIF_ID, -1)
            .takeIf { it != -1 }
            ?: return loge { "No notif id found" }
        val path = intent.getStringExtra(FILE_PATH)
            ?: return loge { "No path found" }
        val file = File(path)

        if (!file.isFile)
            return logi { "File ${file.name} no longer exists" }

        val contentUri = FileProvider.getUriForFile(
            context,
            context.getString(R.string.github_release_file_provider_authority), File(path)
        )

        if (install(context, contentUri)) {
            val notifManager = context.notificationManager ?: return
            notifManager.cancel(notifId)
        }
    }

    /**
     * Attempt to install apk at uri.
     * Return true if completed, false otherwise.
     */
    private fun install(context: Context, uri: Uri): Boolean {
        logi { "Found install request for $uri" }
        val packageManager = context.packageManager

        fun fallback(): Boolean {
            logd { "Fallback" }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                data = uri
            }
            context.startActivity(intent)
            return true
        }

        fun install(): Boolean {
            logd { "Installer" }
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            context.startActivity(intent)
            return true
        }

        // No further permissions required
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return install()

        val packageName = context.packageName

        // Cannot install apps
        if (Manifest.permission.REQUEST_INSTALL_PACKAGES !in
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        ) {
            logd { "REQUEST_INSTALL_PACKAGES permission not found in manifest" }
            return fallback()
        }

        // Can install apps if granted by user
        return if (packageManager.canRequestPackageInstalls()) {
            install()
        } else {
            logd { "Enabler" }
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
            false
        }
    }

    companion object {
        const val ACTION = "ca.allanwang.android.github.releases.INSTALL"
        const val NOTIF_ID = "notif_id"
        const val FILE_PATH = "file_path"
        const val DEBUG = "debug"

        fun getIntent(context: Context, notifId: Int, file: File, debug: Boolean): Intent =
            Intent(context, GithubInstallerReceiver::class.java).apply {
                action = ACTION
                putExtra(NOTIF_ID, notifId)
                putExtra(FILE_PATH, file.absolutePath)
                putExtra(DEBUG, debug)
            }
    }
}