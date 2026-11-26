package app.filmmerkezi.tv.core
object EpisodeNumbers {
        fun episodeNumber(label:String,url:String,defaultSeason:Int=1):EpisodeNumber? {
            val m=Regex("(\\d+)\\s*\\.?\\s*Sezon\\s*(\\d+)\\s*\\.?\\s*Bölüm",RegexOption.IGNORE_CASE).find(label)
                ?:Regex("(?:-|/)sezon-(\\d+)/(?:bolum-)(\\d+)|-(\\d+)-sezon-(\\d+)-bolum").find(url)
            if(m!=null) {
                val nums=m.groupValues.drop(1).filter{it.isNotBlank()}.mapNotNull{it.toIntOrNull()}
                if(nums.size>=2&&nums[0]>0&&nums[1]>0)return EpisodeNumber(nums[0],nums[1])
            }
            val n=Regex("(\\d+)\\s*\\.?\\s*Bölüm",RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)?.toIntOrNull()
            return n?.takeIf{it>0}?.let{EpisodeNumber(defaultSeason,it)}
        }
}
