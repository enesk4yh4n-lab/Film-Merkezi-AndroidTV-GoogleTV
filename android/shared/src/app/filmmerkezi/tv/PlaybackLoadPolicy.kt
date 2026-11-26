package app.filmmerkezi.tv

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Keeps loader retries bounded before the session tries the next actual source. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class PlaybackLoadPolicy : DefaultLoadErrorHandlingPolicy(2) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (info.errorCount > 2) return C.TIME_UNSET
        val http = generateSequence<Throwable>(info.exception) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (http?.responseCode in listOf(401, 403, 404, 410, 416)) return C.TIME_UNSET
        val delay = super.getRetryDelayMsFor(info)
        if (delay == C.TIME_UNSET) return delay
        // Honor a short server delay without blocking the application thread or waiting forever.
        val retryAfter = http?.headerFields?.entries?.firstOrNull { it.key.equals("Retry-After", true) }
            ?.value?.firstOrNull()?.toLongOrNull()?.coerceIn(0, 3)?.times(1000)
        return retryAfter ?: delay.coerceIn(250, 2000)
    }
}
