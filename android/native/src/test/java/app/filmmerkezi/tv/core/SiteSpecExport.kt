package app.filmmerkezi.tv.core

/** Build tool entry point, not a test and never packaged into the application. */
object SiteSpecExport {
    @JvmStatic fun main(args:Array<String>){java.io.File(args.single()).writeText(org.json.JSONArray(Sites.defaults.map(PluginWire::site)).toString(2))}
}
