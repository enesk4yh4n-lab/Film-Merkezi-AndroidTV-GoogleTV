package app.filmmerkezi.tv

object PlaybackPreferencePolicy {
    fun normalizedLanguage(value:String?)=when(val v=value.orEmpty().lowercase().substringBefore('-').substringBefore('_')){"tur"->"tr";"eng"->"en";"deu","ger"->"de";"fra","fre"->"fr";else->v}
    fun matches(a:String?,b:String?)=normalizedLanguage(a).isNotBlank()&&normalizedLanguage(a)==normalizedLanguage(b)
    fun subtitleAllowed(policy:SubtitlePolicy,audio:String?,forced:Boolean)=when(policy){
        SubtitlePolicy.OFF->false;SubtitlePolicy.ON->true;SubtitlePolicy.AUTO->!matches(audio,"tr")||forced
    }
    fun autoVideoAllowed(height:Int,fps:Float,settings:PlaybackSettings):Boolean = settings.preferredHeight>0||
        ((settings.autoMaxHeight==0||height<=0||height<=settings.autoMaxHeight)&&(settings.autoMaxFps==0||fps<=0||fps<=settings.autoMaxFps+0.01f))
}
