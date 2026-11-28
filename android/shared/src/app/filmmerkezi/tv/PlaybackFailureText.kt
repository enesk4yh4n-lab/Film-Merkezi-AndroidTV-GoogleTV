package app.filmmerkezi.tv

import android.content.Context

internal object PlaybackFailureText {
    fun http(context: Context, code: Int): String {
        val summary = when (code) {
            401, 403 -> R.string.player_failure_access
            404, 410 -> R.string.player_failure_missing
            429 -> R.string.player_failure_rate
            in 500..599 -> R.string.player_failure_server
            else -> R.string.player_failure_http
        }
        return context.getString(R.string.player_failure_with_code, context.getString(summary), code)
    }
}
