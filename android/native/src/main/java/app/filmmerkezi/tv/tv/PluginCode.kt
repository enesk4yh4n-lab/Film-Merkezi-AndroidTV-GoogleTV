package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.Sites

private val numberedPluginId = Regex("[pr][0-9]{3}")

internal fun pluginCode(idOrKey: String): String {
    val id = idOrKey.substringAfterLast('/')
    return if (numberedPluginId.matches(id)) id.uppercase() else id
}

internal fun catalogPluginCode(siteId:String)=pluginCode(Sites.catalogPackageId(siteId))
