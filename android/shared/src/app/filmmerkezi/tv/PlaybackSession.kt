package app.filmmerkezi.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Timeline
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.DecoderReuseEvaluation
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

/** One Media3 engine at a time. Metadata/JS lifetimes do not own the playback state machine. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class PlaybackSession(
    private val context: Context,
    private val listener: Listener,
    startupTimeoutMs: Long = 20000,
    stallTimeoutMs: Long = 30000
) : AutoCloseable {
    companion object {
        // TLS close_notify can perform socket I/O, including ConnectionPool.evictAll().
        // Retired transports are never closed by the UI/Media3 application looper.
        private val transportCleanup = Executors.newSingleThreadExecutor { task -> Thread(task, "FilmMediaCleanup").apply { isDaemon = true } }
    }
    interface Listener {
        fun onEngineChanged(player: ExoPlayer?)
        fun onMessage(resource: Int, label: String = "")
        fun onEnded() {}
        fun onTracksAvailable() {}
        fun onSourcesExhausted(message:String) {}
    }
    fun interface SourceLookup {
        /** Starts asynchronous metadata work. Closing must cancel it without blocking. */
        fun resolve(option:JSONObject, result:(JSONObject?,Throwable?)->Unit):AutoCloseable
    }
    var player: ExoPlayer? = null
        private set
    val active: Boolean get() = sources.isNotEmpty()
    var diagnosticsEnabled = false
    var automaticSources=true
    var crossSiteSources=true
    private var discoveryPending=false
    private var waitingForSources=false
    private val handler = Handler(Looper.getMainLooper())
    private val guard = PlaybackGuard(startupTimeoutMs, stallTimeoutMs)
    private var sources = emptyList<JSONObject>()
    private var originals=emptyList<JSONObject>()
    private val refreshed=mutableSetOf<Int>()
    private val refreshAt=mutableMapOf<Int,Long>()
    private var refreshes=0
    private var selector:DefaultTrackSelector?=null
    private val device by lazy{PlaybackDevicePolicy(context.applicationContext)}
    private var audioLanguage="tr"
    private var textLanguage="tr"
    private var textEnabled=true
    private var qualityHeight=0
    private var configuredSettings=PlaybackSettings()
    private var sessionBuffer=BufferProfile.AUTO
    var candidateAllowed:(JSONObject)->Boolean={true}
    private val manual=mutableMapOf<Int,Pair<TrackGroup,Int>>()
    private var sourceChanged=false
    private var endedNotified=false
    private var lastDiagnosticMs=0L
    internal data class TrackChoice(val revision:Long,val group:TrackGroup,val index:Int,val format:Format,val selected:Boolean,val variantId:String?=null)
    private val triedVariants=mutableSetOf<String>()
    private var sourceLookup:SourceLookup?=null
    private var pendingLookup:AutoCloseable?=null
    private var resolving=false
    private var client: OkHttpClient? = null
    private var fixtureBase: String? = null
    private var recovery = PlaybackRecovery(0)
    private var attempt = 0L
    private var recovering = false
    private var suspended = false
    private var terminal = false
    private var position = 0L
    private var wantedToPlay = true
    private var attemptHadProgress = false
    private var attemptPrepared = false
    private var firstFrame = false
    private var attemptStartedMs = 0L
    private var firstFrameMs: Long? = null
    private var terminalError: String? = null
    private var lastFailure: String? = null
    private var httpCode: Int? = null
    private var headerVariant: String? = null
    private var rebufferCount = 0
    private var rebufferStarted = 0L
    private var rebufferMs = 0L
    private var droppedFrames = 0
    private var audioUnderruns = 0
    private var transferBytes = 0L
    private var transferMs = 0L
    private var videoCodec: String? = null
    private var videoCapabilityProbe: String? = null
    private var videoDecoder: String? = null
    private var switches = 0
    private var restarts = 0
    private var enginesCreated = 0
    private var engineInstancesUsed = 0
    private val events = ArrayDeque<JSONObject>()
    private var lastTracks = JSONArray()
    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if(diagnosticsEnabled&&SystemClock.elapsedRealtime()-lastDiagnosticMs>=15000){
                lastDiagnosticMs=SystemClock.elapsedRealtime();event("metrics")
            }
            if (!suspended && !terminal && !recovering && !resolving) {
                val failure = guard.observe(SystemClock.elapsedRealtime(),
                    p.playWhenReady && p.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE && p.playbackState != Player.STATE_ENDED,
                    firstFrame, p.currentPosition, p.videoDecoderCounters?.renderedOutputBufferCount ?: 0)
                if (failure != null) fail(failure, "watchdog:${failure.name}",
                    if (failure == PlaybackRecovery.Failure.STARTUP) R.string.player_no_picture else R.string.player_stalled)
            }
            handler.postDelayed(this, 500)
        }
    }
    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) { "Playback must use the main application looper" } }
    private fun event(kind: String, detail: String = "") {
        if (!diagnosticsEnabled) return
        if (events.size == 64) events.removeFirst()
        events.addLast(objectOf("atMs" to SystemClock.elapsedRealtime(), "kind" to kind, "detail" to detail.take(240), "sourceIndex" to recovery.index))
        // Local opt-in diagnostics only. Never include media URLs, cookies or headers.
        android.util.Log.i("FilmPlayback",kind+" source="+recovery.index+" rebuffer="+rebufferCount+" ms="+rebufferMs+
            " meanBps="+if(transferMs>0)transferBytes*8000/transferMs else 0)
    }
    fun preferences(audio:String,text:String,textOn:Boolean,height:Int)=configure(configuredSettings.copy(audio=audio,text=text,subtitles=if(textOn)SubtitlePolicy.ON else SubtitlePolicy.OFF,preferredHeight=height.coerceAtLeast(0)))
    val automaticQuality get()=qualityHeight==0&&manual[C.TRACK_TYPE_VIDEO]==null
    val subtitlesEnabled get()=textEnabled
    fun configure(value:PlaybackSettings){
        mainThread()
        fun matches(type:Int,language:String,role:Int?):Boolean {
            val selected=manual[type]?:return false
            val f=player?.currentTracks?.groups?.firstOrNull{it.mediaTrackGroup==selected.first}?.let{trackFormat(it,selected.second)}?:return false
            return PlaybackPreferencePolicy.matches(f.language,language)&&(role==null||f.roleFlags==role)
        }
        if((audioLanguage!=value.audio||configuredSettings.sourceAudio!=value.sourceAudio||configuredSettings.audioRole!=value.audioRole)&&!matches(C.TRACK_TYPE_AUDIO,value.audio,value.audioRole))manual.remove(C.TRACK_TYPE_AUDIO)
        if((textLanguage!=value.text||configuredSettings.subtitles!=value.subtitles||configuredSettings.textRole!=value.textRole)&&!matches(C.TRACK_TYPE_TEXT,value.text,value.textRole))manual.remove(C.TRACK_TYPE_TEXT)
        if(value.subtitles==SubtitlePolicy.OFF)manual.remove(C.TRACK_TYPE_TEXT)
        if(qualityHeight!=value.preferredHeight)manual.remove(C.TRACK_TYPE_VIDEO)
        configuredSettings=value;audioLanguage=value.audio;textLanguage=value.text;qualityHeight=value.preferredHeight;textEnabled=value.subtitles!=SubtitlePolicy.OFF
        player?.currentTracks?.let(::applySafeTracks)
    }
    fun setPlaying(value:Boolean){
        mainThread();wantedToPlay=value;player?.playWhenReady=value
    }
    private fun trackFormat(group:Tracks.Group,index:Int):Format {
        val format=group.getTrackFormat(index)
        if(group.type!=C.TRACK_TYPE_AUDIO)return format
        val audio=sources.getOrNull(recovery.index)?.optJSONArray("audioStreams")?:return format
        val at=player?.currentTracks?.groups.orEmpty().filter{it.type==C.TRACK_TYPE_AUDIO}.indexOfFirst{it.mediaTrackGroup==group.mediaTrackGroup}
        val metadata=audio.optJSONObject(at)?:return format
        return format.buildUpon().apply{metadata.optString("language").takeIf{it.isNotBlank()}?.let(::setLanguage)
            metadata.optString("label").takeIf{it.isNotBlank()}?.let(::setLabel)}.build()
    }
    fun trackChoices(type:Int):List<TrackChoice>{
        mainThread()
        if(type==C.TRACK_TYPE_VIDEO){
            val source=sources.getOrNull(recovery.index)
            val variants=source?.let(PlaybackVariants::all).orEmpty().filter{device.allows(PlaybackVariants.format(it),true)}.groupBy{it.optInt("height")}.values.map{group->
                group.firstOrNull{it.optString("id")==source?.optString("variantId")}
                    ?:group.firstOrNull{it.optJSONArray("audioStreams")?.length()==0}?:group.first()}
            if(variants.isNotEmpty()){
                val formats=variants.map(PlaybackVariants::format)
                val group=TrackGroup("variants-${source!!.optString("id")}",*formats.toTypedArray())
                return variants.mapIndexedNotNull{i,v->if(device.allows(formats[i],true))TrackChoice(attempt,group,i,formats[i],v.optString("id")==source.optString("variantId"),v.optString("id"))else null}
            }
        }
        return player?.currentTracks?.groups.orEmpty().filter{it.type==type}.flatMap{g->
            (0 until g.length).filter{device.allows(g.getTrackFormat(it),g.isTrackSupported(it))}.map{i->
                TrackChoice(attempt,g.mediaTrackGroup,i,trackFormat(g,i),g.isTrackSelected(i))}
        }
    }
    fun chooseTrack(type:Int,choice:TrackChoice?):Boolean {
        mainThread()
        if(choice!=null&&(choice.revision!=attempt||trackChoices(type).none{it.group==choice.group&&it.index==choice.index}))return false
        if(type==C.TRACK_TYPE_VIDEO&&sources.getOrNull(recovery.index)?.let{PlaybackVariants.all(it).isNotEmpty()}==true){
            val source=JSONObject(sources[recovery.index].toString())
            qualityHeight=choice?.format?.height?.coerceAtLeast(0)?:0
            if(choice==null)source.remove("variantId")else source.put("variantId",choice.variantId)
            player?.let{if(attemptPrepared){position=it.currentPosition;wantedToPlay=it.playWhenReady}}
            triedVariants.clear();sources=sources.toMutableList().apply{set(recovery.index,source)}
            loadCurrent(R.string.player_switching);return true
        }
        if(type==C.TRACK_TYPE_TEXT)textEnabled=choice!=null
        if(type==C.TRACK_TYPE_VIDEO)qualityHeight=choice?.format?.height?.coerceAtLeast(0)?:0
        if(type==C.TRACK_TYPE_AUDIO&&choice!=null)audioLanguage=choice.format.language.orEmpty().ifBlank{audioLanguage}
        if(type==C.TRACK_TYPE_AUDIO&&choice==null)audioLanguage=configuredSettings.audio
        if(type==C.TRACK_TYPE_TEXT&&choice!=null)textLanguage=choice.format.language.orEmpty().ifBlank{textLanguage}
        if(choice==null)manual.remove(type)else manual[type]=choice.group to choice.index
        player?.currentTracks?.let(::applySafeTracks);event("track-selection")
        return true
    }
    private fun automaticVideo()=qualityHeight==0&&manual[C.TRACK_TYPE_VIDEO]==null
    private fun autoAllowed(f:Format)=PlaybackPreferencePolicy.autoVideoAllowed(f.height,f.frameRate,configuredSettings.copy(preferredHeight=if(automaticVideo())0 else qualityHeight.coerceAtLeast(1)))
    private fun applySafeTracks(tracks:Tracks):Boolean {
        val s=selector?:return false
        if(qualityHeight>0&&manual[C.TRACK_TYPE_VIDEO]==null&&tracks.groups.any{it.type==C.TRACK_TYPE_VIDEO}&&tracks.groups.filter{it.type==C.TRACK_TYPE_VIDEO}.none{g->(0 until g.length).any{g.getTrackFormat(it).height==qualityHeight&&device.allows(g.getTrackFormat(it),g.isTrackSupported(it))}})qualityHeight=0
        val b=s.buildUponParameters().setPreferredAudioLanguages(*listOf(audioLanguage,configuredSettings.secondAudio).filter{it.isNotBlank()}.toTypedArray())
            .setPreferredTextLanguages(textLanguage).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!textEnabled)
            .clearVideoSizeConstraints().setMaxVideoFrameRate(if(automaticVideo()&&configuredSettings.autoMaxFps>0)configuredSettings.autoMaxFps else Int.MAX_VALUE)
        if(automaticVideo()&&configuredSettings.autoMaxHeight>0)b.setMaxVideoSize(Int.MAX_VALUE,configuredSettings.autoMaxHeight)
        var chosenAudio:String?=null
        for(type in listOf(C.TRACK_TYPE_VIDEO,C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_TEXT)){
            b.clearOverridesOfType(type)
            val groups=tracks.groups.filter{it.type==type}
            var eligible=groups.associateWith{g->(0 until g.length).filter{device.allows(g.getTrackFormat(it),g.isTrackSupported(it))&&
                (type!=C.TRACK_TYPE_VIDEO||!automaticVideo()||autoAllowed(g.getTrackFormat(it)))}}
            if(type==C.TRACK_TYPE_TEXT)eligible=eligible.mapValues{(g,indices)->indices.filter{i->
                manual[type]!=null||PlaybackPreferencePolicy.subtitleAllowed(configuredSettings.subtitles,chosenAudio,g.getTrackFormat(i).selectionFlags and C.SELECTION_FLAG_FORCED!=0)}}
            val wanted=manual[type]?.takeIf{(group,index)->eligible.entries.any{it.key.mediaTrackGroup==group&&index in it.value}}
            val lang=if(type==C.TRACK_TYPE_AUDIO)audioLanguage else textLanguage
            fun language(value:String?)=PlaybackPreferencePolicy.matches(value,lang)
            val role=if(type==C.TRACK_TYPE_AUDIO)configuredSettings.audioRole else configuredSettings.textRole
            val group=if(wanted!=null)groups.first{it.mediaTrackGroup==wanted.first}
                else if(type==C.TRACK_TYPE_VIDEO)groups.filter{eligible.getValue(it).isNotEmpty()}.maxByOrNull{g->
                    val heights=eligible.getValue(g).map{g.getTrackFormat(it).height}
                    if(qualityHeight>0)heights.filter{it in 1..qualityHeight}.maxOrNull()?:-(heights.filter{it>0}.minOrNull()?:Int.MAX_VALUE) else heights.maxOrNull()?:-1}
                else if(type==C.TRACK_TYPE_TEXT)groups.firstOrNull{g->eligible.getValue(g).any{language(trackFormat(g,it).language)}}
                else (if(type==C.TRACK_TYPE_AUDIO&&configuredSettings.sourceAudio)null else groups.firstOrNull{g->eligible.getValue(g).any{language(trackFormat(g,it).language)}})
                    ?:(if(type==C.TRACK_TYPE_AUDIO&&!configuredSettings.sourceAudio)groups.firstOrNull{g->eligible.getValue(g).any{PlaybackPreferencePolicy.matches(trackFormat(g,it).language,configuredSettings.secondAudio)}}else null)
                    ?:groups.firstOrNull{it.isSelected&&eligible.getValue(it).isNotEmpty()}?:groups.firstOrNull{eligible.getValue(it).isNotEmpty()}
            for(g in groups){
                var indices=if(g==group)eligible.getValue(g)else emptyList()
                if(wanted!=null&&g.mediaTrackGroup==wanted.first)indices=listOf(wanted.second)
                else if(type==C.TRACK_TYPE_VIDEO&&qualityHeight>0&&indices.isNotEmpty()){
                    val best=indices.map{g.getTrackFormat(it).height}.filter{it in 1..qualityHeight}.maxOrNull()?:indices.map{g.getTrackFormat(it).height}.filter{it>0}.minOrNull()
                    if(best!=null)indices=indices.filter{g.getTrackFormat(it).height==best}
                }else if(type!=C.TRACK_TYPE_VIDEO&&!(type==C.TRACK_TYPE_AUDIO&&configuredSettings.sourceAudio)&&indices.any{language(trackFormat(g,it).language)})indices=indices.filter{language(trackFormat(g,it).language)}
                if(role!=null&&indices.any{g.getTrackFormat(it).roleFlags==role})indices=indices.filter{g.getTrackFormat(it).roleFlags==role}
                if(type==C.TRACK_TYPE_AUDIO&&indices.isNotEmpty())chosenAudio=trackFormat(g,indices.first()).language
                b.addOverride(TrackSelectionOverride(g.mediaTrackGroup,indices))
            }
        }
        val p=b.build();if(p==s.parameters)return false
        s.parameters=p;return true
    }
    fun sourceChoices():List<Pair<String,Boolean>> {mainThread();return originals.mapIndexed{i,s->s.optString("label") to (i==recovery.index)}}
    data class SourceEntry(val id:String,val label:String,val selected:Boolean,val state:String="untried",val reason:String="")
    private val sourceStates=mutableMapOf<String,Pair<String,String>>()
    private fun sourceState(state:String,reason:String=""){originals.getOrNull(recovery.index)?.optString("id")?.let{sourceStates[it]=state to reason}}
    fun sourceEntries():List<SourceEntry>{mainThread();return originals.mapIndexed{i,s->
        val id=s.optString("id");val status=sourceStates[id]
        SourceEntry(id,s.optString("label"),i==recovery.index,if(i==recovery.index&&firstFrame)if(player?.playWhenReady==true)"playing"else "paused"else status?.first?:"untried",status?.second.orEmpty())}}
    fun chooseSource(id:String)=chooseSource(originals.indexOfFirst{it.optString("id")==id})
    fun appendCandidates(candidates:List<JSONObject>,pending:Boolean){
        mainThread();discoveryPending=pending
        val added=candidates.filter{c->originals.none{it.optString("id")==c.optString("id")}}.distinctBy{it.optString("id")}.take((1024-originals.size).coerceAtLeast(0))
        val first=sources.size
        if(added.isNotEmpty()){originals=originals+added.map{JSONObject(it.toString())};sources=sources+added.map{JSONObject(it.toString())};recovery.extend(sources.size)}
        if(waitingForSources){
            if(added.isNotEmpty()){waitingForSources=false;recovering=false;terminal=false;switches++;recovery.select(first);createEngine();loadCurrent(R.string.player_switching)}
            else if(!pending){waitingForSources=false;recovering=false;terminal=true;listener.onSourcesExhausted(lastFailure?:"Kaynak bulunamadı")}
        }
    }
    fun chooseSource(index:Int):Boolean {
        mainThread();if(index !in sources.indices)return false
        player?.let{if(attemptPrepared){position=it.currentPosition;wantedToPlay=it.playWhenReady}}
        recovery.select(index);triedVariants.clear();terminal=false;terminalError=null;recovering=false;sourceChanged=true
        // A previously resolved signed URL must be refreshed when the user returns to it.
        if(originals[index].has("lazyOptionId"))sources=sources.toMutableList().apply{set(index,JSONObject(originals[index].toString()))}
        if(player==null)createEngine()
        loadCurrent(R.string.player_switching);return true
    }
    fun start(candidates: List<JSONObject>, http: OkHttpClient, fixture: String? = null, lookup:SourceLookup?=null, resumePositionMs:Long=0) {
        mainThread(); val oldBuffer=sessionBuffer;stopPlayback();if(oldBuffer!=configuredSettings.buffer)destroyEngine();sessionBuffer=configuredSettings.buffer
        require(candidates.isNotEmpty()) { "No playback source" }
        // A bad page must not create an unbounded retry list. Keep all actual alternatives, up to 16.
        sources = candidates.take(1024).map { JSONObject(it.toString()) }
        sourceStates.clear();triedVariants.clear();originals=sources.map{JSONObject(it.toString())};refreshed.clear();refreshAt.clear();refreshes=0;endedNotified=false;sourceChanged=false
        sourceLookup=lookup;discoveryPending=false;waitingForSources=false
        // Cookies and measured header policy are shared, execution/pool lifetime is owned here.
        // Closing a parser/JS runtime must not shut down active segment downloads.
        client = http.newBuilder().dispatcher(okhttp3.Dispatcher())
            .connectionPool(okhttp3.ConnectionPool(3, 1, TimeUnit.MINUTES)).build()
        fixtureBase = fixture; recovery = PlaybackRecovery(sources.size)
        position = resumePositionMs.coerceAtLeast(0); wantedToPlay = true; suspended = false; terminal = false
        terminalError = null; lastFailure = null; switches = 0; restarts = 0; enginesCreated = 0; engineInstancesUsed = 0
        rebufferCount = 0; rebufferMs = 0; droppedFrames = 0; audioUnderruns = 0; transferBytes = 0; transferMs = 0
        events.clear()
        if (player == null) createEngine() else {
            engineInstancesUsed++; event("reuse-foreground-engine")
            listener.onEngineChanged(player); handler.postDelayed(ticker, 500)
        }
        loadCurrent()
    }
    private fun createEngine() {
        val selector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                // Some real masters say AVC High while their SPS is Main/Baseline. Permit
                // Media3's lowest-quality capability probe; validate the actual input below.
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(false)
                // Resolution, frame rate and bitrate have no application ceiling.
                .clearVideoSizeConstraints().clearViewportSizeConstraints()
                .setMaxVideoFrameRate(Int.MAX_VALUE).setMaxVideoBitrate(Int.MAX_VALUE)
                .setPreferredAudioLanguages(audioLanguage, "tr", "tur")
                .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG)
                .setPreferredTextLanguages(textLanguage, "tr", "tur").setSelectUndeterminedTextLanguage(false)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!textEnabled)
                .setTunnelingEnabled(false).build()
        }
        this.selector=selector
        val renderer = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val buffer=sessionBuffer
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(buffer.minMs,buffer.maxMs,buffer.startMs,buffer.restartMs)
            .setTargetBufferBytes(buffer.bytes).setBackBuffer(0, false)
            .setPrioritizeTimeOverSizeThresholds(false).build()
        val bandwidth = DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(1_000_000).build()
        player = ExoPlayer.Builder(context, renderer).setTrackSelector(selector).setLoadControl(load)
            .setBandwidthMeter(bandwidth).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true).build().also { p ->
                p.setForegroundMode(true)
                p.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
                        if(active&&p===player)wantedToPlay=playWhenReady
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        if (!active || p !== player || tracks != p.currentTracks) return
                        val constrained=applySafeTracks(tracks)
                        lastTracks = JSONArray()
                        for (g in tracks.groups) for (i in 0 until g.length) {
                            val f = g.getTrackFormat(i)
                            if (lastTracks.length() < 32) lastTracks.put(objectOf("type" to g.type, "mime" to f.sampleMimeType, "codecs" to f.codecs,
                                "language" to f.language, "selected" to g.isTrackSelected(i), "supported" to g.isTrackSupported(i),
                                "width" to f.width, "height" to f.height, "frameRate" to f.frameRate, "bitrate" to f.bitrate,"roleFlags" to f.roleFlags))
                        }
                        if (tracks.groups.isEmpty() || p.playbackState == Player.STATE_IDLE) return
                        listener.onTracksAvailable()
                        if(tracks.groups.any{it.type==C.TRACK_TYPE_AUDIO}&&trackChoices(C.TRACK_TYPE_AUDIO).isEmpty()){
                            fail(PlaybackRecovery.Failure.AUDIO,"No device-supported audio track",R.string.player_audio_unsupported);return
                        }
                        if(tracks.groups.any{it.type==C.TRACK_TYPE_VIDEO}&&trackChoices(C.TRACK_TYPE_VIDEO).isEmpty()){
                            fail(PlaybackRecovery.Failure.VIDEO,"No device-supported video track",R.string.player_video_unsupported);return
                        }
                        if(constrained)return
                        if (tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO && it.isSelected })
                            fail(PlaybackRecovery.Failure.VIDEO, "No supported video track", R.string.player_video_unsupported)
                        else if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO } && tracks.groups.none { it.type == C.TRACK_TYPE_AUDIO && it.isSelected })
                            fail(PlaybackRecovery.Failure.AUDIO, "No supported audio track", R.string.player_audio_unsupported)
                    }
                    override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) guard.reset(SystemClock.elapsedRealtime(), p.currentPosition, p.videoDecoderCounters?.renderedOutputBufferCount ?: 0)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if(!active||p!==player||p.playbackState!=state)return
                        val now = SystemClock.elapsedRealtime()
                        if (state == Player.STATE_BUFFERING && firstFrame && rebufferStarted == 0L) { rebufferCount++; rebufferStarted = now; event("rebuffer") }
                        if (state != Player.STATE_BUFFERING) finishRebuffer()
                        if (state == Player.STATE_ENDED&&!endedNotified) {endedNotified=true;event("ended");handler.post{if(active&&p===player&&p.playbackState==Player.STATE_ENDED)listener.onEnded()}}
                    }
                })
                p.addAnalyticsListener(object : AnalyticsListener {
                    private fun belongs(time:AnalyticsListener.EventTime):Boolean {
                        if(!active||p!==player||!attemptPrepared)return false
                        val id=if(time.timeline.isEmpty)p.currentMediaItem?.mediaId else time.timeline.getWindow(time.windowIndex,Timeline.Window()).mediaItem.mediaId
                        return id=="attempt-$attempt"
                    }
                    override fun onRenderedFirstFrame(time:AnalyticsListener.EventTime,output:Any,renderTimeMs:Long){
                        if(!belongs(time))return
                        if(!firstFrame)firstFrameMs=SystemClock.elapsedRealtime()-attemptStartedMs
                        firstFrame=true;attemptHadProgress=true;guard.reset(SystemClock.elapsedRealtime(),p.currentPosition)
                        listener.onMessage(R.string.player_source,label());refreshed.remove(recovery.index)
                    }
                    override fun onPlayerError(time:AnalyticsListener.EventTime,error:PlaybackException) {
                        // One ExoPlayer is reused. A previous decoder can report its error
                        // after the next source was queued; EventTime identifies its media item.
                        if(!belongs(time)||p.playerError!==error){event("obsolete-player-error",error.errorCodeName);return}
                        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
                        httpCode = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
                        val reason = when {
                            httpCode != null -> PlaybackRecovery.Failure.HTTP
                            error.errorCode in 4000..4999 -> PlaybackRecovery.Failure.DECODER
                            error.errorCode in 3000..3999 -> PlaybackRecovery.Failure.FORMAT
                            else -> PlaybackRecovery.Failure.NETWORK
                        }
                        fail(reason, (error.errorCodeName + ": " + causes.joinToString(" | ") { it.javaClass.simpleName + ": " + it.message }).take(1600), R.string.player_source_failed)
                    }
                    override fun onVideoInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, reuse: DecoderReuseEvaluation?) {
                        if (!active || p !== player || time.timeline.isEmpty) return
                        val window = Timeline.Window()
                        time.timeline.getWindow(time.windowIndex, window)
                        if (window.mediaItem.mediaId != "attempt-$attempt") return
                        if(automaticVideo()&&!autoAllowed(format)){
                            fail(PlaybackRecovery.Failure.VIDEO,"Actual format exceeds automatic quality limit",R.string.player_quality_limit);return
                        }
                        videoCodec = format.codecs
                        // Use the actual input format when available: manifests can mislabel Hi10 as High.
                        val mime = format.sampleMimeType ?: return
                        val deviceAllowed=device.allows(format,true,true)
                        if(!deviceAllowed){
                            fail(PlaybackRecovery.Failure.VIDEO,"Device video/HDR capability rejected",R.string.player_video_unsupported);return
                        }
                        val supported = runCatching {
                            val decoders = MediaCodecUtil.getDecoderInfos(mime, false, false)
                            if (decoders.any { it.isFormatSupported(context, format) }) true
                            else {
                                val bounded = if (mime == MimeTypes.VIDEO_H264) AvcLevelProbe.boundedCodec(format.codecs, format.width, format.height, format.frameRate) else null
                                val probe = bounded != null && decoders.any { it.isFormatSupported(context, format.buildUpon().setCodecs(bounded).build()) }
                                if (probe) { videoCapabilityProbe = "avc-level"; event("avc-level-probe", format.codecs + " -> " + bounded) }
                                probe
                            }
                        }.getOrNull()
                        if(supported==false&&mime==MimeTypes.VIDEO_H264&&deviceAllowed){
                            // Keep the real SPS/profile. Some legacy platform declarations omit
                            // 8-bit High/Main even though their native decoder can render them.
                            // Decoder errors and the first-frame watchdog still bound this probe.
                            videoCapabilityProbe="avc-8bit-runtime";event("avc-8bit-runtime",format.codecs.orEmpty())
                        }else if (supported == false) fail(PlaybackRecovery.Failure.VIDEO, "Unsupported actual video format: $mime/${format.codecs}", R.string.player_video_unsupported)
                    }
                    override fun onVideoDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestamp: Long, duration: Long) { if(belongs(time))videoDecoder = name }
                    override fun onBandwidthEstimate(time: AnalyticsListener.EventTime, elapsedMs: Int, bytes: Long, bitrate: Long) { transferBytes += bytes; transferMs += elapsedMs }
                    override fun onDroppedVideoFrames(time: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) { droppedFrames += count }
                    override fun onAudioUnderrun(time: AnalyticsListener.EventTime, size: Int, sizeMs: Long, elapsedMs: Long) { audioUnderruns++ }
                })
            }
        enginesCreated++; engineInstancesUsed++; listener.onEngineChanged(player)
        handler.removeCallbacks(ticker); handler.postDelayed(ticker, 500)
    }
    private fun label() = sources.getOrNull(recovery.index)?.optString("label").orEmpty()
    private fun loadCurrent(message: Int = R.string.player_preparing) {
        val p = player ?: return
        val available=PlaybackVariants.all(sources[recovery.index]).filter{device.allows(PlaybackVariants.format(it),true)}
        if(qualityHeight>0&&available.isNotEmpty()&&available.none{it.optInt("height")==qualityHeight})qualityHeight=0
        val source = PlaybackVariants.prepare(sources[recovery.index],qualityHeight){device.allows(PlaybackVariants.format(it),true)&&(!automaticVideo()||autoAllowed(PlaybackVariants.format(it)))}
        sources=sources.toMutableList().apply{set(recovery.index,source)}
        sourceState(if(source.optString("url").isBlank())"resolving"else "ready")
        manual.clear();endedNotified=false
        attempt++; val token = attempt
        pendingLookup?.close();pendingLookup=null;resolving=false
        recovering = false; firstFrame = false; firstFrameMs = null; attemptPrepared=false; attemptStartedMs = SystemClock.elapsedRealtime()
        attemptHadProgress = false; headerVariant = null; httpCode = null; videoCodec = null; videoDecoder = null; videoCapabilityProbe = null
        lastTracks = JSONArray(); guard.reset(SystemClock.elapsedRealtime(), position)
        if(!candidateAllowed(originals[recovery.index])){fail(PlaybackRecovery.Failure.FORMAT,"Source disabled",R.string.player_source_disabled);return}
        if(PlaybackVariants.all(source).isNotEmpty()&&automaticVideo()&&PlaybackVariants.all(source).none{device.allows(PlaybackVariants.format(it),true)&&autoAllowed(PlaybackVariants.format(it))}){
            fail(PlaybackRecovery.Failure.VIDEO,"Automatic quality limit",R.string.player_quality_limit);return
        }
        if(source.optString("url").isBlank() && source.has("lazyOptionId")) {
            val lookup=sourceLookup
            if(lookup==null){fail(PlaybackRecovery.Failure.FORMAT,"No source resolver",R.string.player_source_failed);return}
            resolving=true;p.stop();p.clearMediaItems()
            listener.onMessage(R.string.player_preparing,label());event("resolve-selected",source.optString("lazyOptionId"))
            pendingLookup=lookup.resolve(JSONObject(source.toString())){resolved,error->
                handler.post {
                    if(attempt!=token || suspended || terminal || !active)return@post
                    pendingLookup=null;resolving=false
                    if(error!=null || resolved==null){fail(PlaybackRecovery.Failure.FORMAT,"Resolve: "+(error?.message?:"No source"),R.string.player_source_failed)}
                    else {
                        sources=sources.toMutableList().apply{set(recovery.index,JSONObject(resolved.toString()))}
                        loadCurrent(message)
                    }
                }
            }
            return
        }
        val originalUrl = source.optString("url").toHttpUrlOrNull()
        if(PlaybackVariants.all(source).isNotEmpty()&&PlaybackVariants.all(source).none{device.allows(PlaybackVariants.format(it),true)}){
            fail(PlaybackRecovery.Failure.VIDEO,"No device-supported video variant",R.string.player_video_unsupported);return
        }
        val url = fixtureBase?.let { "$it/media/master.m3u8" } ?: originalUrl?.toString()
        if (url == null || originalUrl?.scheme !in listOf("http", "https")) { fail(PlaybackRecovery.Failure.FORMAT, "Invalid media URL", R.string.player_source_failed); return }
        val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            put("Accept", "*/*")
            if (!source.isNull("referer") && source.optString("referer").isNotBlank()) put("Referer", source.getString("referer"))
            put("User-Agent", source.optString("userAgent").takeIf { it.isNotBlank() && it != "null" } ?: SiteHttpPolicy.defaultUserAgent(checkNotNull(originalUrl)))
            if (!source.isNull("cookie") && source.optString("cookie").isNotBlank()) put("Cookie", source.getString("cookie"))
            source.optJSONObject("headers")?.let { extra -> extra.keys().forEach { key -> put(key, extra.getString(key)) } }
        }
        val http = PlaybackHttpPolicy.client(source, checkNotNull(client)) { value -> handler.post { if (token == attempt) headerVariant = value } }
            .newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()
        val ds = OkHttpDataSource.Factory(http).setDefaultRequestProperties(headers)
        val factory = DefaultMediaSourceFactory(ds).setLoadErrorHandlingPolicy(PlaybackLoadPolicy())
        val mime = when {fixtureBase!=null||source.optString("type")=="hls"->MimeTypes.APPLICATION_M3U8
            source.optString("type")=="mp4"->MimeTypes.VIDEO_MP4
            source.optString("type")=="webm"->MimeTypes.VIDEO_WEBM
            source.optString("type")=="dash"->MimeTypes.APPLICATION_MPD
            else->null}
        val subtitles=source.optJSONArray("subtitles")?.let{list->(0 until list.length()).mapNotNull{i->
            val sub=list.optJSONObject(i)?:return@mapNotNull null
            val uri=sub.optString("url").toHttpUrlOrNull()?:return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(uri.toString()))
                .setMimeType(if(sub.optString("format")=="srt")MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage(sub.optString("language").ifBlank{sub.optString("lang")}).setLabel(sub.optString("label")).build()
        }}?:emptyList()
        val item = MediaItem.Builder().setMediaId("attempt-$token").setUri(url).setMimeType(mime).setSubtitleConfigurations(subtitles).build()
        event("prepare", source.optString("id")); listener.onMessage(message, label())
        attemptPrepared=true
        // Flush the previous decoder before preparing a different attempt, without
        // replacing the player or losing the resume point captured above.
        p.stop()
        p.setMediaSource(PlaybackMediaSources.create(source, factory, item), position); p.prepare(); p.playWhenReady = wantedToPlay
    }
    private fun finishRebuffer() {
        if (rebufferStarted != 0L) { rebufferMs += SystemClock.elapsedRealtime() - rebufferStarted; rebufferStarted = 0;event("rebuffer-end") }
    }
    private fun fail(reason: PlaybackRecovery.Failure, message: String, userMessage: Int) {
        sourceState("failed",message.take(240))
        if (recovering || suspended || terminal || !active) return
        recovering = true; val token = attempt
        lastFailure = message; event("failure", reason.name + ": " + message)
        val p = player
        // Zero while a new media source is preparing must not erase a valid resume point.
        if (firstFrame || (p?.currentPosition ?: 0) > 0) position = p?.currentPosition ?: position
        if(attemptPrepared)wantedToPlay = p?.playWhenReady ?: wantedToPlay
        handler.post {
            if (attempt != token || suspended || terminal || !active) return@post
            finishRebuffer()
            val now=SystemClock.elapsedRealtime()
            if(reason==PlaybackRecovery.Failure.HTTP&&httpCode in listOf(401,403,404,410)&&sourceLookup!=null&&
                originals.getOrNull(recovery.index)?.has("lazyOptionId")==true&&refreshes<16&&
                recovery.index !in refreshed&&now-(refreshAt[recovery.index]?:-60000)>=30000){
                refreshed.add(recovery.index);refreshAt[recovery.index]=now;refreshes++
                sources=sources.toMutableList().apply{set(recovery.index,JSONObject(originals[recovery.index].toString()))}
                event("refresh-expired-source");loadCurrent(R.string.player_refreshing);return@post
            }
            val currentSource=sources[recovery.index]
            if(automaticSources&&PlaybackVariants.all(currentSource).isNotEmpty()){
                val sourceId=currentSource.optString("id")
                triedVariants.add(sourceId+"|"+currentSource.optString("variantId"))
                val fallback=PlaybackVariants.fallback(currentSource,triedVariants.filter{it.startsWith(sourceId+"|")}.map{it.substringAfterLast('|')}.toSet()){device.allows(PlaybackVariants.format(it),true)&&(!automaticVideo()||autoAllowed(PlaybackVariants.format(it)))}
                if(fallback!=null){
                    sources=sources.toMutableList().apply{set(recovery.index,fallback)};restarts++
                    event("combined-source-fallback");loadCurrent(R.string.player_retrying);return@post
                }
            }
            val currentSite=originals[recovery.index].optString("id").substringBefore('|')
            val previousIndex=recovery.index
            var action=recovery.failed(reason,attemptHadProgress,automaticSources)
            while(!crossSiteSources&&action==PlaybackRecovery.Action.NEXT&&originals[recovery.index].optString("id").substringBefore('|')!=currentSite)action=recovery.failed(reason,false,automaticSources)
            if(action==PlaybackRecovery.Action.STOP&&originals[recovery.index].optString("id").substringBefore('|')!=currentSite)recovery.select(previousIndex)
            when (action) {
                PlaybackRecovery.Action.RETRY -> { restarts++; loadCurrent(R.string.player_retrying) }
                PlaybackRecovery.Action.NEXT -> { switches++; loadCurrent(R.string.player_switching) }
                PlaybackRecovery.Action.STOP -> {
                    if(automaticSources&&crossSiteSources&&discoveryPending){waitingForSources=true;recovering=true;destroyEngine();return@post}
                    terminal = true; recovering = false; terminalError = message; destroyEngine()
                    listener.onMessage(userMessage, label())
                    listener.onSourcesExhausted(message)
                }
            }
        }
    }
    fun suspendPlayback() {
        mainThread()
        if (!active) { destroyEngine(); return }
        if (suspended || terminal) return
        if(attemptPrepared)player?.let { position = it.currentPosition; wantedToPlay = it.playWhenReady }
        suspended = true; attempt++; pendingLookup?.close();pendingLookup=null;resolving=false; recovering = false; event("suspend"); finishRebuffer(); destroyEngine()
    }
    fun resumePlayback() {
        mainThread(); if (!active || !suspended || terminal) return
        suspended = false; event("resume"); createEngine(); loadCurrent()
    }
    private fun destroyEngine() {
        handler.removeCallbacks(ticker)
        val old = player; player = null; listener.onEngineChanged(null); old?.release()
    }
    fun trimIdle() { mainThread(); if (!active) destroyEngine() }
    fun stopPlayback() {
        mainThread(); attempt++; pendingLookup?.close();pendingLookup=null;resolving=false;sourceLookup=null
        handler.removeCallbacksAndMessages(null); finishRebuffer()
        player?.stop(); player?.clearMediaItems()
        sources = emptyList()
        firstFrame=false;firstFrameMs=null;attemptPrepared=false;attemptHadProgress=false;waitingForSources=false;discoveryPending=false;terminalError=null
        originals=emptyList();manual.clear()
        val retired = client; client = null
        if (retired != null) transportCleanup.execute {
            try { retired.dispatcher.cancelAll(); retired.connectionPool.evictAll() }
            catch (error: Exception) { if (diagnosticsEnabled) android.util.Log.w("FilmPlayback", "Transport cleanup: " + error.javaClass.simpleName) }
            finally { retired.dispatcher.executorService.shutdown() }
        }
        suspended = false; terminal = false; recovering = false
    }
    fun snapshot(): JSONObject {
        mainThread(); val p = player
        return objectOf("firstFrame" to firstFrame, "positionMs" to (p?.currentPosition ?: position), "isPlaying" to (p?.isPlaying ?: false),
            "width" to (p?.videoSize?.width ?: 0), "height" to (p?.videoSize?.height ?: 0),
            "state" to p?.playbackState, "playWhenReady" to (p?.playWhenReady ?: wantedToPlay), "bufferedMs" to (p?.totalBufferedDuration ?: 0), "firstFrameMs" to firstFrameMs,
            "renderedBuffers" to (p?.videoDecoderCounters?.renderedOutputBufferCount ?: 0), "audioRenderedBuffers" to (p?.audioDecoderCounters?.renderedOutputBufferCount ?: 0),
            "audioMime" to p?.audioFormat?.sampleMimeType, "videoCodec" to videoCodec, "videoDecoder" to videoDecoder, "videoCapabilityProbe" to videoCapabilityProbe,
            "rebufferCount" to rebufferCount, "rebufferMs" to (rebufferMs + if (rebufferStarted != 0L) SystemClock.elapsedRealtime() - rebufferStarted else 0),
            "httpStatus" to httpCode, "headerVariant" to headerVariant, "error" to terminalError, "lastFailure" to lastFailure,
            "sourceIndex" to recovery.index, "sourceId" to sources.getOrNull(recovery.index)?.optString("id"),"sourceCount" to originals.size,
            "sourceSwitches" to switches, "sourceRestarts" to restarts, "enginesCreated" to enginesCreated,
            "sourceRefreshes" to refreshes,"bufferProfile" to sessionBuffer.name,"automaticQuality" to automaticQuality,"subtitlePolicy" to configuredSettings.subtitles.name,
            "diagnosticsEnabled" to diagnosticsEnabled,
            "engineInstancesUsed" to engineInstancesUsed, "engineIdentity" to p?.let(System::identityHashCode), "suspended" to suspended, "resolving" to resolving,
            "droppedFrames" to droppedFrames, "audioUnderruns" to audioUnderruns, "downloadBytes" to transferBytes,
            "averageDownloadBps" to if (transferMs > 0) transferBytes * 8000 / transferMs else 0,
            "tracks" to JSONArray(lastTracks.toString()), "events" to JSONArray(events.toList()))
            .put("videoLimits",selector?.parameters?.let{v->objectOf("width" to v.maxVideoWidth,"height" to v.maxVideoHeight,"fps" to v.maxVideoFrameRate,
                "bitrate" to v.maxVideoBitrate,"viewportWidth" to v.viewportWidth,"viewportHeight" to v.viewportHeight)}?:JSONObject.NULL)
    }
    override fun close() {
        stopPlayback(); destroyEngine()
    }
}
