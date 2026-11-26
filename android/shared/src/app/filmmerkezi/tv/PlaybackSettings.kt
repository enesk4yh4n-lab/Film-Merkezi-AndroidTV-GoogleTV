package app.filmmerkezi.tv

enum class SubtitlePolicy { AUTO, ON, OFF }
enum class VideoSizing { FIT, ZOOM, STRETCH }
enum class BufferProfile(val minMs:Int,val maxMs:Int,val bytes:Int,val startMs:Int,val restartMs:Int) {
    AUTO(8000,20000,16*1024*1024,1500,3000),
    LOW(4000,12000,8*1024*1024,1000,2000),
    LONG(12000,45000,24*1024*1024,2000,4000)
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
