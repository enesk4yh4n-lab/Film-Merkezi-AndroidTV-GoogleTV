package app.filmmerkezi.tv.pluginapi;
/** Version 1 ABI. UTF-8 JSON only; no Activity, Context, player or database references. */
public interface Plugin {
    String call(String operation, String payload, PluginHost host) throws Exception;
}
