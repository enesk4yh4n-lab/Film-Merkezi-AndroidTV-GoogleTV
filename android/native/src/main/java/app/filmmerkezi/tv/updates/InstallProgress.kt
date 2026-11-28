package app.filmmerkezi.tv.updates

enum class InstallPhase { DOWNLOADING, VERIFYING, READY }
data class InstallProgress(val phase:InstallPhase,val name:String,val completed:Long,val total:Long)
