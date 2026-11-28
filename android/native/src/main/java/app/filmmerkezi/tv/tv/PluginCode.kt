package app.filmmerkezi.tv.tv

private val numberedPluginId = Regex("[pr][0-9]{3}")

internal fun pluginCode(idOrKey: String): String {
    val id = idOrKey.substringAfterLast('/')
    return if (numberedPluginId.matches(id)) id.uppercase() else id
}
