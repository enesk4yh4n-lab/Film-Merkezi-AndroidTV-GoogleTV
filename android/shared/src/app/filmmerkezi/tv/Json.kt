package app.filmmerkezi.tv

import org.json.JSONArray
import org.json.JSONObject

fun jsonValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    is JSONObject, is JSONArray, is Number, is Boolean -> value.toString()
    else -> JSONObject.quote(value.toString())
}
fun objectOf(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
