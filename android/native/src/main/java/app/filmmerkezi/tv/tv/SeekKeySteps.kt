package app.filmmerkezi.tv.tv

/** One tap is precise; held keys accelerate without following the remote's repeat frequency. */
internal class SeekKeySteps(private val seconds:()->Int={10},private val accelerate:()->Boolean={true}) {
    private var direction=0
    private var started=0L
    private var last=0L
    fun reset(){direction=0;started=0;last=0}
    fun down(keyDirection:Int,time:Long,repeat:Int):Long? {
        val step=seconds().coerceIn(5,30)*1000L
        if(repeat==0||keyDirection!=direction||time<last){
            direction=keyDirection;started=time;last=time;return step
        }
        if(time-last<300)return null
        last=time
        return when {
            !accelerate()->step
            time-started>=5000->step*(6+((time-started-5000)/2000)*3)
            time-started>=3000->step*3
            time-started>=1500->step*2
            else->step
        }
    }
}
