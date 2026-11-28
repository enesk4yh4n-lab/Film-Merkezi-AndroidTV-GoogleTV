package app.filmmerkezi.tv

/** Finite recovery budget. Source alternatives belong to the same movie/episode. */
internal class PlaybackRecovery(private var sourceCount: Int) {
    enum class Failure { NETWORK, STALL, STARTUP, HTTP, FORMAT, DECODER, AUDIO, VIDEO }
    enum class Action { RETRY, NEXT, STOP }
    var index = 0
        private set
    var retryCount = 0
        private set
    fun select(selected:Int){require(selected in 0 until sourceCount);index=selected;retryCount=0}
    fun extend(count:Int){require(count>=sourceCount);sourceCount=count}
    fun failed(reason: Failure, hadProgress: Boolean,allowNext:Boolean=true,eligible:(Int)->Boolean={true}): Action {
        // The loader already retries transient HTTP failures. One session restart is allowed
        // only after playback made progress, never an endless retry of a dead startup URL.
        if (hadProgress && retryCount == 0 && reason in setOf(Failure.NETWORK, Failure.STALL)) {
            retryCount++
            return Action.RETRY
        }
        if (!allowNext) return Action.STOP
        index = (index + 1 until sourceCount).firstOrNull(eligible) ?: return Action.STOP
        retryCount = 0
        return Action.NEXT
    }
}

/** Monotonic, wall-clock watchdog; pause, focus loss and ended media never spend its budget. */
internal class PlaybackGuard(private val startupMs: Long = 20000, private val stallMs: Long = 30000) {
    private var changedAt = 0L
    private var lastPosition = 0L
    private var lastFrames = 0
    fun reset(now: Long, position: Long = 0, frames: Int = 0) {
        changedAt = now; lastPosition = position; lastFrames = frames
    }
    fun observe(now: Long, expectedToPlay: Boolean, firstFrame: Boolean, position: Long, frames: Int): PlaybackRecovery.Failure? {
        if (!expectedToPlay) { reset(now, position, frames); return null }
        if (!firstFrame) return if (now - changedAt >= startupMs) PlaybackRecovery.Failure.STARTUP else null
        if (frames > lastFrames && position > lastPosition) reset(now, position, frames)
        return if (now - changedAt >= stallMs) PlaybackRecovery.Failure.STALL else null
    }
}
