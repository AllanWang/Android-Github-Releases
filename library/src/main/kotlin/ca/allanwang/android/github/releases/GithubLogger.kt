package ca.allanwang.android.github.releases

import android.util.Log

internal interface GithubLoggable {
    /**
     * Enable to allow for some extra logging.
     * You can set this to your app's BuildConfig.DEBUG
     */
    val debug: Boolean

    /**
     * Log tag
     */
    val logTag: String
}

internal fun GithubLoggable.logd(message: () -> String) {
    if (debug)
        Log.d(logTag, message())
}

internal fun GithubLoggable.logi(message: () -> String) {
    Log.i(logTag, message())
}

internal fun GithubLoggable.loge(message: () -> String) {
    Log.e(logTag, message())
}

internal fun GithubLoggable.loge(t: Throwable, message: () -> String) {
    Log.e(logTag, message(), t)
}