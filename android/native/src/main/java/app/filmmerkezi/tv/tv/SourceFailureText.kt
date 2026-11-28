package app.filmmerkezi.tv.tv

import android.content.Context
import app.filmmerkezi.tv.PlaybackFailureText
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.DataFailure
import app.filmmerkezi.tv.core.FailureKind

internal fun sourceFailureText(context: Context, error: Throwable): String {
    val failure = error as? DataFailure ?: return context.getString(R.string.tv_no_source)
    val specific = when (failure.stage) {
        "adult-filter" -> R.string.settings_filtered
        "site-settings" -> R.string.player_source_disabled
        "source-pool" -> R.string.tv_no_source
        else -> null
    }
    return specific?.let(context::getString) ?: sourceFailureText(context, failure.kind, failure.httpCode)
}

internal fun sourceFailureText(context: Context, kind: FailureKind, httpCode: Int?): String {
    httpCode?.takeIf { it in 400..599 }?.let { return PlaybackFailureText.http(context, it) }
    return context.getString(when (kind) {
        FailureKind.HTTP -> R.string.player_failure_http
        FailureKind.ACCESS -> R.string.player_failure_access
        FailureKind.NETWORK -> R.string.player_failure_network
        FailureKind.TIMEOUT -> R.string.player_failure_timeout
        FailureKind.PARSE -> R.string.player_failure_format
        FailureKind.UNSUPPORTED -> R.string.player_failure_unsupported_source
        FailureKind.LIMIT -> R.string.player_failure_limit
        FailureKind.STORAGE -> R.string.ux_storage_retry
    })
}
