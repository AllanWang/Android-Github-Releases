package ca.allanwang.android.github.releases.sample

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Environment
import ca.allanwang.android.github.releases.GithubAsset
import ca.allanwang.android.github.releases.GithubReleaseDownloader
import java.io.File

class SampleReleaseService : JobService() {

    private val downloader by lazy { SampleReleaseDownloader(this) }

    override fun onStartJob(params: JobParameters?): Boolean {
        downloader.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        downloader.stop()
        return false
    }
}

class SampleReleaseDownloader(context: Context) : GithubReleaseDownloader(
    context,
    debug = BuildConfig.DEBUG
) {
    override suspend fun getAsset(): GithubAsset? =
        GithubAsset(
            id = 10426075,
            url = "https://api.github.com/repos/AllanWang/Frost-for-Facebook/releases/assets/10426075",
            name = "Frost-release.apk",
            label = null,
            content_type = "application/vnd.android.package-archive",
            size = 6721271,
            download_count = 550,
            created_at = "2019-01-06T04:33:30Z",
            updated_at = "2019-01-06T04:33:36Z",
            uploader = null,
            browser_download_url = "https://github.com/AllanWang/Frost-for-Facebook/releases/download/v2.2.1/Frost-release.apk"
        )

    override fun getFileLocation(asset: GithubAsset): File? {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SampleReleaseDownloads/test.apk"
        )
    }
}