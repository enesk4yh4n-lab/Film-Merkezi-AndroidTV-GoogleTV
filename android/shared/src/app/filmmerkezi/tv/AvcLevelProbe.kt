package app.filmmerkezi.tv

/** Some bounded 8-bit AVC streams overstate their SPS level. Never change the
 * profile or the stream: this only permits a watchdog-bounded decoder attempt. */
internal object AvcLevelProbe {
    private val codec = Regex("^(avc[13])\\.([0-9a-f]{4})([0-9a-f]{2})$", RegexOption.IGNORE_CASE)
    fun boundedCodec(value: String?, width: Int, height: Int, fps: Float): String? {
        if (width !in 1..1920 || height !in 1..1080 || !fps.isFinite() || fps <= 0 || fps > 30.01f) return null
        val match = codec.matchEntire(value ?: return null) ?: return null
        // Baseline/Main/High only. Hi10, 4:2:2, 4:4:4 and unknown profiles stay rejected.
        if (match.groupValues[2].take(2).lowercase() !in setOf("42", "4d", "64")) return null
        val level = if (width <= 1280 && height <= 720) 31 else 40
        if (match.groupValues[3].toInt(16) <= level) return null
        return match.groupValues[1] + "." + match.groupValues[2] + level.toString(16).padStart(2, '0')
    }
}
