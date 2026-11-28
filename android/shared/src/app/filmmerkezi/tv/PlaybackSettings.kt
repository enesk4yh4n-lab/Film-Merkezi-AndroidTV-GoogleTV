package app.filmmerkezi.tv

enum class SubtitlePolicy { AUTO, ON, OFF }
enum class VideoSizing { FIT, ZOOM, STRETCH }
private const val NORMAL_PLAYBACK_BUDGET_BYTES = 32 * 1024 * 1024

enum class BufferProfile(val minMs:Int,val maxMs:Int,val bytes:Int,val startMs:Int,val restartMs:Int) {
    AUTO(8000,20000,NORMAL_PLAYBACK_BUDGET_BYTES,1500,3000),
    LOW(4000,12000,8*1024*1024,1000,2000),
    LONG(12000,45000,NORMAL_PLAYBACK_BUDGET_BYTES,2000,4000);

    internal fun targetBytes(heapBytes: Long): Int {
        val deviceBudget = (heapBytes / 4).coerceAtLeast(1L)
        return minOf(deviceBudget, bytes.toLong(), NORMAL_PLAYBACK_BUDGET_BYTES.toLong()).toInt()
    }
}
data class PlaybackSettings(
    val audio:String="tr",val secondAudio:String="en",val sourceAudio:Boolean=false,
    val text:String="tr",val subtitles:SubtitlePolicy=SubtitlePolicy.AUTO,
    val preferredHeight:Int=0,val autoMaxHeight:Int=0,val autoMaxFps:Int=0,
    val sizing:VideoSizing=VideoSizing.FIT,val buffer:BufferProfile=BufferProfile.AUTO,
    val seekSeconds:Int=10,val accelerate:Boolean=true,val controlsSeconds:Int=5,
    val customSubtitles:Boolean=false,val subtitleSize:Int=1,val subtitleYellow:Boolean=false,
    val subtitleOutline:Boolean=true,val subtitleBackground:Int=0,val subtitleBottom:Int=8,
    val audioRole:Int?=null,val textRole:Int?=null
)
