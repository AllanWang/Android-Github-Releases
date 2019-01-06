package ca.allanwang.android.github.releases

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

internal object ContextHelper : CoroutineScope {

    val looper = Looper.getMainLooper()

    val handler = Handler(looper)

    /**
     * Creating dispatcher from main handler to avoid IO
     * See https://github.com/Kotlin/kotlinx.coroutines/issues/878
     */
    val dispatcher = handler.asCoroutineDispatcher("kau-main")

    override val coroutineContext: CoroutineContext get() = dispatcher
}