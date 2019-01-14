package ca.allanwang.android.github.releases.sample

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import ca.allanwang.kau.internal.KauBaseActivity
import ca.allanwang.kau.permissions.PERMISSION_WRITE_EXTERNAL_STORAGE
import ca.allanwang.kau.permissions.kauRequestPermissions
import ca.allanwang.kau.utils.snackbar
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : KauBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        main_button.setOnClickListener {
            kauRequestPermissions(PERMISSION_WRITE_EXTERNAL_STORAGE) {
                granted, _ ->
                if (granted)
                    requestDownload()
            }
        }
    }

    private fun warn(message: String) {
        snackbar(message)
    }

    private fun requestDownload() {
        val scheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return warn("No job scheduler found")
        val serviceComponent = ComponentName(this, SampleReleaseService::class.java)

        val builder = JobInfo.Builder(12345, serviceComponent)
            .setMinimumLatency(0L)
            .setOverrideDeadline(2000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        val result = scheduler.schedule(builder.build())
        if (result <= 0)
            return warn("Service failed with $result")
    }
}